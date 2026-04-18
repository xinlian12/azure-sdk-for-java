## Release History

### 4.47.0 (2026-04-17)

#### Features Added
* Added Spark 4.1 support with updated HDFSMetadataLog import path for SPARK-52787 package reorganization. - See [PR 48861](https://github.com/Azure/azure-sdk-for-java/pull/48861)

#### Bugs Fixed
* Fixed `NoClassDefFoundError` for `MetadataVersionUtil` when using change feed Spark Structured Streaming on Databricks Runtime 17.3+ by inlining the version validation logic. - See [PR 48837](https://github.com/Azure/azure-sdk-for-java/pull/48837)
* Fixed JVM `<clinit>` deadlock when multiple threads concurrently trigger Cosmos SDK class loading for the first time. - See [PR 48689](https://github.com/Azure/azure-sdk-for-java/pull/48689)
