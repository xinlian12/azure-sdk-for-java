package com.azure.cosmos.extension

import com.azure.cosmos.models.SparkModelBridgeInternal
import com.azure.cosmos.spark.{CosmosConstants, ItemsScan, ItemsTable}
import org.apache.spark.sql.catalyst.expressions.{Alias, Attribute, AttributeReference, ExprId, Expression, NamedExpression}
import org.apache.spark.sql.catalyst.optimizer.{BuildLeft, BuildRight, BuildSide}
import org.apache.spark.sql.catalyst.planning.ExtractEquiJoinKeys
import org.apache.spark.sql.catalyst.plans.logical.{LogicalPlan, ReturnAnswer}
import org.apache.spark.sql.catalyst.plans.{Inner, JoinType, LeftOuter, QueryPlan, RightOuter}
import org.apache.spark.sql.execution.datasources.v2.{BatchScanExec, DataSourceV2Relation, DataSourceV2ScanRelation, DataSourceV2Strategy}
import org.apache.spark.sql.execution.{ProjectExec, SparkPlan}
import org.apache.spark.sql.{SparkSession, Strategy}

import scala.collection.mutable.ArrayBuffer

case class CosmosReadManyJoinStrategy(spark: SparkSession) extends Strategy with Serializable {
    /**
     * Currently we will only support Inner, LeftOuter and RightOuter joins
     * Depending on the side of the CassandraTarget different joins are allowed.
     */
    val validJoins: Map[BuildSide, Seq[JoinType]] = Map(
        BuildRight -> Seq[JoinType](Inner, LeftOuter),
        BuildLeft -> Seq[JoinType](Inner, RightOuter)
    )

    override def apply(plan: LogicalPlan): Seq[SparkPlan] = {
        if (plan.isInstanceOf[ReturnAnswer]) {
            val childLogicalPlan = plan.asInstanceOf[ReturnAnswer].child
            childLogicalPlan match {
                case ExtractEquiJoinKeys(joinType, leftKeys, rightKeys, condition, leftChild, rightChild, joinHint) =>
                    if (hasValidReadManyJoin(joinType, leftKeys, rightKeys, condition, leftChild, rightChild)) {
                        val (otherBranch, joinTargetBranch, buildType) = {
                            if (leftValid(joinType, leftKeys, leftChild, rightChild)) {
                                (rightChild, leftChild, BuildLeft)
                            } else {
                                (leftChild, rightChild, BuildRight)
                            }
                        }

                        /* We want to take advantage of all of our pushed filter code which happens in
                           full table scans. Unfortunately the pushdown code itself is private within the
                           DataSourceV2Strategy class. To work around this we will invoke DataSourceV2Strategy on
                           our target branch. This will let us know all of the pushable filters that we can
                           use in the direct join.
                        */
                        val dataSourceOptimizedPlan = new DataSourceV2Strategy(spark)(joinTargetBranch).head
                        val cosmosScanExec = getScanExec(dataSourceOptimizedPlan).get

                        cosmosScanExec match {
                            case BatchScanExec(output, scan: ItemsScan) =>
                                // Construct Cosmos direct join exec
                                val readManyJoin =
                                    CosmosReadManyJoinExec(
                                        leftKeys,
                                        rightKeys,
                                        joinType,
                                        buildType,
                                        condition,
                                        planLater(otherBranch), // TODO: as documented, this API may not be stable across different releases, suggested to use stable APIs provided in org.apache.spark.sql.sources
                                        aliasMap(output),
                                        cosmosScanExec
                                    )

                                val newPlan = reorderPlan(dataSourceOptimizedPlan, readManyJoin, plan.output) :: Nil
                                val newOutput = (newPlan.head.outputSet, newPlan.head.output.map(_.name))
                                val oldOutput = (plan.outputSet, plan.output.map(_.name))
                                val noMissingOutput = oldOutput._1.subsetOf(newPlan.head.outputSet)
                                require(noMissingOutput, s"Cosmos ReadMany join Optimization produced invalid output. Original plan output: " +
                                    s"${oldOutput} was not part of ${newOutput} \nOld Plan\n${plan}\nNew Plan\n${newPlan}")
                                newPlan

                            case _ => Nil // unable to do optimization on target branch
                        }
                    } else {
                        Nil
                    }
                case _ => Nil
            }
        } else {
            Nil
        }
    }

    def hasValidReadManyJoin(
                                joinType: JoinType,
                                leftKeys: Seq[Expression],
                                rightKeys: Seq[Expression],
                                condition: Option[Expression],
                                leftPlan: LogicalPlan,
                                rightPlan: LogicalPlan): Boolean = {
        leftValid(joinType, leftKeys, leftPlan, rightPlan) ||
            rightValid(joinType, rightKeys, leftPlan, rightPlan)
    }

    def leftValid(
                     joinType: JoinType,
                     leftKeys: Seq[Expression],
                     leftPlan: LogicalPlan,
                     rightPlan: LogicalPlan): Boolean = {
        validJoinBranch(leftPlan, leftKeys) &&
            validJoinType(BuildLeft, joinType) &&
            checkSizeRatio(leftPlan, rightPlan)
    }

    def rightValid(
                      joinType: JoinType,
                      rightKeys: Seq[Expression],
                      leftPlan: LogicalPlan,
                      rightPlan: LogicalPlan): Boolean = {
        validJoinBranch(rightPlan, rightKeys) &&
            validJoinType(BuildRight, joinType) &&
            checkSizeRatio(rightPlan, leftPlan)
    }

    /**
     * Check whether the plan contains only acceptable logical nodes and all partition keys are joined
     *
     * @param plan        the logical plan
     * @param expressions the keys
     * @return flag to indicate whether it is valid join branch
     */
    def validJoinBranch(plan: LogicalPlan, keys: Seq[Expression]): Boolean = {
        val safePlan = containsSafePlans(plan)
        val idAndPkConstrained = allPartitionKeysAreJoined(plan, keys)

        safePlan && idAndPkConstrained
    }

    /**
     * Check whether a logical plan only contains Cosmos data source scans
     *
     * @param plan the logical plan
     * @return the flag to indicate whether it contains cosmos data source scans
     */
    def containsSafePlans(plan: LogicalPlan): Boolean = {
        plan match {
            case DataSourceV2ScanRelation(DataSourceV2Relation(table: ItemsTable, _, catalog, identifier, options), scan: ItemsScan, output) => true
            case _ => false
        }
    }

    /**
     * Currently, we will only do the readMany optimization if both id and partition key are joined on
     *
     * @param plan     the logical plan
     * @param joinKeys the joined keys
     * @return a flag to indicate whether all partition keys and id are joined
     */
    def allPartitionKeysAreJoined(plan: LogicalPlan, joinKeys: Seq[Expression]): Boolean = {
        plan match {
            case DataSourceV2ScanRelation(DataSourceV2Relation(table: ItemsTable, _, catalog, identifier, options), scan: ItemsScan, output) => {
                val joinKeysExprId = joinKeys.collect { case attributeReference: AttributeReference => attributeReference.exprId }

                val joinKeyAliases = {
                    aliasMap(output).filter { case (_, value) => joinKeysExprId.contains(value) }
                }

                // TODO: the table properties are still empty at this point, how to get it populated
                val partitionKeyDefinitionJsonString =
                    table.tableProperties.getOrDefault(CosmosConstants.TableProperties.PartitionKeyDefinition, "")
                val partitionKeyAndIdNames = new ArrayBuffer[String]()
                partitionKeyAndIdNames += "id" // TODO: change into using properties

                if (!partitionKeyDefinitionJsonString.isEmpty) {
                    val partitionKeyDefinition = SparkModelBridgeInternal.createPartitionKeyDefinitionFromJson(partitionKeyDefinitionJsonString)
                    // TODO: consider how subpartition should work here. Currently readMany does not allow using subpartition key, will throw error
                    partitionKeyDefinition.getPaths.forEach(path => partitionKeyAndIdNames += path)
                }

                // check whether all keys are present
                val allPresents = partitionKeyAndIdNames.forall(joinKeyAliases.contains)
                if (!allPresents) {
                    logDebug(s"Not all $partitionKeyAndIdNames are contained within $joinKeyAliases")
                }

                allPresents
            }

            case _ => false
        }
    }

    def aliasMap(aliases: Seq[NamedExpression]): Map[String, ExprId] = {
        aliases
            .flatMap(expression => {
                expression match {
                    case a@Alias(child: AttributeReference, name) => Seq(child.name -> a.exprId)
                    case a@Alias(child, _) => {
                        val attrs = child.collect {
                            case attr: AttributeReference => attr
                        }
                        // There might be more than one attribute in the aliased expression.
                        // For example, `named_struct(x, y, z) AS alias1`
                        attrs.map(attr => attr.name -> a.exprId)
                    }
                    case attributeReference: AttributeReference => Seq(attributeReference.name -> attributeReference.exprId)
                }
            })
            .toMap
    }

    def validJoinType(buildSide: BuildSide, joinType: JoinType): Boolean = {
        validJoins(buildSide).contains(joinType)
    }

    /**
     * Check whether the size of the cosmos target is larger than the key plan by the sizeRatio specified in the configuration
     *
     * @param cosmosPlan the cosmos target logic plan
     * @param keyPlan    the key target logic plan
     * @return a flag to indicate whether the size ratio valid
     */
    def checkSizeRatio(cosmosPlan: LogicalPlan, keyPlan: LogicalPlan): Boolean = {
        // add size ratio check
        true
    }

    /**
     * Returns the single DataSourceScanExec for the branch if there is one and
     * it scans Cosmos
     */
    def getScanExec(plan: SparkPlan): Option[BatchScanExec] = {
        plan.collectFirst {
            case exec@BatchScanExec(_, scan:ItemsScan) => exec
        }
    }

    /**
     * Given our target Cosmos based branch, we remove the node which draws data
     * from cosmos (DataSourceScanExec) and replace it with the passed readManyJoin plan
     * instead.
     *
     * This should only be called on optimized Physical Plans
     */
    def reorderPlan(
                       plan: SparkPlan,
                       readManyJoin: CosmosReadManyJoinExec,
                       originalOutput: Seq[Attribute]): SparkPlan = {
        val reordered = plan match {
            //This may be the only node in the Plan
            case BatchScanExec(output, scan: ItemsScan) => readManyJoin
            // Plan has children
            case normalPlan => normalPlan.transform {
                case penultimate if hasCosmosChild(penultimate) =>
                    penultimate.withNewChildren(Seq(readManyJoin))
            }
        }

        /*
        The output of our new join node may be missing some aliases which were
        previously applied to columns coming out of cassandra. Take the directJoin output and
        make sure all aliases are correctly applied to the new attributes. Nullability is a
        concern here as columns which may have been non-nullable previously, become nullable in
        a left/right join
        */
        reordered.transform {
            case ProjectExec(projectList, child) =>
                val attrMap = readManyJoin.output.map {
                    case attr => attr.exprId -> attr
                }.toMap

                val aliases = projectList.collect {
                    case a@Alias(child, _) =>
                        val newAliasChild = child.transform {
                            case attr: Attribute => attrMap.getOrElse(attr.exprId, attr)
                        }
                        (a.exprId, a.withNewChildren(newAliasChild :: Nil).asInstanceOf[Alias])
                }.toMap

                // The original output of Join
                val reorderedOutput = originalOutput.map {
                    case attr if aliases.contains(attr.exprId) => aliases(attr.exprId)
                    case other => other
                }

                ProjectExec(reorderedOutput, child)
        }
    }

    /**
     * Checks whether a query plan has either a logical or physical node pulling data from cosmos
     */
    def hasCosmosChild[T <: QueryPlan[T]](plan: T): Boolean = {
        plan.children.size == 1 && plan.children.exists {
            case DataSourceV2ScanRelation(DataSourceV2Relation(table: ItemsTable, _, catalog, identifier, options), scan: ItemsScan, output) => true
            case BatchScanExec(_, _: ItemsScan) => true
            case _ => false
        }
    }

}
