package com.azure.cosmos.spark

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.{Attribute, AttributeSeq, BindReferences, Expression, SafeProjection}
import org.apache.spark.sql.catalyst.plans.JoinType
import org.apache.spark.sql.catalyst.plans.physical.BroadcastMode
import org.apache.spark.sql.execution.datasources.v2.BatchScanExec
import org.apache.spark.sql.execution.joins.{HashJoin, HashedRelationBroadcastMode}
import org.apache.spark.sql.execution.{SparkPlan, UnaryExecNode}

import scala.collection.mutable.ArrayBuffer

case class CosmosReadManyFilterExec(
                                                       sparkSession: SparkSession,
                                                       streamingPlanJoinedKeys: Seq[Expression],
                                                       buildPlanJoinedKeys: Seq[Expression],
                                                       joinType: JoinType,
                                                       condition: Option[Expression],
                                                       buildPlan: SparkPlan,
                                                       cosmosPlan: SparkPlan,
                                                       originalOutput: Seq[Attribute]
                                                   ) extends UnaryExecNode { // TODO: is it right to change this into a unary exec code?
    val cosmosScan = getScanExec(cosmosPlan).get.scan.asInstanceOf[ItemsScan]
    override def child: SparkPlan = buildPlan // TODO: what about the other child
    override def output: Seq[Attribute] = originalOutput

    override protected def doExecute(): RDD[InternalRow] = {
        val result = buildPlan.execute()
        val broadcastResult = sparkSession.sparkContext.broadcast(result)

        // based on the result to calculate the predicates
        // TODO: figure out how to pass in the index properly
//        val index = 0
//        val expression = BoundReference(0, buildPlanJoinedKeys(index).dataType, buildPlanJoinedKeys(index).nullable)
        val safeProjection = SafeProjection.create(buildPlan.schema)
        val rows = broadcastResult.value.map(safeProjection).map(_.copy())
        val predicates = new ArrayBuffer[String]()
        rows.foreach(r => {
            val id = r.getString(0) // TODO: populate real index
            predicates += id
        })
        cosmosScan.filterByIdAndPartitionKey(predicates.toList)
        cosmosPlan.execute()
    }


    /**
     * Identify the shape in which keys of a given plan are broadcasted.
     */
    private def broadcastMode(keys: Seq[Expression], output: AttributeSeq): BroadcastMode = {
        val packedKeys = BindReferences.bindReferences(HashJoin.rewriteKeyExpr(keys), output)
        HashedRelationBroadcastMode(packedKeys)
    }

    def getScanExec(plan: SparkPlan): Option[BatchScanExec] = {
        plan.collectFirst {
            case exec@BatchScanExec(_, scan: ItemsScan) => exec
        }
    }
}
