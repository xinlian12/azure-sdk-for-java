// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.spark

import com.azure.cosmos.implementation.spark.{OperationContextAndListenerTuple, OperationListener}
import com.azure.cosmos.implementation.{ImplementationBridgeHelpers, SparkBridgeImplementationInternal, SparkRowItem, Strings}
import com.azure.cosmos.models.{CosmosItemIdentity, CosmosParameterizedQuery, CosmosQueryRequestOptions, ModelBridgeInternal, PartitionKeyDefinition}
import com.azure.cosmos.spark.BulkWriter.getThreadInfo
import com.azure.cosmos.spark.diagnostics.{DetailedFeedDiagnosticsProvider, DiagnosticsContext, DiagnosticsLoader, LoggerHelper, SparkTaskContext}
import com.azure.cosmos.util.UtilBridgeInternal
import org.apache.spark.TaskContext
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql.Row
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.encoders.ExpressionEncoder
import org.apache.spark.sql.connector.read.PartitionReader
import org.apache.spark.sql.types.StructType
import reactor.core.publisher.Flux

import scala.collection.JavaConverters._
import scala.::

// per spark task there will be one CosmosPartitionReader.
// This provides iterator to read from the assigned spark partition
// For now we are creating only one spark partition
private case class ItemsPartitionReaderWithReadMany
(
    config: Map[String, String],
    feedRange: NormalizedRange,
    readSchema: StructType,
    diagnosticsContext: DiagnosticsContext,
    cosmosClientStateHandles: Broadcast[CosmosClientMetadataCachesSnapshots],
    diagnosticsConfig: DiagnosticsConfig,
    sparkEnvironmentInfo: String,
    aggregatedFilter: List[CosmosItemIdentity]
)
    extends PartitionReader[InternalRow] {

    private lazy val log = LoggerHelper.getLogger(diagnosticsConfig, this.getClass)

    private val queryOptions = ImplementationBridgeHelpers
        .CosmosQueryRequestOptionsHelper
        .getCosmosQueryRequestOptionsAccessor
        .disallowQueryPlanRetrieval(new CosmosQueryRequestOptions())

    private val readConfig = CosmosReadConfig.parseCosmosReadConfig(config)
    ThroughputControlHelper.populateThroughputControlGroupName(queryOptions, readConfig.throughputControlConfig)

    private val operationContext = {
        val taskContext = TaskContext.get
        assert(taskContext != null)

        SparkTaskContext(diagnosticsContext.correlationActivityId,
            taskContext.stageId(),
            taskContext.partitionId(),
            taskContext.taskAttemptId(),
            feedRange.toString + " " + aggregatedFilter.map(_.toString).reduce((left, right) => s"$left;$right"))
    }

    private val operationContextAndListenerTuple: Option[OperationContextAndListenerTuple] = {
        if (diagnosticsConfig.mode.isDefined) {
            val listener =
                DiagnosticsLoader.getDiagnosticsProvider(diagnosticsConfig).getLogger(this.getClass)

            val ctxAndListener = new OperationContextAndListenerTuple(operationContext, listener)

            ImplementationBridgeHelpers.CosmosQueryRequestOptionsHelper
                .getCosmosQueryRequestOptionsAccessor
                .setOperationContext(queryOptions, ctxAndListener)

            Some(ctxAndListener)
        } else {
            None
        }
    }

    log.logTrace(s"Instantiated ${this.getClass.getSimpleName}, Context: ${operationContext.toString} ${getThreadInfo}")

    private val containerTargetConfig = CosmosContainerConfig.parseCosmosContainerConfig(config)
    log.logInfo(s"Reading from feed range $feedRange of " +
        s"container ${containerTargetConfig.database}.${containerTargetConfig.container} - " +
        s"correlationActivityId ${diagnosticsContext.correlationActivityId}, " +
        s"readManyFilter: ${aggregatedFilter.map(_.toString).reduce((left, right) => s"$left;$right")}, " +
        s"Context: ${operationContext.toString} ${getThreadInfo}")

    private val clientCacheItem = CosmosClientCache(
        CosmosClientConfiguration(config, readConfig.forceEventualConsistency, sparkEnvironmentInfo),
        Some(cosmosClientStateHandles.value.cosmosClientMetadataCaches),
        s"ItemsPartitionReader($feedRange, ${containerTargetConfig.database}.${containerTargetConfig.container})"
    )

    private val throughputControlClientCacheItemOpt =
        ThroughputControlHelper.getThroughputControlClientCacheItem(
            config,
            clientCacheItem.context,
            Some(cosmosClientStateHandles),
            sparkEnvironmentInfo)

    private val cosmosAsyncContainer =
        ThroughputControlHelper.getContainer(
            config,
            containerTargetConfig,
            clientCacheItem,
            throughputControlClientCacheItemOpt)
    SparkUtils.safeOpenConnectionInitCaches(cosmosAsyncContainer, log)

    private val partitionKeyDefinition: Option[PartitionKeyDefinition] =
        if (diagnosticsConfig.mode.isDefined &&
            diagnosticsConfig.mode.get.equalsIgnoreCase(classOf[DetailedFeedDiagnosticsProvider].getName)) {

            Option.apply(cosmosAsyncContainer.read().block().getProperties.getPartitionKeyDefinition)
        } else {
            None
        }

    private val cosmosSerializationConfig = CosmosSerializationConfig.parseSerializationConfig(config)
    private val cosmosRowConverter = CosmosRowConverter.get(cosmosSerializationConfig)

    // TODO: figure out how to do this
    ImplementationBridgeHelpers
        .CosmosQueryRequestOptionsHelper
        .getCosmosQueryRequestOptionsAccessor
        .setItemFactoryMethod(
            queryOptions,
            jsonNode => {
                val objectNode = cosmosRowConverter.ensureObjectNode(jsonNode)
                val row = cosmosRowConverter.fromObjectNodeToRow(readSchema,
                    objectNode,
                    readConfig.schemaConversionMode)

                val pkValue = partitionKeyDefinition match {
                    case Some(pkDef) => Some(PartitionKeyHelper.getPartitionKeyPath(objectNode, pkDef))
                    case None => None
                }

                SparkRowItem(row, pkValue)
            })

    private lazy val iterator = new TransientIOErrorsRetryingIterator(
        continuationToken => {
            // there is no use for continuationToken
            queryOptions.setDedicatedGatewayRequestOptions(readConfig.dedicatedGatewayRequestOptions)

            ImplementationBridgeHelpers
                .CosmosQueryRequestOptionsHelper
                .getCosmosQueryRequestOptionsAccessor
                .setCorrelationActivityId(
                    queryOptions,
                    diagnosticsContext.correlationActivityId)

            val readManyFlux = Flux.fromIterable(aggregatedFilter.asJava)
                .buffer(readConfig.maxItemCount)
                .flatMap(itemIdentityList => cosmosAsyncContainer.readMany(itemIdentityList, queryOptions, classOf[SparkRowItem]))

            UtilBridgeInternal.createCosmosPagedFlux((cosmosPagedFluxOption) => readManyFlux)
        },
        readConfig.maxItemCount,
        readConfig.prefetchBufferSize,
        operationContextAndListenerTuple
    )

    private val rowSerializer: ExpressionEncoder.Serializer[Row] = RowSerializerPool.getOrCreateSerializer(readSchema)

    override def next(): Boolean = iterator.hasNext

    override def get(): InternalRow = {
        cosmosRowConverter.fromRowToInternalRow(iterator.next().row, rowSerializer)
    }

    override def close(): Unit = {
        this.iterator.close()
        RowSerializerPool.returnSerializerToPool(readSchema, rowSerializer)
        clientCacheItem.close()
        if (throughputControlClientCacheItemOpt.isDefined) {
            throughputControlClientCacheItemOpt.get.close()
        }
    }
}
