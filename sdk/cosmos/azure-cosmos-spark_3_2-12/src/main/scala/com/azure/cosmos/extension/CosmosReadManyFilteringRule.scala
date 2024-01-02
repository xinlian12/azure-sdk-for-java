package com.azure.cosmos.extension

import org.apache.spark.sql.catalyst.expressions.PredicateHelper
import org.apache.spark.sql.catalyst.optimizer.JoinSelectionHelper
import org.apache.spark.sql.catalyst.plans.logical.{LogicalPlan, ReturnAnswer, Subquery}
import org.apache.spark.sql.catalyst.rules.Rule

class CosmosReadManyFilteringRule extends Rule[LogicalPlan] with PredicateHelper with JoinSelectionHelper{
    override def apply(plan: LogicalPlan): LogicalPlan = plan match {
        case r: ReturnAnswer => {
            val logicalPlan
        }
    }

    def prune(plan: LogicalPlan): LogicalPlan = {

    }
}
