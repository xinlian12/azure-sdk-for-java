package com.azure.cosmos.spark

import org.apache.spark.sql.catalyst.expressions.{Alias, Attribute, AttributeReference, ExprId, Expression, NamedExpression}
import org.apache.spark.sql.catalyst.optimizer.{BuildLeft, BuildRight, BuildSide}
import org.apache.spark.sql.catalyst.plans.logical.{Join, LogicalPlan, ReturnAnswer}
import org.apache.spark.sql.catalyst.plans.{Inner, JoinType, LeftOuter, RightOuter}
import org.apache.spark.sql.execution.datasources.v2.{DataSourceV2Relation, DataSourceV2ScanRelation}
import org.apache.spark.sql.execution.joins.BroadcastHashJoinExec
import org.apache.spark.sql.execution.{PlanLater, SparkPlan}
import org.apache.spark.sql.{SparkSession, Strategy}

import scala.collection.mutable.ArrayBuffer

case class CosmosReadManyJoinStrategy(spark: SparkSession) extends Strategy with Serializable {
    /**
     * Currently we will only support Inner, LeftOuter and RightOuter joins depends on the broadcast hash join build side
     */
    val validJoins: Map[BuildSide, Seq[JoinType]] = Map(
        BuildRight -> Seq[JoinType](Inner, RightOuter),
        BuildLeft -> Seq[JoinType](Inner, LeftOuter)
    )

    override def apply(plan: LogicalPlan): Seq[SparkPlan] = plan match {
        case ReturnAnswer(child) =>
            child match {
                case j@Join(_, _, _, _, _) =>
                    val plannedJoin = spark.sessionState.planner.JoinSelection(j)
                    // we will only target for broadcast hash join

                    plannedJoin.head match { // TODO: validating
                        case BroadcastHashJoinExec(leftKeys, rightKeys, joinType, buildSide, condition, leftChild, rightChild, isNullAwareAntiJoin) =>
                            readManyFilter(leftKeys, rightKeys, joinType, buildSide, condition, leftChild, rightChild, j.output)
                        case _ => Nil
                    }
                case _ => Nil
            }
        case _ => Nil
    }

    def readManyFilter(
                          leftKeys: Seq[Expression],
                          rightKeys: Seq[Expression],
                          joinType: JoinType,
                          buildSide: BuildSide,
                          condition: Option[Expression],
                          leftSparkPlan: SparkPlan,
                          rightSparkPlan: SparkPlan,
                          output: Seq[Attribute]): Seq[SparkPlan] = {
        //TODO: any cases the logical link is none?
        val (buildPlan, buildPlanJoinedKeys, streamingPlan, streamingPlanJoinedKeys) = buildSide match {
            case BuildLeft => (leftSparkPlan, leftKeys, rightSparkPlan, rightKeys)
            case BuildRight => (rightSparkPlan, rightKeys, leftSparkPlan, leftKeys)
        }

        if (canApplyReadManyFilter(joinType, buildSide, streamingPlanJoinedKeys, streamingPlan.asInstanceOf[PlanLater].plan)) {
            Seq(CosmosReadManyFilterExec(
                spark,
                streamingPlanJoinedKeys,
                buildPlanJoinedKeys,
                joinType,
                condition,
                buildPlan,
                streamingPlan,
                output
            ))
        } else {
            Nil
        }
    }

    def canApplyReadManyFilter(joinType: JoinType, buildSide: BuildSide, joinKeys: Seq[Expression], plan: LogicalPlan): Boolean = {
        validJoinBranch(joinKeys, plan) && validJoinType(joinType, buildSide)
    }

    def validJoinType(joinType: JoinType, side: BuildSide): Boolean = {
        validJoins(side).contains(joinType)
    }

    def validJoinBranch(expressions: Seq[Expression], plan: LogicalPlan): Boolean = {
        val isCosmosScan = containsCosmosScan(plan)
        val idAndPkConstrained = idAndPartitionKeyAreJoined(expressions, plan)

        isCosmosScan && idAndPkConstrained
    }

    def containsCosmosScan(plan: LogicalPlan): Boolean = {
        plan match {
            case DataSourceV2ScanRelation(DataSourceV2Relation(_: ItemsTable, _, _, _, _), _: ItemsScan, _) => true
            case _ => false
        }
    }

    def idAndPartitionKeyAreJoined(joinKeys: Seq[Expression], plan: LogicalPlan): Boolean = {
        plan match {
            case DataSourceV2ScanRelation(DataSourceV2Relation(table: ItemsTable, _, _, _, _), scan: ItemsScan, output) =>
                val joinKeysExprId = joinKeys.collect { case attributeReference: AttributeReference => attributeReference.exprId }
                val joinKeyAliases = aliasMap(output).filter { case (_, value) => joinKeysExprId.contains(value) }

                // TODO: figure out why the pkDefinition is empty when reaching here
                val partitionKeyDefinitionJsonString = table.tableProperties.getOrDefault(CosmosConstants.TableProperties.PartitionKeyDefinition, "")
                val idAndPartitionKeyNames = new ArrayBuffer[String]()
                idAndPartitionKeyNames += "id"

                if (!partitionKeyDefinitionJsonString.isEmpty) {
                    // TODO: consider how subpartition should work here. Currently readMany does not allow using subpartition key, will throw error
                    //                    val partitionKeyDefinition = SparkModelBridgeInternal.createPartitionKeyDefinitionFromJson(partitionKeyDefinitionJsonString)
                    //                    partitionKeyDefinition.getPaths.forEach(path => idAndPartitionKeyNames += path)
                }

                // check whether id and partition keys all exists
                idAndPartitionKeyNames.forall(joinKeyAliases.contains)
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
}
