// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.azure.cosmos.spark

import com.azure.cosmos.implementation.SparkBridgeImplementationInternal
import com.azure.cosmos.models.{CosmosContainerRequestOptions, CosmosItemIdentity, CosmosParameterizedQuery, FeedRange, PartitionKey, SqlParameter, SqlQuerySpec}
import com.azure.cosmos.spark.CosmosPredicates.requireNotNull
import com.azure.cosmos.spark.diagnostics.{DiagnosticsContext, LoggerHelper}
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.connector.expressions.{Expressions, NamedReference}
import org.apache.spark.sql.connector.read.streaming.ReadLimit
import org.apache.spark.sql.connector.read.{Batch, InputPartition, PartitionReaderFactory, Statistics, SupportsReportStatistics, SupportsRuntimeFiltering}
import org.apache.spark.sql.sources.{Filter, In}
import org.apache.spark.sql.types.{DataTypes, StructType}

import java.util.concurrent.atomic.AtomicReference
import java.util.{OptionalLong, UUID}
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

private case class ItemsScan(session: SparkSession,
                             schema: StructType,
                             config: Map[String, String],
                             readConfig: CosmosReadConfig,
                             cosmosQuery: CosmosParameterizedQuery,
                             cosmosClientStateHandles: Broadcast[CosmosClientMetadataCachesSnapshots],
                             diagnosticsConfig: DiagnosticsConfig,
                             sparkEnvironmentInfo: String)
  extends SupportsRuntimeFiltering
    with SupportsReportStatistics
    with Batch {

  requireNotNull(cosmosQuery, "cosmosQuery")

  @transient private lazy val log = LoggerHelper.getLogger(diagnosticsConfig, this.getClass)
  log.logTrace(s"Instantiated ${this.getClass.getSimpleName}")

  private val clientConfiguration = CosmosClientConfiguration.apply(
    config,
    readConfig.forceEventualConsistency,
    CosmosClientConfiguration.getSparkEnvironmentInfo(Some(session))
  )
  private val containerConfig = CosmosContainerConfig.parseCosmosContainerConfig(config)
  private val partitioningConfig = CosmosPartitioningConfig.parseCosmosPartitioningConfig(config)
  private val defaultMinPartitionCount = 1 + (2 * session.sparkContext.defaultParallelism)
  private val partitionFilterMapReference = new AtomicReference[Map[NormalizedRange, ListBuffer[CosmosItemIdentity]]]()

  override def description(): String = {
    s"""Cosmos ItemsScan: ${containerConfig.database}.${containerConfig.container}
       | - Cosmos Query: ${toPrettyString(cosmosQuery.toSqlQuerySpec)}""".stripMargin
  }

  private[this] def toPrettyString(query: SqlQuerySpec) = {
    //scalastyle:off magic.number
    val sb = new StringBuilder()
    //scalastyle:on magic.number
    sb.append(query.getQueryText)
    query.getParameters.forEach(
      (p: SqlParameter) => sb
        .append(CosmosConstants.SystemProperties.LineSeparator)
        .append(" > param: ")
        .append(p.getName)
        .append(" = ")
        .append(p.getValue(classOf[Any])))

    sb.toString
  }

  /**
   * Returns the actual schema of this data source scan, which may be different from the physical
   * schema of the underlying storage, as column pruning or other optimizations may happen.
   */
  override def readSchema(): StructType = {
      schema
  }

  //scalastyle:off
  override def planInputPartitions(): Array[InputPartition] = {
    System.out.println("Replanning partitions")
    val partitionMetadata = CosmosPartitionPlanner.getFilteredPartitionMetadata(
      config,
      clientConfiguration,
      Some(cosmosClientStateHandles),
      containerConfig,
      partitioningConfig,
      false
    )

    val calledFrom = s"ItemsScan($description()).planInputPartitions"
    Loan(
      List[Option[CosmosClientCacheItem]](
        Some(CosmosClientCache.apply(
          clientConfiguration,
          Some(cosmosClientStateHandles.value.cosmosClientMetadataCaches),
          calledFrom
        )),
        ThroughputControlHelper.getThroughputControlClientCacheItem(
          config, calledFrom, Some(cosmosClientStateHandles), sparkEnvironmentInfo)
      ))
      .to(clientCacheItems => {
        val container =
          ThroughputControlHelper.getContainer(
            config,
            containerConfig,
            clientCacheItems(0).get,
            clientCacheItems(1))
        SparkUtils.safeOpenConnectionInitCaches(container, log)

        val plannedInputPartitions = CosmosPartitionPlanner
          .createInputPartitions(
            partitioningConfig,
            container,
            partitionMetadata,
            defaultMinPartitionCount,
            CosmosPartitionPlanner.DefaultPartitionSizeInMB,
            ReadLimit.allAvailable(),
            false
          )

        // Filter down the partitions if there is any runtime filters
        if (partitionFilterMapReference.get() == null) {
            // cannot prune any partitions, return the original one
            plannedInputPartitions.map(_.asInstanceOf[InputPartition])
        } else {
            // remove the partitions does not overlap with the filter map
            val prunedPartitions = plannedInputPartitions.filter(inputPartition => {
                partitionFilterMapReference.get().keys.filter(filterRange => {
                    SparkBridgeImplementationInternal.doRangesOverlap(
                        filterRange,
                        inputPartition.feedRange)
                })
                    .toList
                    .nonEmpty
            })

            prunedPartitions.map(_.asInstanceOf[InputPartition])
        }
      })
  }

  override def createReaderFactory(): PartitionReaderFactory = {
    val correlationActivityId = UUID.randomUUID()
      System.out.println("Already creating readerFactory")
    log.logInfo(s"Creating ItemsScan with CorrelationActivityId '${correlationActivityId.toString}' for query '${cosmosQuery.queryText}'")
    ItemsScanPartitionReaderFactory(config,
      schema,
      cosmosQuery,
      DiagnosticsContext(correlationActivityId, cosmosQuery.queryText),
      cosmosClientStateHandles,
      DiagnosticsConfig.parseDiagnosticsConfig(config),
      sparkEnvironmentInfo,
      partitionFilterMapReference
    )
  }

  override def toBatch: Batch = {
    this
  }

    override def filterAttributes(): Array[NamedReference] = {
        // getting the partition key information
        val filterAttributes = new ArrayBuffer[NamedReference]()
        filterAttributes += Expressions.column("id")

        // TODO: check whether this part can be skipped
        val calledFrom = s"ItemsScan($description()).filterAttributes"
        Loan(
            List[Option[CosmosClientCacheItem]](
                Some(CosmosClientCache.apply(
                    clientConfiguration,
                    Some(cosmosClientStateHandles.value.cosmosClientMetadataCaches),
                    calledFrom
                )),
                ThroughputControlHelper.getThroughputControlClientCacheItem(
                    config, calledFrom, Some(cosmosClientStateHandles), sparkEnvironmentInfo)
            ))
            .to(clientCacheItems => {
                val container =
                    ThroughputControlHelper.getContainer(
                        config,
                        containerConfig,
                        clientCacheItems(0).get,
                        clientCacheItems(1))
                SparkUtils.safeOpenConnectionInitCaches(container, log)

                val cosmosContainerResponse = container.read().block()
                cosmosContainerResponse
                    .getProperties
                    .getPartitionKeyDefinition()
                    .getPaths
                    .forEach(path => {
                        if (!path.stripPrefix("/").equals("id")) {
                            filterAttributes += Expressions.column(path.stripPrefix("/"))
                        }
                    })
            })

        System.out.println("This is soooo great, filterAttributes is being called")
        filterAttributes.toArray
    }

    //scalastyle:off
    override def filter(filters: Array[Filter]): Unit = {
        // this method is called for runtime filters
        // for now, we only care about partition dynamic pruning filters which is a IN filter
        // but other optimizations can be achieved as well
        // Scenario 1: Only PK values are provided, we could potentially just readAllItems by pk or using query IN
        // Scenario 2: Only id values are provided, we could potentially using query IN
        // Scenario 3: PK and Id can both be provided, but for this API, it will return two filters, one for PK
        // One for id, so in order to utilize the readMany join, we will have to request the customer to construct a new filter attribute
        // which can be the combination of id and pk. And also this new
        //
        // For POC, using id=pk to start with
        val filterAttributes = "id"
        val partitionFilterMap = collection.mutable.Map[CosmosInputPartition, ListBuffer[CosmosItemIdentity]]()
        val cosmosItemIdentities = new ListBuffer[CosmosItemIdentity]
        // TODO: Should we only care about the first matching filter
        // TODO: should we only allow it if the original customized query is empty?
        filters.foreach(filter => {
            filter match {
                case In(attribute, values) => {
                    if (attribute.equals(filterAttributes)) {
                        // get all the values and converted into readMany
                        values.foreach(value => {
                            cosmosItemIdentities += new CosmosItemIdentity(new PartitionKey(value.toString), value.toString)
                        })
                    }
                }
            }
        })

        // decide which cosmosInputPartition has the filters
        val calledFrom = s"ItemsScan($description()).filter"
        val partitionKeyDefinition =
            Loan(
                List[Option[CosmosClientCacheItem]](
                    Some(CosmosClientCache.apply(
                        clientConfiguration,
                        Some(cosmosClientStateHandles.value.cosmosClientMetadataCaches),
                        calledFrom
                    )),
                    ThroughputControlHelper.getThroughputControlClientCacheItem(
                        config, calledFrom, Some(cosmosClientStateHandles), sparkEnvironmentInfo)
                ))
                .to(clientCacheItems => {
                    val container =
                        ThroughputControlHelper.getContainer(
                            config,
                            containerConfig,
                            clientCacheItems(0).get,
                            clientCacheItems(1))
                    SparkUtils.safeOpenConnectionInitCaches(container, log)

                    container.read().block().getProperties.getPartitionKeyDefinition()
                })

        val plannedPartitions = this.planInputPartitions()

        cosmosItemIdentities.foreach(cosmosItemIdentity => {
            val feedRange = SparkBridgeImplementationInternal.partitionKeyToNormalizedRange(cosmosItemIdentity.getPartitionKey, partitionKeyDefinition)
            val overlapPlannedPartitions =
                plannedPartitions
                    .filter(inputPartition => {
                        SparkBridgeImplementationInternal.doRangesOverlap(
                            feedRange,
                            inputPartition.asInstanceOf[CosmosInputPartition].feedRange)
                    }).toList

            overlapPlannedPartitions.foreach(overlapPartition => {
                partitionFilterMap.get(overlapPartition.asInstanceOf[CosmosInputPartition]) match {
                    case Some(identityFilterList) => identityFilterList += cosmosItemIdentity
                    case None => {
                        val filterList = new ListBuffer[CosmosItemIdentity]
                        filterList += cosmosItemIdentity
                        partitionFilterMap.put(overlapPartition.asInstanceOf[CosmosInputPartition], filterList)
                    }
                }
            })
        })

        // TODO: should not happen, but in case the item does not fall into any planned cosmosInputPartition


        System.out.println("oh my gosh filter method is being called")
    }

    override def estimateStatistics(): Statistics = {
        // TODO: change into more accurate calculation
        val calledFrom = s"ItemsScan($description()).estimateStatistics"
        Loan(
            List[Option[CosmosClientCacheItem]](
                Some(CosmosClientCache.apply(
                    clientConfiguration,
                    Some(cosmosClientStateHandles.value.cosmosClientMetadataCaches),
                    calledFrom
                )),
                ThroughputControlHelper.getThroughputControlClientCacheItem(
                    config, calledFrom, Some(cosmosClientStateHandles), sparkEnvironmentInfo)
            ))
            .to(clientCacheItems => {
                val container =
                    ThroughputControlHelper.getContainer(
                        config,
                        containerConfig,
                        clientCacheItems(0).get,
                        clientCacheItems(1))
                SparkUtils.safeOpenConnectionInitCaches(container, log)

                val requestOptions = new CosmosContainerRequestOptions()
                requestOptions.setQuotaInfoEnabled(true)
                val cosmosContainerResponse = container.read(requestOptions).block()
                val currentResourceUsage = cosmosContainerResponse.getCurrentResourceQuotaUsage
                val documentsCountArray =
                    currentResourceUsage
                        .split(";")
                        .filter(component => component.startsWith("documentsCount"))
                        .toArray
                assert(documentsCountArray.length == 1)
                val docCount = documentsCountArray(0).split("=")(1).toLong
                // TODO: getting the size of each item properly
                SparkCosmosStatistics(1024 * docCount, docCount)
            })
    }

    case class SparkCosmosStatistics(sizeInByte: Long, rowCount: Long) extends Statistics{
        override def sizeInBytes(): OptionalLong = OptionalLong.of(sizeInByte)

        override def numRows(): OptionalLong = OptionalLong.of(rowCount)
    }
}
