# Azure Cosmos DB OLTP Spark 4 connector

## Azure Cosmos DB OLTP Spark 4 connector for Spark 4.1
**Azure Cosmos DB OLTP Spark connector** provides Apache Spark support for Azure Cosmos DB using
the [SQL API][sql_api_query].
[Azure Cosmos DB][cosmos_introduction] is a globally-distributed database service which allows
developers to work with data using a variety of standard APIs, such as SQL, MongoDB, Cassandra, Graph, and Table.

If you have any feedback or ideas on how to improve your experience please let us know here:
https://github.com/Azure/azure-sdk-for-java/issues/new

### Documentation

- [Getting started](https://aka.ms/azure-cosmos-spark-3-quickstart)
- [Catalog API](https://aka.ms/azure-cosmos-spark-3-catalog-api)
- [Configuration Parameter Reference](https://aka.ms/azure-cosmos-spark-3-config)

### Version Compatibility

#### azure-cosmos-spark_4-1_2-13
| Connector | Supported Spark Versions | Minimum Java Version | Supported Scala Versions  | Supported Databricks Runtimes | Supported Fabric Runtimes |
|-----------|--------------------------|----------------------|---------------------------|-------------------------------|---------------------------|
| 4.47.0    | 4.1.0, 4.1.1            | [17, 21]             | 2.13                      | TBD                           | TBD                       |

Note: Spark 4.1 requires Scala 2.13 and Java 17 or higher. When using the Scala API, it is necessary for applications
to use Scala 2.13 that Spark 4.1 was compiled for.

This connector handles the package reorganization introduced in Apache Spark 4.1 (SPARK-52787) where 
`HDFSMetadataLog` and `MetadataVersionUtil` were moved from `org.apache.spark.sql.execution.streaming` 
to `org.apache.spark.sql.execution.streaming.checkpointing`.

### Usage

#### Maven

```xml
<dependency>
  <groupId>com.azure.cosmos.spark</groupId>
  <artifactId>azure-cosmos-spark_4-1_2-13</artifactId>
  <version>4.47.0</version>
</dependency>
```

#### Databricks

1. Launch an Azure Databricks cluster running a compatible runtime (see version compatibility table above)
2. Install the Azure Cosmos DB Spark Connector on your cluster:
   1. Download the jar from Maven Central
   2. Install jar on the cluster
   3. Attach jar to notebook libraries

#### Fabric

Azure Cosmos DB Spark connector support for Microsoft Fabric is coming soon.

## Contributing

This project welcomes contributions and suggestions. Most contributions require you to agree to a
Contributor License Agreement (CLA) declaring that you have the right to, and actually do, grant us
the rights to use your contribution. For details, visit https://cla.microsoft.com.

When you submit a pull request, a CLA-bot will automatically determine whether you need to provide
a CLA and decorate the PR appropriately (e.g., label, comment). Simply follow the instructions
provided by the bot. You will only need to do this once across all repos using our CLA.

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/).
For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or
contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.

<!-- LINKS -->
[source_code]: src
[cosmos_introduction]: https://docs.microsoft.com/azure/cosmos-db/
[cosmos_docs]: https://docs.microsoft.com/azure/cosmos-db/introduction
[jdk]: https://docs.microsoft.com/java/azure/jdk/
[maven]: https://maven.apache.org/
[sql_api_query]: https://docs.microsoft.com/azure/cosmos-db/how-to-sql-query

![Impressions](https://azure-sdk-impressions.azurewebsites.net/api/impressions/azure-sdk-for-java%2Fsdk%2Fcosmos%2Fazure-cosmos-spark_4-1_2-13%2FREADME.png)