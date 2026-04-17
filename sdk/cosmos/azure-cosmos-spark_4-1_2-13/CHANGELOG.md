## Release History

### 4.47.0 (Unreleased)

#### Features Added
* Added support for Apache Spark 4.1 with package reorganization handling (SPARK-52787). - See [PR #48849](https://github.com/Azure/azure-sdk-for-java/pull/48849)
* Handled package reorganization in Apache Spark 4.1 where HDFSMetadataLog and MetadataVersionUtil moved from `org.apache.spark.sql.execution.streaming` to `org.apache.spark.sql.execution.streaming.checkpointing`.

#### Other Changes
* Initial release, sharing the common Spark connector codebase from azure-cosmos-spark_3
