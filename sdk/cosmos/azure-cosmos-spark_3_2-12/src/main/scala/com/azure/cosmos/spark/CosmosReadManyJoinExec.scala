package com.azure.cosmos.spark

import com.azure.cosmos.models.{CosmosItemIdentity, PartitionKey}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.BindReferences.bindReferences
import org.apache.spark.sql.catalyst.expressions.{Attribute, AttributeSeq, ExprId, Expression, SafeProjection, UnsafeProjection}
import org.apache.spark.sql.catalyst.optimizer.{BuildLeft, BuildSide}
import org.apache.spark.sql.catalyst.plans.{ExistenceJoin, InnerLike, JoinType, LeftExistence, LeftOuter, RightOuter}
import org.apache.spark.sql.execution.datasources.v2.BatchScanExec
import org.apache.spark.sql.execution.joins.HashJoin
import org.apache.spark.sql.execution.{SparkPlan, UnaryExecNode}

import scala.collection.mutable.ArrayBuffer

case class CosmosReadManyJoinExec (   sparkSession: SparkSession,
                                      leftKeys: Seq[Expression],
                                      rightKeys: Seq[Expression],
                                      joinType: JoinType,
                                      cosmosSide: BuildSide,
                                      condition: Option[Expression],
                                      otherBranchPlan: SparkPlan,
                                      aliasMap: Map[String, ExprId],
                                      cosmosPlan: BatchScanExec) extends UnaryExecNode {
    val cosmosScan = cosmosPlan.scan.asInstanceOf[ItemsScan]

    val (left, right, idAndPartitionKeys) = if (cosmosSide == BuildLeft) {
        val idAndPartitionKeys = bindReferences(HashJoin.rewriteKeyExpr(rightKeys), otherBranchPlan.output)
        (cosmosPlan, otherBranchPlan, idAndPartitionKeys)
    } else {
        val idAndPartitionKeys = bindReferences(HashJoin.rewriteKeyExpr(leftKeys), cosmosPlan.output)
        (otherBranchPlan, cosmosPlan, idAndPartitionKeys)
    }

    override protected def doExecute(): RDD[InternalRow] = {
        /* UnsafeRows are pointers to spots in memory and when our
         * UnsafeProject is called on the next element it rewrites our first
         * pointer. Since we call our executions async we end up losing
         * the pointer to the join key unless we make a copy of the pointer
         *
         *
         * see @UnsafeRow.copy()
         * see @UnsafeProjection
         */
        // method 1: broadcast the whole result
        val idAndPartitionKeyFilterGenerator = SafeProjection.create(idAndPartitionKeys)
        val idAndPartitionKeyValueFilters = new ArrayBuffer[CosmosItemIdentity]()
        val executionResult = otherBranchPlan.execute()
        executionResult.foreach(
            r => {
                val projection = SafeProjection.create(otherBranchPlan.schema)
                val mappedRow = projection.apply(r).copy()
                val id = mappedRow.toSeq(otherBranchPlan.schema).head // TODO: figure out how to get the id
                idAndPartitionKeyValueFilters += new CosmosItemIdentity(new PartitionKey(id.toString), id.toString)
            }
        )

        // filter the partitions
        cosmosScan.applyReadManyFilter(idAndPartitionKeyValueFilters.toList)

//            .mapPartitions(it => {
//                val projection = UnsafeProjection.create(otherBranchPlan.schema)
//                it.map(row => projection.apply(row).copy())
//            })

        System.out.print("Delegates back the original spark plan")
        cosmosPlan.doExecute()
    }

    def readResult(otherBranchResult: RDD[InternalRow]): Unit = {
        val idAndPartitionKeyFilterGenerator = UnsafeProjection.create(idAndPartitionKeys)

        val copy = otherBranchResult
        copy
        .foreach(internalRow => {
         //   val idAndPartitionKeyValue = idAndPartitionKeyFilterGenerator.apply(internalRow)
            System.out.println("Something is wrong and good")

            val projection = SafeProjection.create(otherBranchPlan.schema)
            val mappedRow = projection.apply(internalRow).copy()
//            // TODO: get the id and partition key column
            val id = mappedRow.toSeq(otherBranchPlan.schema).head
//            val partitionKeyValue = id
//            // how to pass don
//
//            System.out.println(mappedRow)
        })
    }

    override def output: Seq[Attribute] = {
        joinType match {
            case _: InnerLike =>
                left.output ++ right.output
            case LeftOuter =>
                left.output ++ right.output.map(_.withNullability(true))
            case RightOuter =>
                left.output.map(_.withNullability(true)) ++ right.output
            case j: ExistenceJoin =>
                left.output :+ j.exists
            case LeftExistence(_) =>
                left.output
            case x =>
                throw new IllegalArgumentException(s"CosmosReadManyJoin should not take $x as the JoinType")
        }
    }

    override def child: SparkPlan = otherBranchPlan
}
