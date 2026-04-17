// Databricks notebook source
// val cosmosEndpoint = "<inserted by environment>"
// val subscriptionId = "<inserted by environment>"
// val tenantId = "<inserted by environment>"
// val resourceGroupName = "<inserted by environment>"

println("SCENARIO: basicScenarioAadManagedIdentity")

val authType = "ManagedIdentity"
val cosmosEndpoint = dbutils.widgets.get("cosmosEndpointMsi")
val subscriptionId = dbutils.widgets.get("subscriptionId")
val tenantId = dbutils.widgets.get("tenantId")
val resourceGroupName = dbutils.widgets.get("resourceGroupName")
val cosmosContainerName = dbutils.widgets.get("cosmosContainerName")
val cosmosDatabaseName = dbutils.widgets.get("cosmosDatabaseName")

val cfg = Map("spark.cosmos.accountEndpoint" -> cosmosEndpoint,
    "spark.cosmos.auth.type" -> authType,
    "spark.cosmos.account.subscriptionId" -> subscriptionId,
    "spark.cosmos.account.tenantId" -> tenantId,
    "spark.cosmos.account.resourceGroupName" -> resourceGroupName,
    "spark.cosmos.database" -> cosmosDatabaseName,
    "spark.cosmos.container" -> cosmosContainerName,
    "spark.cosmos.enforceNativeTransport" -> "true",
    "spark.cosmos.read.consistencyStrategy" -> "LatestCommitted",
)

val cfgWithAutoSchemaInference = Map("spark.cosmos.accountEndpoint" -> cosmosEndpoint,
    "spark.cosmos.auth.type" -> authType,
    "spark.cosmos.account.subscriptionId" -> subscriptionId,
    "spark.cosmos.account.tenantId" -> tenantId,
    "spark.cosmos.account.resourceGroupName" -> resourceGroupName,
    "spark.cosmos.database" -> cosmosDatabaseName,
    "spark.cosmos.container" -> cosmosContainerName,
    "spark.cosmos.read.inferSchema.enabled" -> "true",
    "spark.cosmos.enforceNativeTransport" -> "true",
    "spark.cosmos.read.consistencyStrategy" -> "LatestCommitted",
)

// COMMAND ----------

// create Cosmos Database and Cosmos Container using Catalog APIs
spark.conf.set(s"spark.sql.catalog.cosmosCatalogMI", "com.azure.cosmos.spark.CosmosCatalog")
spark.conf.set(s"spark.sql.catalog.cosmosCatalogMI.spark.cosmos.accountEndpoint", cosmosEndpoint)
spark.conf.set(s"spark.sql.catalog.cosmosCatalogMI.spark.cosmos.auth.type", authType)
spark.conf.set(s"spark.sql.catalog.cosmosCatalogMI.spark.cosmos.account.subscriptionId", subscriptionId)
spark.conf.set(s"spark.sql.catalog.cosmosCatalogMI.spark.cosmos.account.tenantId", tenantId)
spark.conf.set(s"spark.sql.catalog.cosmosCatalogMI.spark.cosmos.account.resourceGroupName", resourceGroupName)

// create a cosmos database
spark.sql(s"CREATE DATABASE IF NOT EXISTS cosmosCatalogMI.${cosmosDatabaseName};")

// create a cosmos container
spark.sql(s"CREATE TABLE IF NOT EXISTS cosmosCatalogMI.${cosmosDatabaseName}.${cosmosContainerName} using cosmos.oltp " +
    s"TBLPROPERTIES(partitionKeyPath = '/id', manualThroughput = '400')")

// update the throughput
spark.sql(s"ALTER TABLE cosmosCatalogMI.${cosmosDatabaseName}.${cosmosContainerName} " +
  s"SET TBLPROPERTIES('manualThroughput' = '1100')")

// COMMAND ----------

// ingestion
spark.createDataFrame(Seq(("cat-alive", "Schrodinger cat", 2, true), ("cat-dead", "Schrodinger cat", 2, false)))
    .toDF("id","name","age","isAlive")
    .write
    .format("cosmos.oltp")
    .options(cfg)
    .mode("APPEND")
    .save()

// COMMAND ----------

// Show the schema of the table and data without auto schema inference
val df = spark.read.format("cosmos.oltp").options(cfg).load()
df.printSchema()

df.show()

// COMMAND ----------

// Show the schema of the table and data with auto schema inference
val df = spark.read.format("cosmos.oltp").options(cfgWithAutoSchemaInference).load()
df.printSchema()

df.show()

// COMMAND ----------

import org.apache.spark.sql.functions.col

// Query to find the live cat and increment age of the alive cat
df.filter(col("isAlive") === true)
    .withColumn("age", col("age") + 1)
    .show()

// COMMAND ----------

// Change Feed - micro-batch structured streaming
// This exercises the ChangeFeedInitialOffsetWriter and HDFSMetadataLog code paths
// that can break on certain Spark distributions (e.g. Databricks Runtime 17.3+)

val changeFeedCfg = cfg ++ Map(
  "spark.cosmos.read.inferSchema.enabled" -> "false",
  "spark.cosmos.changeFeed.startFrom" -> "Beginning",
  "spark.cosmos.changeFeed.mode" -> "Incremental"
)

val testId = java.util.UUID.randomUUID().toString.replace("-", "")

println(s"Change Feed test: using endpoint=${cosmosEndpoint}")
println(s"Change Feed test: database=${cosmosDatabaseName}, container=${cosmosContainerName}")
println(s"Change Feed test: authType=${authType}")
println(s"Change Feed test: changeFeedCfg keys=${changeFeedCfg.keys.mkString(", ")}")

// Verify the source container is accessible before starting streaming
val verifyDf = spark.read.format("cosmos.oltp").options(cfg).load()
val verifyCount = verifyDf.count()
println(s"Change Feed test: source container has $verifyCount records before streaming")

try {
  val changeFeedDF = spark
    .readStream
    .format("cosmos.oltp.changeFeed")
    .options(changeFeedCfg)
    .load()

  val microBatchQuery = changeFeedDF
    .writeStream
    .format("memory")
    .queryName(testId)
    .outputMode("append")
    .start()

  println(s"Change Feed test: streaming query started, id=${microBatchQuery.id}")
  microBatchQuery.processAllAvailable()
  microBatchQuery.stop()

  val sinkCount = spark.sql(s"SELECT * FROM $testId").count()
  println(s"Change Feed micro-batch streaming: $sinkCount records read via change feed")
  assert(sinkCount >= 2, s"Expected at least 2 records from change feed but found $sinkCount")
} catch {
  case e: Exception =>
    println(s"Change Feed test FAILED: ${e.getClass.getName}: ${e.getMessage}")
    if (e.getCause != null) {
      println(s"Change Feed test cause: ${e.getCause.getClass.getName}: ${e.getCause.getMessage}")
    }
    throw e
}

// COMMAND ----------

// cleanup
spark.sql(s"DROP TABLE cosmosCatalogMI.${cosmosDatabaseName}.${cosmosContainerName};")
