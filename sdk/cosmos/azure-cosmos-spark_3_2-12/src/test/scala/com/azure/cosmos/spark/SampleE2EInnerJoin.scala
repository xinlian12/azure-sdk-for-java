package com.azure.cosmos.spark

import com.azure.cosmos.{ConsistencyLevel, CosmosClientBuilder}
import com.azure.cosmos.implementation.TestConfigurations
import org.apache.spark.sql.SparkSession

object SampleE2EInnerJoin {
    def main(args: Array[String]) {
        val cosmosEndpoint = TestConfigurations.HOST
        val cosmosMasterKey = TestConfigurations.MASTER_KEY
        val cosmosDatabase = "SampleDatabase"
        val cosmosContainer = "GreenTaxiRecords"

        val client = new CosmosClientBuilder()
            .endpoint(cosmosEndpoint)
            .key(cosmosMasterKey)
            .consistencyLevel(ConsistencyLevel.EVENTUAL)
            .buildAsyncClient()

        client.createDatabaseIfNotExists(cosmosDatabase).block()
        client.getDatabase(cosmosDatabase).createContainerIfNotExists(cosmosContainer, "/id").block()
        client.close()

        val cfg = Map("spark.cosmos.accountEndpoint" -> cosmosEndpoint,
            "spark.cosmos.accountKey" -> cosmosMasterKey,
            "spark.cosmos.database" -> cosmosDatabase,
            "spark.cosmos.container" -> cosmosContainer
        )

        val spark = SparkSession.builder()
            .appName("spark connector sample")
            .master("local")
            .withExtensions(new CosmosSparkExtensions())
            .getOrCreate()

//        spark.conf.set("spark.sql.adaptive.enabled", true)
//        spark.conf.set("spark.sql.optimizer.dynamicPartitionPruning.enabled", true)

        LocalJavaFileSystem.applyToSparkSession(spark)

        // scalastyle:off underscore.import
        // scalastyle:off import.grouping
        import spark.implicits._
        // scalastyle:on underscore.import
        // scalastyle:on import.grouping

        val df = spark.read.format("cosmos.oltp").options(cfg).load()
        val innerJoinDf = Seq(
            ("9cd25c3a-0cfe-4808-b1e6-641aa948988b"),
            ("ab2732df-3753-440c-946f-9e3811ddc51e")
        ).toDF("id")

        val results = df.join(innerJoinDf).where(df("id") === innerJoinDf("id")).collect()
        //    println(results.length)
        //    println(results)

        spark.close()
    }

}
