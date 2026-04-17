// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
// Origin: Forked from azure-cosmos-spark_3 to address SPARK-52787 package reorganization in Spark 4.1
package com.azure.cosmos.spark

import org.apache.spark.sql.SparkSession
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets

class ChangeFeedInitialOffsetWriterSpec extends UnitSpec {

  "validateVersion" should "return version for valid version string within supported range" in {
    ChangeFeedInitialOffsetWriter.validateVersion("v1", 1) shouldBe 1
  }

  it should "return version when version is less than max supported" in {
    ChangeFeedInitialOffsetWriter.validateVersion("v1", 5) shouldBe 1
  }

  it should "return version when version equals max supported" in {
    ChangeFeedInitialOffsetWriter.validateVersion("v3", 3) shouldBe 3
  }

  it should "throw IllegalStateException for version exceeding max supported" in {
    val exception = intercept[IllegalStateException] {
      ChangeFeedInitialOffsetWriter.validateVersion("v2", 1)
    }
    exception.getMessage should include("UnsupportedLogVersion")
    exception.getMessage should include("v1")
    exception.getMessage should include("v2")
  }

  it should "throw IllegalStateException for non-numeric version" in {
    val exception = intercept[IllegalStateException] {
      ChangeFeedInitialOffsetWriter.validateVersion("vabc", 1)
    }
    exception.getMessage should include("malformed")
  }

  it should "throw IllegalStateException for empty string" in {
    val exception = intercept[IllegalStateException] {
      ChangeFeedInitialOffsetWriter.validateVersion("", 1)
    }
    exception.getMessage should include("malformed")
  }

  it should "throw IllegalStateException for string without v prefix" in {
    val exception = intercept[IllegalStateException] {
      ChangeFeedInitialOffsetWriter.validateVersion("1", 1)
    }
    exception.getMessage should include("malformed")
  }

  it should "throw IllegalStateException for v0 (zero version)" in {
    val exception = intercept[IllegalStateException] {
      ChangeFeedInitialOffsetWriter.validateVersion("v0", 1)
    }
    exception.getMessage should include("malformed")
  }

  it should "throw IllegalStateException for negative version" in {
    val exception = intercept[IllegalStateException] {
      ChangeFeedInitialOffsetWriter.validateVersion("v-1", 1)
    }
    exception.getMessage should include("malformed")
  }

  it should "throw IllegalStateException for version string with only v" in {
    val exception = intercept[IllegalStateException] {
      ChangeFeedInitialOffsetWriter.validateVersion("v", 1)
    }
    exception.getMessage should include("malformed")
  }

  "serialize and deserialize" should "handle round-trip correctly" in {
    // Create a temporary SparkSession for testing
    val spark = SparkSession.builder()
      .appName("ChangeFeedInitialOffsetWriterTest")
      .master("local[*]")
      .getOrCreate()

    try {
      val metadataPath = "/tmp/test-metadata"
      val writer = new ChangeFeedInitialOffsetWriter(spark, metadataPath)
      val testOffsetJson = """{"partitionId": "test-partition", "lsn": 12345}"""

      // Test serialization
      val outputStream = new ByteArrayOutputStream()
      writer.serialize(testOffsetJson, outputStream)
      val serializedData = outputStream.toByteArray

      // Verify serialized format
      val serializedString = new String(serializedData, StandardCharsets.UTF_8)
      serializedString should startWith("v1\n")
      serializedString should include(testOffsetJson)

      // Test deserialization
      val inputStream = new ByteArrayInputStream(serializedData)
      val deserializedJson = writer.deserialize(inputStream)

      deserializedJson shouldBe testOffsetJson
    } finally {
      spark.stop()
    }
  }

  it should "handle different JSON structures in serialize/deserialize" in {
    val spark = SparkSession.builder()
      .appName("ChangeFeedInitialOffsetWriterTest")
      .master("local[*]")
      .getOrCreate()

    try {
      val metadataPath = "/tmp/test-metadata"
      val writer = new ChangeFeedInitialOffsetWriter(spark, metadataPath)
      
      // Test with complex JSON
      val complexOffsetJson = """{"containers": [{"database": "testDb", "container": "testContainer", "partitionKeyRangeId": "0", "lsn": 98765}]}"""

      val outputStream = new ByteArrayOutputStream()
      writer.serialize(complexOffsetJson, outputStream)
      
      val inputStream = new ByteArrayInputStream(outputStream.toByteArray)
      val deserializedJson = writer.deserialize(inputStream)

      deserializedJson shouldBe complexOffsetJson
    } finally {
      spark.stop()
    }
  }

  it should "throw exception when deserializing malformed data" in {
    val spark = SparkSession.builder()
      .appName("ChangeFeedInitialOffsetWriterTest")
      .master("local[*]")
      .getOrCreate()

    try {
      val metadataPath = "/tmp/test-metadata"
      val writer = new ChangeFeedInitialOffsetWriter(spark, metadataPath)

      // Test with empty input
      val emptyInputStream = new ByteArrayInputStream(Array[Byte]())
      intercept[IllegalArgumentException] {
        writer.deserialize(emptyInputStream)
      }

      // Test with malformed version
      val malformedData = "invalid_version\nsome_json"
      val malformedInputStream = new ByteArrayInputStream(malformedData.getBytes(StandardCharsets.UTF_8))
      intercept[IllegalStateException] {
        writer.deserialize(malformedInputStream)
      }

      // Test with missing newline
      val noNewlineData = "v1some_json_without_newline"
      val noNewlineInputStream = new ByteArrayInputStream(noNewlineData.getBytes(StandardCharsets.UTF_8))
      intercept[IllegalStateException] {
        writer.deserialize(noNewlineInputStream)
      }
    } finally {
      spark.stop()
    }
  }

  it should "be compatible with existing checkpoint data format" in {
    val spark = SparkSession.builder()
      .appName("ChangeFeedInitialOffsetWriterTest")
      .master("local[*]")
      .getOrCreate()

    try {
      val metadataPath = "/tmp/test-metadata"
      val writer = new ChangeFeedInitialOffsetWriter(spark, metadataPath)
      
      // Simulate existing checkpoint data (v1 format)
      val legacyOffsetJson = """{"legacyFormat": true, "timestamp": 1234567890}"""
      val legacyData = s"v1\n$legacyOffsetJson"
      val legacyInputStream = new ByteArrayInputStream(legacyData.getBytes(StandardCharsets.UTF_8))
      
      val deserializedJson = writer.deserialize(legacyInputStream)
      deserializedJson shouldBe legacyOffsetJson
    } finally {
      spark.stop()
    }
  }
}