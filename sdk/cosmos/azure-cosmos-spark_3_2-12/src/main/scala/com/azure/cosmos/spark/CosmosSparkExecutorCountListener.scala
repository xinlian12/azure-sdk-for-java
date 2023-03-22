// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.spark

import com.azure.cosmos.spark.diagnostics.BasicLoggingTrait
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.scheduler.{SparkListener, SparkListenerExecutorAdded, SparkListenerExecutorRemoved}
import org.apache.spark.sql.SparkSession

private[spark] case class CosmosSparkExecutorCountListener()
    extends SparkListener
        with BasicLoggingTrait {

    private var executorCountBroadcast = this.initialize()

    def getExecutorCountBroadcast(): Broadcast[Int] = {
        logInfo(s"getExecutorCountBroadcast: ${this.executorCountBroadcast.value}")
        this.executorCountBroadcast
    }

    def initialize(): Broadcast[Int] = {
        logInfo("Initializing CosmosSparkExecutorCountListener")
        val ctx = SparkSession.active.sparkContext
        val executorCountBroadcast = ctx.broadcast(ctx.statusTracker.getExecutorInfos.length - 1)
        ctx.addSparkListener(this)

        executorCountBroadcast
    }

    override def onExecutorAdded(executorAdded: SparkListenerExecutorAdded): Unit = {
        val sparkSession = SparkSession.active

        // getExecutorInfo will return information for both driver and executor
        // and what we really want is the executor count, so -1
        val currentExecutorCount = sparkSession.sparkContext.statusTracker.getExecutorInfos.size - 1

        logInfo(s"Executor is added. Destroy the current one. Before [${executorCountBroadcast.value}], after [$currentExecutorCount]")
        executorCountBroadcast.destroy()
        executorCountBroadcast = sparkSession.sparkContext.broadcast(currentExecutorCount)
    }


    override def onExecutorRemoved(executorRemoved: SparkListenerExecutorRemoved): Unit = {
        val sparkSession = SparkSession.active
        val currentExecutorCount = sparkSession.sparkContext.statusTracker.getExecutorInfos.length - 1

        logInfo(s"Executor is removed. Destroy the current one. Before [${executorCountBroadcast.value}], after [$currentExecutorCount]")
        executorCountBroadcast.destroy()
        executorCountBroadcast = sparkSession.sparkContext.broadcast(currentExecutorCount)
    }
}
