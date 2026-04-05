// Databricks notebook source
// MAGIC %md
// MAGIC **Throughput Bucket Sample**
// MAGIC
// MAGIC This sample demonstrates how to use server-side throughput bucket configuration with the Azure Cosmos DB Spark connector.
// MAGIC
// MAGIC Throughput buckets allow you to assign a fixed RU/s budget to a specific workload without requiring a separate
// MAGIC throughput control container. This is server-side throughput control — no global control database/container is needed.
// MAGIC
// MAGIC For full context, see: https://learn.microsoft.com/azure/cosmos-db/throughput-buckets?tabs=dotnet
// MAGIC
// MAGIC **Important:**
// MAGIC - `throughputBucket` must be a positive integer representing the RU/s budget.
// MAGIC - `throughputBucket` cannot be combined with SDK-based throughput control settings
// MAGIC   (`targetThroughput`, `targetThroughputThreshold`, `globalControl.database`, `globalControl.container`).
// MAGIC - `priorityLevel` (High or Low) can optionally be used with throughput bucket.

// COMMAND ----------

val cosmosEndpoint = "https://YOURACCOUNTNAME.documents.azure.com:443/"
val cosmosMasterKey = "YOUR_MASTER_KEY"
val cosmosDatabaseName = "SampleDatabase"
val cosmosContainerName = "SampleContainer"

// COMMAND ----------

// Configure Catalog API to be used
spark.conf.set("spark.sql.catalog.cosmosCatalog", "com.azure.cosmos.spark.CosmosCatalog")
spark.conf.set("spark.sql.catalog.cosmosCatalog.spark.cosmos.accountEndpoint", cosmosEndpoint)
spark.conf.set("spark.sql.catalog.cosmosCatalog.spark.cosmos.accountKey", cosmosMasterKey)

// COMMAND ----------

// MAGIC %md
// MAGIC **Create database and container**

// COMMAND ----------

// Create database using catalog API
spark.sql(s"CREATE DATABASE IF NOT EXISTS cosmosCatalog.${cosmosDatabaseName};")

// Create container using catalog API
spark.sql(s"CREATE TABLE IF NOT EXISTS cosmosCatalog.${cosmosDatabaseName}.${cosmosContainerName} using cosmos.oltp TBLPROPERTIES(partitionKeyPath = '/id', manualThroughput = '1000')")

// COMMAND ----------

// MAGIC %md
// MAGIC **Ingest sample data**

// COMMAND ----------

import org.apache.spark.sql.functions._

// Base config without throughput control
val cfgBase = Map(
  "spark.cosmos.accountEndpoint" -> cosmosEndpoint,
  "spark.cosmos.accountKey" -> cosmosMasterKey,
  "spark.cosmos.database" -> cosmosDatabaseName,
  "spark.cosmos.container" -> cosmosContainerName
)

val df = Seq(
  ("item1", "electronics", 10),
  ("item2", "books", 25),
  ("item3", "electronics", 5),
  ("item4", "clothing", 15),
  ("item5", "books", 30)
).toDF("id", "category", "quantity")

df.write.format("cosmos.oltp").mode("Append").options(cfgBase).save()

// COMMAND ----------

// MAGIC %md
// MAGIC **Read with throughput bucket**
// MAGIC
// MAGIC The following example reads from a Cosmos DB container while limiting the read workload
// MAGIC to 200 RU/s using a server-side throughput bucket.

// COMMAND ----------

val cfgReadWithThroughputBucket = Map(
  "spark.cosmos.accountEndpoint" -> cosmosEndpoint,
  "spark.cosmos.accountKey" -> cosmosMasterKey,
  "spark.cosmos.database" -> cosmosDatabaseName,
  "spark.cosmos.container" -> cosmosContainerName,
  "spark.cosmos.read.inferSchema.enabled" -> "true",
  "spark.cosmos.throughputControl.enabled" -> "true",
  "spark.cosmos.throughputControl.name" -> "ReadThroughputBucketGroup",
  "spark.cosmos.throughputControl.throughputBucket" -> "3"
)

