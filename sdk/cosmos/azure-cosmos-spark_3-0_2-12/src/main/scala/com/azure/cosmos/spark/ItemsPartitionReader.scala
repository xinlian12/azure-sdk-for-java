// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.spark

import com.azure.cosmos.implementation.CosmosClientMetadataCachesSnapshot
import com.azure.cosmos.models.{CosmosParameterizedQuery, CosmosQueryRequestOptions}
import com.azure.cosmos.{CosmosAsyncContainer, CosmosClientBuilder, CosmosException, ThroughputControlGroupConfigBuilder}
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.connector.read.PartitionReader
import org.apache.spark.sql.types.StructType

// per spark task there will be one CosmosPartitionReader.
// This provides iterator to read from the assigned spark partition
// For now we are creating only one spark partition
private case class ItemsPartitionReader
(
  config: Map[String, String],
  readSchema: StructType,
  cosmosQuery: CosmosParameterizedQuery,
  cosmosClientStateHandle: Broadcast[CosmosClientMetadataCachesSnapshot]
)
// TODO: moderakh query need to change to SqlSpecQuery
// requires making a serializable wrapper on top of SqlQuerySpec
  extends PartitionReader[InternalRow] with CosmosLoggingTrait {
  logInfo(s"Instantiated ${this.getClass.getSimpleName}")

  private val containerTargetConfig = CosmosContainerConfig.parseCosmosContainerConfig(config)
  private val readConfig = CosmosReadConfig.parseCosmosReadConfig(config)
  private val client = CosmosClientCache(CosmosClientConfiguration(config, readConfig.forceEventualConsistency), Some(cosmosClientStateHandle))

  private val cosmosAsyncContainer = getContainerWithThroughputControl()

  private lazy val iterator = cosmosAsyncContainer.queryItems(
    cosmosQuery.toSqlQuerySpec,
    new CosmosQueryRequestOptions(),
    classOf[ObjectNode]).toIterable.iterator()

    private def getContainerWithThroughputControl(): CosmosAsyncContainer = {
        val container = client
            .getDatabase(containerTargetConfig.database)
            .getContainer(containerTargetConfig.container)

        val throughputControlGroupConfig = CosmosThroughputControlGroupConfig.parseThroughputControlGroupConfig(config, None)

        if (throughputControlGroupConfig.isDefined) {
            val cosmosGroupConfig = throughputControlGroupConfig.get

            val groupConfigBuilder = new ThroughputControlGroupConfigBuilder()
                .setGroupName(cosmosGroupConfig.groupName)
                .setDefault(true)

            if (cosmosGroupConfig.targetThroughput.isDefined) {
                groupConfigBuilder.setTargetThroughput(cosmosGroupConfig.targetThroughput.get)
            }
            if (cosmosGroupConfig.targetThroughputThreshold.isDefined) {
                groupConfigBuilder.setTargetThroughputThreshold(cosmosGroupConfig.targetThroughputThreshold.get)
            }

            val globalControlClient = new CosmosClientBuilder()
                .endpoint(cosmosGroupConfig.globalControlAccountConfig.endpoint)
                .key(cosmosGroupConfig.globalControlAccountConfig.key)
                .buildAsyncClient()

            val globalThroughputControlConfig =
                globalControlClient.createGlobalThroughputControlConfigBuilder(
                    cosmosGroupConfig.globalControlContainerConfig.database,
                    cosmosGroupConfig.globalControlContainerConfig.container)
                    .build()

            container.enableThroughputGlobalControlGroup(groupConfigBuilder.build(), globalThroughputControlConfig)
        }

        container
    }

  override def next(): Boolean = iterator.hasNext

  override def get(): InternalRow = {
      var exceptionOpt = Option.empty[Exception]

      try {
          val objectNode = iterator.next()
          CosmosRowConverter.fromObjectNodeToInternalRow(readSchema, objectNode)
      } catch {
          case e: CosmosException if Exceptions.isResourceExistsException(e) =>
              // TODO: what should we do on unique index violation? should we ignore or throw?
              // TODO moderakh we need to add log messages extract identifier (id, pk) and log
              exceptionOpt = Option.apply(e)
          case e: CosmosException if Exceptions.isRequestRateTooLargeException(e) =>
              exceptionOpt = Option.apply(e)
          case e: CosmosException if Exceptions.canBeTransientFailure(e) =>
              exceptionOpt = Option.apply(e)
      }

      InternalRow.empty
      // throw exceptionOpt.get
  }

  override def close(): Unit = {
    // TODO moderakh manage the lifetime of the cosmos clients
  }
}
