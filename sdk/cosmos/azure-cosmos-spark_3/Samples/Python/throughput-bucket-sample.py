# Databricks notebook source
# MAGIC %md
# MAGIC **Throughput Bucket Sample**
# MAGIC
# MAGIC This sample demonstrates how to use server-side throughput bucket configuration with the Azure Cosmos DB Spark connector.
# MAGIC
# MAGIC Throughput buckets allow you to assign a fixed RU/s budget to a specific workload without requiring a separate
# MAGIC throughput control container. This is server-side throughput control — no global control database/container is needed.
# MAGIC
# MAGIC For full context, see: https://learn.microsoft.com/azure/cosmos-db/throughput-buckets?tabs=dotnet
# MAGIC
# MAGIC **Important:**
# MAGIC - `throughputBucket` must be a positive integer representing the RU/s budget.
# MAGIC - `throughputBucket` cannot be combined with SDK-based throughput control settings
# MAGIC   (`targetThroughput`, `targetThroughputThreshold`, `globalControl.database`, `globalControl.container`).
# MAGIC - `priorityLevel` (High or Low) can optionally be used with throughput bucket.

# COMMAND ----------

cosmosEndpoint = "https://YOURACCOUNTNAME.documents.azure.com:443/"
cosmosMasterKey = "YOUR_MASTER_KEY"
cosmosDatabaseName = "SampleDatabase"
cosmosContainerName = "SampleContainer"

# COMMAND ----------

# Configure Catalog API to be used
spark.conf.set("spark.sql.catalog.cosmosCatalog", "com.azure.cosmos.spark.CosmosCatalog")
spark.conf.set("spark.sql.catalog.cosmosCatalog.spark.cosmos.accountEndpoint", cosmosEndpoint)
spark.conf.set("spark.sql.catalog.cosmosCatalog.spark.cosmos.accountKey", cosmosMasterKey)

# COMMAND ----------

# MAGIC %md
# MAGIC **Create database and container**

# COMMAND ----------

# Create database using catalog API
spark.sql("CREATE DATABASE IF NOT EXISTS cosmosCatalog.{};".format(cosmosDatabaseName))

# Create container using catalog API
spark.sql(
    "CREATE TABLE IF NOT EXISTS cosmosCatalog.{}.{} using cosmos.oltp TBLPROPERTIES(partitionKeyPath = '/id', manualThroughput = '1000')".format(
        cosmosDatabaseName, cosmosContainerName
    )
)

# COMMAND ----------

# MAGIC %md
# MAGIC **Ingest sample data**

# COMMAND ----------

# Base config without throughput control
cfgBase = {
    "spark.cosmos.accountEndpoint": cosmosEndpoint,
    "spark.cosmos.accountKey": cosmosMasterKey,
    "spark.cosmos.database": cosmosDatabaseName,
    "spark.cosmos.container": cosmosContainerName,
}

columns = ["id", "category", "quantity"]
data = [
    ("item1", "electronics", 10),
    ("item2", "books", 25),
    ("item3", "electronics", 5),
    ("item4", "clothing", 15),
    ("item5", "books", 30),
]

df = spark.createDataFrame(data, columns)
df.write.format("cosmos.oltp").mode("Append").options(**cfgBase).save()

# COMMAND ----------

# MAGIC %md
# MAGIC **Read with throughput bucket**
# MAGIC
# MAGIC The following example reads from a Cosmos DB container while limiting the read workload
# MAGIC to 200 RU/s using a server-side throughput bucket.

# COMMAND ----------

cfgReadWithThroughputBucket = {
    "spark.cosmos.accountEndpoint": cosmosEndpoint,
    "spark.cosmos.accountKey": cosmosMasterKey,
    "spark.cosmos.database": cosmosDatabaseName,
    "spark.cosmos.container": cosmosContainerName,
    "spark.cosmos.read.inferSchema.enabled": "true",
    "spark.cosmos.throughputControl.enabled": "true",
    "spark.cosmos.throughputControl.name": "ReadThroughputBucketGroup",
    "spark.cosmos.throughputControl.throughputBucket": "1",
}

