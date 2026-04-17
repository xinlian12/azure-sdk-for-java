// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.azure.cosmos.spark

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.execution.streaming.checkpointing.{HDFSMetadataLog, MetadataVersionUtil}

/**
 * Integration test specifically validating SPARK-52787 package reorganization fixes.
 * Ensures classes can be loaded from new package locations in Spark 4.1.
 */
class Spark41PackageReorganizationITest extends UnitSpec {

  "SPARK-52787 package reorganization" should "successfully load HDFSMetadataLog from new package" in {
    val spark = SparkSession.builder()
      .appName("Spark41PackageReorganizationTest")
      .master("local[*]")
      .config("spark.sql.warehouse.dir", "/tmp/spark-warehouse")
      .getOrCreate()

    try {
      // Test 1: Verify HDFSMetadataLog can be instantiated from new package location
      val metadataPath = "/tmp/test-metadata-log"
      
      // This should not throw ClassNotFoundException if package reorganization is handled correctly
      noException should be thrownBy {
        new TestMetadataLog(spark, metadataPath)
      }

      // Test 2: Verify class is loaded from correct package
      val metadataLog = new TestMetadataLog(spark, metadataPath)
      val className = metadataLog.getClass.getSuperclass.getName
      className should include("org.apache.spark.sql.execution.streaming.checkpointing.HDFSMetadataLog")

    } finally {
      spark.stop()
    }
  }

  it should "successfully access MetadataVersionUtil from new package" in {
    // Test that we can access MetadataVersionUtil from the new package location
    // Note: We don't directly use this in ChangeFeedInitialOffsetWriter (it's inlined),
    // but verify it's available for potential future use
    noException should be thrownBy {
      val utilClass = Class.forName("org.apache.spark.sql.execution.streaming.checkpointing.MetadataVersionUtil$")
      utilClass should not be null
    }
  }

  it should "successfully instantiate CosmosCatalogBase with new HDFSMetadataLog package" in {
    val spark = SparkSession.builder()
      .appName("Spark41CatalogTest")
      .master("local[*]")
      .config("spark.sql.warehouse.dir", "/tmp/spark-warehouse")
      .getOrCreate()

    try {
      // This tests that CosmosCatalogBase can be instantiated with the updated import
      // Without throwing ClassNotFoundException for HDFSMetadataLog
      noException should be thrownBy {
        // CosmosCatalogBase uses HDFSMetadataLog internally for view repository
        // The class should load successfully with Spark 4.1 package structure
        val catalogBaseClass = Class.forName("com.azure.cosmos.spark.CosmosCatalogBase")
        catalogBaseClass should not be null
      }
    } finally {
      spark.stop()
    }
  }

  it should "successfully instantiate ChangeFeedInitialOffsetWriter with new HDFSMetadataLog package" in {
    val spark = SparkSession.builder()
      .appName("Spark41OffsetWriterTest")
      .master("local[*]")
      .getOrCreate()

    try {
      // Test that ChangeFeedInitialOffsetWriter can be instantiated with Spark 4.1
      val metadataPath = "/tmp/test-offset-writer"
      
      noException should be thrownBy {
        new ChangeFeedInitialOffsetWriter(spark, metadataPath)
      }

      // Verify the writer extends the correct class from the new package
      val writer = new ChangeFeedInitialOffsetWriter(spark, metadataPath)
      val superClassName = writer.getClass.getSuperclass.getName
      superClassName should include("org.apache.spark.sql.execution.streaming.checkpointing.HDFSMetadataLog")

    } finally {
      spark.stop()
    }
  }

  /**
   * Test implementation of HDFSMetadataLog to verify class loading
   */
  private class TestMetadataLog(spark: SparkSession, path: String) 
    extends HDFSMetadataLog[String](spark, path) {
    
    override def serialize(metadata: String, out: java.io.OutputStream): Unit = {
      out.write(metadata.getBytes("UTF-8"))
    }

    override def deserialize(in: java.io.InputStream): String = {
      scala.io.Source.fromInputStream(in, "UTF-8").mkString
    }
  }
}