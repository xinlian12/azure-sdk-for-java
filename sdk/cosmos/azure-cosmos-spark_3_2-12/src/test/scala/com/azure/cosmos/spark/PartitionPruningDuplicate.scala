// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.spark

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.catalog.HiveTableRelation
import org.apache.spark.sql.catalyst.expressions.{Attribute, AttributeSet, DynamicPruningSubquery, EqualTo, Expression, PredicateHelper, V2ExpressionUtils}
import org.apache.spark.sql.catalyst.optimizer.JoinSelectionHelper
import org.apache.spark.sql.catalyst.planning.ExtractEquiJoinKeys
import org.apache.spark.sql.catalyst.plans.logical.{Filter, Join, LogicalPlan, Subquery}
import org.apache.spark.sql.catalyst.rules.Rule
import org.apache.spark.sql.connector.read.SupportsRuntimeV2Filtering
import org.apache.spark.sql.execution.columnar.InMemoryRelation
import org.apache.spark.sql.execution.datasources.v2.DataSourceV2ScanRelation
import org.apache.spark.sql.execution.datasources.{HadoopFsRelation, LogicalRelation}

// scalastyle:off
case class PartitionPruningDuplicate(sparkSession: SparkSession) extends Rule[LogicalPlan] with PredicateHelper with JoinSelectionHelper {

    /**
     * Searches for a table scan that can be filtered for a given column in a logical plan.
     *
     * This methods tries to find either a v1 or Hive serde partitioned scan for a given
     * partition column or a v2 scan that support runtime filtering on a given attribute.
     */
    def getFilterableTableScan(a: Expression, plan: LogicalPlan): Option[LogicalPlan] = {
        val srcInfo: Option[(Expression, LogicalPlan)] = findExpressionAndTrackLineageDown(a, plan)
        srcInfo.flatMap {
            case (resExp, l: LogicalRelation) =>
                l.relation match {
                    case fs: HadoopFsRelation =>
                        val partitionColumns = AttributeSet(
                            l.resolve(fs.partitionSchema, sparkSession.sessionState.analyzer.resolver))
                        if (resExp.references.subsetOf(partitionColumns)) {
                            return Some(l)
                        } else {
                            None
                        }
                    case _ => None
                }
            case (resExp, l: HiveTableRelation) =>
                if (resExp.references.subsetOf(AttributeSet(l.partitionCols))) {
                    return Some(l)
                } else {
                    None
                }
            case (resExp, r @ DataSourceV2ScanRelation(_, scan: SupportsRuntimeV2Filtering, _, _, _)) =>
                val filterAttrs = V2ExpressionUtils.resolveRefs[Attribute](scan.filterAttributes, r)
                if (resExp.references.subsetOf(AttributeSet(filterAttrs))) {
                    Some(r)
                } else {
                    None
                }
            case _ => None
        }
    }

    /**
     * Insert a dynamic partition pruning predicate on one side of the join using the filter on the
     * other side of the join.
     *  - to be able to identify this filter during query planning, we use a custom
     *    DynamicPruning expression that wraps a regular In expression
     *  - we also insert a flag that indicates if the subquery duplication is worthwhile and it
     *  should run regardless of the join strategy, or is too expensive and it should be run only if
     *  we can reuse the results of a broadcast
     */
    private def insertPredicate(
                                   pruningKey: Expression,
                                   pruningPlan: LogicalPlan,
                                   filteringKey: Expression,
                                   filteringPlan: LogicalPlan,
                                   joinKeys: Seq[Expression],
                                   partScan: LogicalPlan): LogicalPlan = {
        val reuseEnabled = conf.exchangeReuseEnabled
        val index = joinKeys.indexOf(filteringKey)
        lazy val hasBenefit = pruningHasBenefit(pruningKey, partScan, filteringKey, filteringPlan)
        if (reuseEnabled || hasBenefit) {
            // insert a DynamicPruning wrapper to identify the subquery during query planning
            Filter(
                DynamicPruningSubquery(
                    pruningKey,
                    filteringPlan,
                    joinKeys,
                    index,
                    conf.dynamicPartitionPruningReuseBroadcastOnly || !hasBenefit),
                pruningPlan)
        } else {
            // abort dynamic partition pruning
            pruningPlan
        }
    }

    /**
     * Given an estimated filtering ratio we assume the partition pruning has benefit if
     * the size in bytes of the partitioned plan after filtering is greater than the size
     * in bytes of the plan on the other side of the join. We estimate the filtering ratio
     * using column statistics if they are available, otherwise we use the config value of
     * `spark.sql.optimizer.dynamicPartitionPruning.fallbackFilterRatio`.
     */
    private def pruningHasBenefit(
                                     partExpr: Expression,
                                     partPlan: LogicalPlan,
                                     otherExpr: Expression,
                                     otherPlan: LogicalPlan): Boolean = {

        // get the distinct counts of an attribute for a given table
        def distinctCounts(attr: Attribute, plan: LogicalPlan): Option[BigInt] = {
            plan.stats.attributeStats.get(attr).flatMap(_.distinctCount)
        }

        // the default filtering ratio when CBO stats are missing, but there is a
        // predicate that is likely to be selective
        val fallbackRatio = conf.dynamicPartitionPruningFallbackFilterRatio
        // the filtering ratio based on the type of the join condition and on the column statistics
        val filterRatio = (partExpr.references.toList, otherExpr.references.toList) match {
            // filter out expressions with more than one attribute on any side of the operator
            case (leftAttr :: Nil, rightAttr :: Nil)
                if conf.dynamicPartitionPruningUseStats =>
                // get the CBO stats for each attribute in the join condition
                val partDistinctCount = distinctCounts(leftAttr, partPlan)
                val otherDistinctCount = distinctCounts(rightAttr, otherPlan)
                val availableStats = partDistinctCount.isDefined && partDistinctCount.get > 0 &&
                    otherDistinctCount.isDefined
                if (!availableStats) {
                    fallbackRatio
                } else if (partDistinctCount.get.toDouble <= otherDistinctCount.get.toDouble) {
                    // there is likely an estimation error, so we fallback
                    fallbackRatio
                } else {
                    1 - otherDistinctCount.get.toDouble / partDistinctCount.get.toDouble
                }
            case _ => fallbackRatio
        }

        val estimatePruningSideSize = filterRatio * partPlan.stats.sizeInBytes.toFloat
        val overhead = calculatePlanOverhead(otherPlan)
        estimatePruningSideSize > overhead
    }

    /**
     * Calculates a heuristic overhead of a logical plan. Normally it returns the total
     * size in bytes of all scan relations. We don't count in-memory relation which uses
     * only memory.
     */
    private def calculatePlanOverhead(plan: LogicalPlan): Float = {
        val (cached, notCached) = plan.collectLeaves().partition(p => p match {
            case _: InMemoryRelation => true
            case _ => false
        })
        val scanOverhead = notCached.map(_.stats.sizeInBytes).sum.toFloat
        val cachedOverhead = cached.map {
            case m: InMemoryRelation if m.cacheBuilder.storageLevel.useDisk &&
                !m.cacheBuilder.storageLevel.useMemory =>
                m.stats.sizeInBytes.toFloat
            case m: InMemoryRelation if m.cacheBuilder.storageLevel.useDisk =>
                m.stats.sizeInBytes.toFloat * 0.2
            case m: InMemoryRelation if m.cacheBuilder.storageLevel.useMemory =>
                0.0
        }.sum.toFloat
        scanOverhead + cachedOverhead
    }


    /**
     * Search a filtering predicate in a given logical plan
     */
    private def hasSelectivePredicate(plan: LogicalPlan): Boolean = {
        plan.exists {
            case f: Filter => isLikelySelective(f.condition)
            case others: Any => {
                System.out.print(others.getClass)
                false
            }
        }
    }

    /**
     * To be able to prune partitions on a join key, the filtering side needs to
     * meet the following requirements:
     *   (1) it can not be a stream
     *   (2) it needs to contain a selective predicate used for filtering
     */
    private def hasPartitionPruningFilter(plan: LogicalPlan): Boolean = {
        !plan.isStreaming && hasSelectivePredicate(plan)
    }

    private def prune(plan: LogicalPlan): LogicalPlan = {
        plan transformUp {
            // skip this rule if there's already a DPP subquery on the LHS of a join
            case j @ Join(Filter(_: DynamicPruningSubquery, _), _, _, _, _) => j
            case j @ Join(_, Filter(_: DynamicPruningSubquery, _), _, _, _) => j
            case j @ Join(left, right, joinType, Some(condition), hint) =>
                var newLeft = left
                var newRight = right

                // extract the left and right keys of the join condition
                val (leftKeys, rightKeys) = j match {
                    case ExtractEquiJoinKeys(_, lkeys, rkeys, _, _, _, _, _) => (lkeys, rkeys)
                    case _ => (Nil, Nil)
                }

                // checks if two expressions are on opposite sides of the join
                def fromDifferentSides(x: Expression, y: Expression): Boolean = {
                    def fromLeftRight(x: Expression, y: Expression) =
                        !x.references.isEmpty && x.references.subsetOf(left.outputSet) &&
                            !y.references.isEmpty && y.references.subsetOf(right.outputSet)
                    fromLeftRight(x, y) || fromLeftRight(y, x)
                }

                splitConjunctivePredicates(condition).foreach {
                    case EqualTo(a: Expression, b: Expression)
                        if fromDifferentSides(a, b) =>
                        val (l, r) = if (a.references.subsetOf(left.outputSet) &&
                            b.references.subsetOf(right.outputSet)) {
                            a -> b
                        } else {
                            b -> a
                        }

                        // there should be a partitioned table and a filter on the dimension table,
                        // otherwise the pruning will not trigger
                        var filterableScan = getFilterableTableScan(l, left)
                        if (hasPartitionPruningFilter(right)) {
                            newLeft = newLeft
                            //newLeft = insertPredicate(l, newLeft, r, right, rightKeys, filterableScan.get)
                        } else {
                            filterableScan = getFilterableTableScan(r, right)
                            if (filterableScan.isDefined && canPruneRight(joinType) &&
                                hasPartitionPruningFilter(left) ) {
                                newRight = insertPredicate(r, newRight, l, left, leftKeys, filterableScan.get)
                            }
                        }
                    case _ =>
                }
                Join(newLeft, newRight, joinType, Some(condition), hint)
        }
    }

    override def apply(plan: LogicalPlan): LogicalPlan = plan match {
        // Do not rewrite subqueries.
        case s: Subquery if s.correlated => plan
        case _ if !conf.dynamicPartitionPruningEnabled => plan
        case _ => prune(plan)
    }
}