dfRead = (
    spark.read.format("cosmos.oltp")
    .options(**cfgReadWithThroughputBucket)
    .load()
)

dfRead.show()

# COMMAND ----------

# MAGIC %md
# MAGIC **Write with throughput bucket and priority level**
# MAGIC
# MAGIC This example writes data with a throughput bucket of 500 RU/s and Low priority level.
# MAGIC Priority-based execution is currently in preview.
# MAGIC See: https://devblogs.microsoft.com/cosmosdb/introducing-priority-based-execution-in-azure-cosmos-db-preview/

# COMMAND ----------

cfgWriteWithThroughputBucket = {
    "spark.cosmos.accountEndpoint": cosmosEndpoint,
    "spark.cosmos.accountKey": cosmosMasterKey,
    "spark.cosmos.database": cosmosDatabaseName,
    "spark.cosmos.container": cosmosContainerName,
    "spark.cosmos.write.strategy": "ItemOverwrite",
    "spark.cosmos.throughputControl.enabled": "true",
    "spark.cosmos.throughputControl.name": "WriteThroughputBucketGroup",
    "spark.cosmos.throughputControl.throughputBucket": "2",
    "spark.cosmos.throughputControl.priorityLevel": "Low",
}

newData = [
    ("item6", "furniture", 3),
    ("item7", "electronics", 42),
]

dfNewData = spark.createDataFrame(newData, columns)
dfNewData.write.format("cosmos.oltp").mode("Append").options(**cfgWriteWithThroughputBucket).save()

print("Write with throughput bucket completed.")

# COMMAND ----------

# MAGIC %md
# MAGIC **Bulk write with throughput bucket**
# MAGIC
# MAGIC This example uses bulk ingestion with a throughput bucket of 800 RU/s.
# MAGIC Bulk mode is recommended for high-volume writes.

# COMMAND ----------

cfgBulkWriteWithThroughputBucket = {
    "spark.cosmos.accountEndpoint": cosmosEndpoint,
    "spark.cosmos.accountKey": cosmosMasterKey,
    "spark.cosmos.database": cosmosDatabaseName,
    "spark.cosmos.container": cosmosContainerName,
    "spark.cosmos.write.strategy": "ItemOverwrite",
    "spark.cosmos.write.bulk.enabled": "true",
    "spark.cosmos.throughputControl.enabled": "true",
    "spark.cosmos.throughputControl.name": "BulkWriteThroughputBucketGroup",
    "spark.cosmos.throughputControl.throughputBucket": "2",
}

bulkData = [("bulk-item-{}".format(i), "bulk-category", i) for i in range(1, 101)]

dfBulkData = spark.createDataFrame(bulkData, columns)
dfBulkData.write.format("cosmos.oltp").mode("Append").options(**cfgBulkWriteWithThroughputBucket).save()

print("Bulk write with throughput bucket completed.")

# COMMAND ----------

# MAGIC %md
# MAGIC **Query with throughput bucket**
# MAGIC
# MAGIC Use a throughput bucket to limit query workloads on shared containers.

# COMMAND ----------

cfgQueryWithThroughputBucket = {
    "spark.cosmos.accountEndpoint": cosmosEndpoint,
    "spark.cosmos.accountKey": cosmosMasterKey,
    "spark.cosmos.database": cosmosDatabaseName,
    "spark.cosmos.container": cosmosContainerName,
    "spark.cosmos.read.inferSchema.enabled": "true",
    "spark.cosmos.read.customQuery": "SELECT c.id, c.category, c.quantity FROM c WHERE c.category = 'electronics'",
    "spark.cosmos.throughputControl.enabled": "true",
    "spark.cosmos.throughputControl.name": "QueryThroughputBucketGroup",
    "spark.cosmos.throughputControl.throughputBucket": "3",
}

dfQuery = (
    spark.read.format("cosmos.oltp")
    .options(**cfgQueryWithThroughputBucket)
    .load()
)

dfQuery.show()
