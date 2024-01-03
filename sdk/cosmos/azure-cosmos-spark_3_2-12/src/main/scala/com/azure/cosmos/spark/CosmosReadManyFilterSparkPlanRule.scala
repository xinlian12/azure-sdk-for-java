package com.azure.cosmos.spark

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.optimizer.{BuildLeft, BuildRight}
import org.apache.spark.sql.catalyst.rules.Rule
import org.apache.spark.sql.execution.SparkPlan
import org.apache.spark.sql.execution.joins.BroadcastHashJoinExec

case class CosmosReadManyFilterSparkPlanRule (sparkSession: SparkSession) extends Rule[SparkPlan] {
    override def apply(plan: SparkPlan): SparkPlan = {
        plan match {
            case BroadcastHashJoinExec(leftKeys, rightKeys, joinType, BuildLeft, condition, left, right, isNullAwareAntiJoin) =>
                System.out.println("it is broadcast has join")
            case BroadcastHashJoinExec(leftKeys, rightKeys, joinType, BuildRight, condition, left, right, isNullAwareAntiJoin) =>
                System.out.println("it is broadcast has join")
            case _ =>
                System.out.println("it matched nothing")
        }
        plan
    }
}
