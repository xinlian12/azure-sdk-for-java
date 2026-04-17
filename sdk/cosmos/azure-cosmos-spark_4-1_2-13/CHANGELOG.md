## Release History

### 4.47.0 (2026-04-17)

#### Features Added
* Added support for Apache Spark 4.1 with package reorganization handling (SPARK-52787). - See [PR #48849](https://github.com/Azure/azure-sdk-for-java/pull/48849)
* Added support for change feed with `startFrom` point-in-time on merged partitions by enabling the `CHANGE_FEED_WITH_START_TIME_POST_MERGE` SDK capability in the azure-cosmos SDK. - See [PR 48752](https://github.com/Azure/azure-sdk-for-java/pull/48752)

#### Bugs Fixed
* Fixed `NoClassDefFoundError` for `MetadataVersionUtil` when using change feed Spark Structured Streaming on Databricks Runtime 17.3+ by inlining the version validation logic. - See [PR 48837](https://github.com/Azure/azure-sdk-for-java/pull/48837)
* Fixed JVM `<clinit>` deadlock when multiple threads concurrently trigger Cosmos SDK class loading for the first time. - See [PR 48689](https://github.com/Azure/azure-sdk-for-java/pull/48689)
* Handled package reorganization in Apache Spark 4.1 where HDFSMetadataLog and MetadataVersionUtil moved from `org.apache.spark.sql.execution.streaming` to `org.apache.spark.sql.execution.streaming.checkpointing`.

#### Other Changes
* Initial release of Spark 4.1 connector with Scala 2.13 support based on Spark 4.0 connector
