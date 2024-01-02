package com.azure.cosmos.spark

import com.azure.cosmos.models.SparkModelBridgeInternal
import org.apache.spark.sql.catalyst.expressions.{Alias, AttributeReference, DynamicPruningSubquery, EqualTo, ExprId, Expression, NamedExpression, PredicateHelper}
import org.apache.spark.sql.catalyst.optimizer.{BuildLeft, BuildRight, BuildSide, JoinSelectionHelper}
import org.apache.spark.sql.catalyst.planning.ExtractEquiJoinKeys
import org.apache.spark.sql.catalyst.plans.logical.{Filter, Join, LogicalPlan, Subquery}
import org.apache.spark.sql.catalyst.plans.{Inner, JoinType, LeftOuter, RightOuter}
import org.apache.spark.sql.catalyst.rules.Rule
import org.apache.spark.sql.execution.datasources.v2.{DataSourceV2Relation, DataSourceV2ScanRelation}

import scala.collection.mutable.ArrayBuffer

case class CosmosReadManyFilteringRule() extends Rule[LogicalPlan] with PredicateHelper with JoinSelectionHelper{
    /**
     * Currently we will only support Inner, LeftOuter and RightOuter joins
     * Depending on the side of the CassandraTarget different joins are allowed.
     */
    val validJoins: Map[BuildSide, Seq[JoinType]] = Map(
        BuildRight -> Seq[JoinType](Inner, LeftOuter),
        BuildLeft -> Seq[JoinType](Inner, RightOuter)
    )

    override def apply(plan: LogicalPlan): LogicalPlan = plan match {
        case s: Subquery if s.correlated => plan
        case _ => prune(plan)
        case _ => plan
    }

    def prune(originalPlan: LogicalPlan): LogicalPlan = {
        originalPlan transformUp {
            // skip this rule if there's already a DPP subquery on the LHS of a join
            case j @ Join(Filter(_: DynamicPruningSubquery, _), _, _, _, _) => j
            case j @ Join(_, Filter(_: DynamicPruningSubquery, _), _, _, _) => j
            case j @ Join(left, right, joinType, Some(condition), hint) =>
                val newLeft = left
                val newRight = right

                j match {
                    case ExtractEquiJoinKeys(joinType, leftKeys, rightKeys, _, leftChild, rightChild, _) => {
                        // get the item scan
                        val filterableScanOpt = canApplyReadManyFilter(
                            joinType,
                            leftKeys,
                            rightKeys,
                            leftChild,
                            rightChild)

                        if (filterableScanOpt.isDefined) {
                            splitConjunctivePredicates(condition).foreach {
                                case EqualTo(a: Expression, b: Expression) =>
                                    val (l, r) = if (a.references.subsetOf(left.outputSet) && b.references.subsetOf(right.outputSet)) {
                                        a -> b
                                    } else {
                                        b -> a
                                    }

                                    insertPredicate(l, newLeft, r, newRight, rightKeys)
                            }
                        }
                        Join(newLeft, newRight, joinType, Some(condition), hint)
                    }
                    case _ => originalPlan
                }
            case _ => originalPlan
        }
    }

    def canApplyReadManyFilter(
                                joinType: JoinType,
                                leftKeys: Seq[Expression],
                                rightKeys: Seq[Expression],
                                leftPlan: LogicalPlan,
                                rightPlan: LogicalPlan): Option[LogicalPlan] = {
        if (leftValid(joinType, leftKeys, leftPlan, rightPlan)) {
            Some(leftPlan)
        }
        else if (rightValid(joinType, rightKeys, leftPlan, rightPlan)) {
            Some(rightPlan)
        } else {
            None
        }
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

    private def insertPredicate(
                                   pruningKey: Expression,
                                   pruningPlan: LogicalPlan,
                                   filteringKey: Expression,
                                   filteringPlan: LogicalPlan,
                                   joinKeys: Seq[Expression]): LogicalPlan = {
        val index = joinKeys.indexOf(filteringKey)
        Filter(
            DynamicPruningSubquery(
                pruningKey,
                filteringPlan,
                joinKeys,
                index,
                true
            ),
            pruningPlan
        )
    }
}
