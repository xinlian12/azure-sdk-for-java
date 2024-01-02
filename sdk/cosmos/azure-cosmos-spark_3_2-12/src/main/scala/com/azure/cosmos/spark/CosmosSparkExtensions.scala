package com.azure.cosmos.spark

import com.azure.cosmos.extension.CosmosReadManyJoinStrategy
import org.apache.spark.internal.Logging
import org.apache.spark.sql.SparkSessionExtensions

class CosmosSparkExtensions extends (SparkSessionExtensions => Unit) with Logging{
    override def apply(extensions: SparkSessionExtensions): Unit = {
        extensions.injectPlannerStrategy(CosmosReadManyJoinStrategy.apply)
    }
}
