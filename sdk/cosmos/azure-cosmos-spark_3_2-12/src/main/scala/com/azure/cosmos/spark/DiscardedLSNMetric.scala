// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.azure.cosmos.spark

import org.apache.spark.sql.connector.metric.CustomMetric

private[spark] class DiscardedLSNMetric extends CustomMetric {
  override def name(): String = CosmosConstants.MetricNames.DiscardedLSN
  override def description(): String = CosmosConstants.MetricNames.DiscardedLSN

    def aggregateTaskMetrics(taskMetrics: Array[Long]): String = {
        taskMetrics.sum.toString
    }
}
