## Release History

### 4.48.0-beta.1 (Unreleased)

#### Features Added

#### Breaking Changes

#### Bugs Fixed

#### Other Changes

### 4.47.0 (2026-04-17)

#### Features Added
* Added Spark 4.1 support with updated HDFSMetadataLog import path for SPARK-52787 package reorganization. - See [PR 48861](https://github.com/Azure/azure-sdk-for-java/pull/48861)

#### Other Changes
* Introduced shared `azure-cosmos-spark_4` base module for code common across Spark 4.x versions. - See [PR 48861](https://github.com/Azure/azure-sdk-for-java/pull/48861)
* Inherited fix for `NoClassDefFoundError` for `MetadataVersionUtil` when using change feed Spark Structured Streaming on Databricks Runtime 17.3+ from the shared Spark connector codebase. - See [PR 48837](https://github.com/Azure/azure-sdk-for-java/pull/48837)
* Inherited fix for JVM `<clinit>` deadlock when multiple threads concurrently trigger Cosmos SDK class loading for the first time from the shared Spark connector codebase. - See [PR 48689](https://github.com/Azure/azure-sdk-for-java/pull/48689)
