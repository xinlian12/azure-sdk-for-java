// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.spark

import com.azure.cosmos.models.{CosmosItemIdentity, CosmosParameterizedQuery}
import com.azure.cosmos.spark.diagnostics.{DiagnosticsContext, LoggerHelper}
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.connector.read.{InputPartition, PartitionReader, PartitionReaderFactory}
import org.apache.spark.sql.types.StructType

import java.util.concurrent.atomic.AtomicReference

private case class ItemsScanPartitionReaderFactory
(
  config: Map[String, String],
  readSchema: StructType,
  cosmosQuery: CosmosParameterizedQuery,
  diagnosticsOperationContext: DiagnosticsContext,
  cosmosClientStateHandles: Broadcast[CosmosClientMetadataCachesSnapshots],
  diagnosticsConfig: DiagnosticsConfig,
  sparkEnvironmentInfo: String,
  readManyFilters: AtomicReference[List[CosmosItemIdentity]]
) extends PartitionReaderFactory {

  @transient private lazy val log = LoggerHelper.getLogger(diagnosticsConfig, this.getClass)
  log.logTrace(s"Instantiated ${this.getClass.getSimpleName}")

  override def createReader(partition: InputPartition): PartitionReader[InternalRow] = {
    val feedRange = partition.asInstanceOf[CosmosInputPartition].feedRange
    log.logInfo(s"Creating an ItemsPartitionReader to read from feed-range [$feedRange]")

      if (readManyFilters.get().isEmpty) {
          ItemsPartitionReader(config,
              feedRange,
              readSchema,
              cosmosQuery,
              diagnosticsOperationContext,
              cosmosClientStateHandles,
              diagnosticsConfig,
              sparkEnvironmentInfo)
      } else {
          log.logInfo(s"Creating readMany filter with ${readManyFilters.get().size}")
          ItemsPartitionReader(config,
              feedRange,
              readSchema,
              cosmosQuery,
              diagnosticsOperationContext,
              cosmosClientStateHandles,
              diagnosticsConfig,
              sparkEnvironmentInfo)
      }
  }
}