val dfRead = spark.read.format("cosmos.oltp")
  .options(cfgReadWithThroughputBucket)
  .load()

dfRead.show()

// COMMAND ----------

// MAGIC %md
// MAGIC **Write with throughput bucket and priority level**
// MAGIC
// MAGIC This example writes data with a throughput bucket of 500 RU/s and Low priority level.
// MAGIC Priority-based execution is currently in preview.
// MAGIC See: https://devblogs.microsoft.com/cosmosdb/introducing-priority-based-execution-in-azure-cosmos-db-preview/

// COMMAND ----------

val cfgWriteWithThroughputBucket = Map(
  "spark.cosmos.accountEndpoint" -> cosmosEndpoint,
  "spark.cosmos.accountKey" -> cosmosMasterKey,
  "spark.cosmos.database" -> cosmosDatabaseName,
  "spark.cosmos.container" -> cosmosContainerName,
  "spark.cosmos.write.strategy" -> "ItemOverwrite",
  "spark.cosmos.throughputControl.enabled" -> "true",
  "spark.cosmos.throughputControl.name" -> "WriteThroughputBucketGroup",
  "spark.cosmos.throughputControl.throughputBucket" -> "1",
  "spark.cosmos.throughputControl.priorityLevel" -> "Low"
)

val dfNewData = Seq(
  ("item6", "furniture", 3),
  ("item7", "electronics", 42)
).toDF("id", "category", "quantity")

dfNewData.write.format("cosmos.oltp").mode("Append").options(cfgWriteWithThroughputBucket).save()

println("Write with throughput bucket completed.")

// COMMAND ----------

// MAGIC %md
// MAGIC **Bulk write with throughput bucket**
// MAGIC
// MAGIC This example uses bulk ingestion with a throughput bucket of 800 RU/s.
// MAGIC Bulk mode is recommended for high-volume writes.

// COMMAND ----------

val cfgBulkWriteWithThroughputBucket = Map(
  "spark.cosmos.accountEndpoint" -> cosmosEndpoint,
  "spark.cosmos.accountKey" -> cosmosMasterKey,
  "spark.cosmos.database" -> cosmosDatabaseName,
  "spark.cosmos.container" -> cosmosContainerName,
  "spark.cosmos.write.strategy" -> "ItemOverwrite",
  "spark.cosmos.write.bulk.enabled" -> "true",
  "spark.cosmos.throughputControl.enabled" -> "true",
  "spark.cosmos.throughputControl.name" -> "BulkWriteThroughputBucketGroup",
  "spark.cosmos.throughputControl.throughputBucket" -> "2"
)

val dfBulkData = (1 to 100).map(i => (s"bulk-item-$i", "bulk-category", i)).toSeq
  .toDF("id", "category", "quantity")

dfBulkData.write.format("cosmos.oltp").mode("Append").options(cfgBulkWriteWithThroughputBucket).save()

println("Bulk write with throughput bucket completed.")

// COMMAND ----------

// MAGIC %md
// MAGIC **Query with throughput bucket**
// MAGIC
// MAGIC Use a throughput bucket to limit query workloads on shared containers.

// COMMAND ----------

val cfgQueryWithThroughputBucket = Map(
  "spark.cosmos.accountEndpoint" -> cosmosEndpoint,
  "spark.cosmos.accountKey" -> cosmosMasterKey,
  "spark.cosmos.database" -> cosmosDatabaseName,
  "spark.cosmos.container" -> cosmosContainerName,
  "spark.cosmos.read.inferSchema.enabled" -> "true",
  "spark.cosmos.read.customQuery" -> "SELECT c.id, c.category, c.quantity FROM c WHERE c.category = 'electronics'",
  "spark.cosmos.throughputControl.enabled" -> "true",
  "spark.cosmos.throughputControl.name" -> "QueryThroughputBucketGroup",
  "spark.cosmos.throughputControl.throughputBucket" -> "2"
)

val dfQuery = spark.read.format("cosmos.oltp")
  .options(cfgQueryWithThroughputBucket)
  .load()

dfQuery.show()
