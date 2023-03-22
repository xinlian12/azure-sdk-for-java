package com.azure.cosmos.spark

import com.azure.cosmos.spark.diagnostics.BasicLoggingTrait
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.scheduler.{SparkListener, SparkListenerExecutorAdded, SparkListenerExecutorRemoved}
import org.apache.spark.sql.SparkSession

private[cosmos] case class SparkExecutorListener()
    extends SparkListener
        with BasicLoggingTrait {

    private var executorCountBroadcast = this.initializeAndBroadcastExecutorCount()

    def getExecutorCountBroadcast(): Broadcast[Integer] = {
        this.executorCountBroadcast
    }

    override def onExecutorAdded(executorAdded: SparkListenerExecutorAdded): Unit = {
        val sparkSession = SparkSession.active
        val currentExecutorCount = sparkSession.sparkContext.getExecutorMemoryStatus.size - 1

        logInfo(s"Executor is being added ${currentExecutorCount}")
       // executorCountBroadcast.unpersist()
        executorCountBroadcast = sparkSession.sparkContext.broadcast(currentExecutorCount)
    }

    override def onExecutorRemoved(executorRemoved: SparkListenerExecutorRemoved): Unit = {
        val sparkSession = SparkSession.active
        val currentExecutorCount = sparkSession.sparkContext.getExecutorMemoryStatus.size - 1

        logInfo(s"Executor is being removed ${currentExecutorCount}")
      //  executorCountBroadcast.unpersist()
        executorCountBroadcast = sparkSession.sparkContext.broadcast(currentExecutorCount)
    }

    private[cosmos] def initializeAndBroadcastExecutorCount(): Broadcast[Integer] = {
        val sparkSession = SparkSession.active
        val currentExecutorCount = sparkSession.sparkContext.getExecutorMemoryStatus.size - 1
        logInfo(s"There are $currentExecutorCount executors")

        sparkSession.sparkContext.broadcast(currentExecutorCount)
    }
}
