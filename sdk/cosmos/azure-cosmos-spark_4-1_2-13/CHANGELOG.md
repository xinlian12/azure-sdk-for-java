## Release History

### 4.47.0 (2026-04-17)

#### Features Added
* Added support for Apache Spark 4.1 with package reorganization handling (SPARK-52787). - See [PR #48849](https://github.com/Azure/azure-sdk-for-java/pull/48849)
* Handled package reorganization in Apache Spark 4.1 where HDFSMetadataLog and MetadataVersionUtil moved from `org.apache.spark.sql.execution.streaming` to `org.apache.spark.sql.execution.streaming.checkpointing`.

#### Other Changes
* Initial release of Spark 4.1 connector with Scala 2.13 support based on Spark 4.0 connector
* Includes all features and fixes from azure-cosmos-spark_4-0_2-13 v4.47.0, including:
  - Support for change feed with `startFrom` point-in-time on merged partitions
  - Fix for `NoClassDefFoundError` for `MetadataVersionUtil` when using change feed on Databricks Runtime 17.3+  
  - Fix for JVM `<clinit>` deadlock during concurrent Cosmos SDK class loading
