// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

// Forked from azure-cosmos-spark_3 — only HDFSMetadataLog import differs (SPARK-52787)
package com.azure.cosmos.spark

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.execution.streaming.checkpointing.HDFSMetadataLog

import java.io.{BufferedWriter, InputStream, InputStreamReader, OutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets

private class ChangeFeedInitialOffsetWriter
(
  sparkSession: SparkSession,
  metadataPath: String
) extends HDFSMetadataLog[String](sparkSession, metadataPath) {

  val VERSION = 1

  override def serialize(offsetJson: String, out: OutputStream): Unit = {
    val writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))
    writer.write(s"v$VERSION\n")
    writer.write(offsetJson)
    writer.flush()
  }

  override def deserialize(in: InputStream): String = {
    val content = readerToString(new InputStreamReader(in, StandardCharsets.UTF_8))
    // HDFSMetadataLog would never create a partial file.
    require(content.nonEmpty)
    val indexOfNewLine = content.indexOf("\n")
    if (content(0) != 'v' || indexOfNewLine < 0) {
      throw new IllegalStateException(
        "Log file was malformed: failed to detect the log file version line.")
    }

    ChangeFeedInitialOffsetWriter.validateVersion(content.substring(0, indexOfNewLine), VERSION)
    content.substring(indexOfNewLine + 1)
  }

  private def readerToString(reader: java.io.Reader): String = {
    val writer = new StringBuilderWriter
    val buffer = new Array[Char](4096)
    Stream.continually(reader.read(buffer)).takeWhile(_ != -1).foreach(writer.write(buffer, 0, _))
    writer.toString
  }

  private class StringBuilderWriter extends java.io.Writer {
    private val stringBuilder = new StringBuilder

    override def write(cbuf: Array[Char], off: Int, len: Int): Unit = {
      stringBuilder.appendAll(cbuf, off, len)
    }

    override def flush(): Unit = {}

    override def close(): Unit = {}

    override def toString: String = stringBuilder.toString()
  }
}

private[spark] object ChangeFeedInitialOffsetWriter {
  /**
   * Validates the version string from the log file.
   * 
   * This logic is deliberately inlined rather than using Spark's MetadataVersionUtil to avoid
   * runtime dependency issues. MetadataVersionUtil has been relocated across different Spark
   * distributions (e.g., moved to checkpointing package in SPARK-52787, unavailable in some
   * Databricks Runtime versions like 17.3+).
   * 
   * Technical Debt: This creates maintenance overhead as Spark's validation logic evolves.
   * 
   * Migration Plan:
   * - Target: Consolidate when Spark 3.x support is dropped (estimated 2025-2026)
   * - Trigger: When minimum supported Spark version >= 4.1 across all Azure SDK for Java releases
   * - Alternative: Create thin abstraction layer if differences become significant before consolidation
   * - Monitor: Track Spark validation logic changes in each release to ensure compatibility
   * 
   * @param versionText the version string to validate (e.g., "v1")
   * @param maxSupportedVersion maximum supported version number
   * @return parsed version number if valid
   * @throws IllegalStateException if version is invalid, unsupported, or malformed
   */
  def validateVersion(versionText: String, maxSupportedVersion: Int): Int = {
    if (versionText.nonEmpty && versionText(0) == 'v') {
      val version =
        try {
          versionText.substring(1).toInt
        } catch {
          case _: NumberFormatException =>
            throw new IllegalStateException(
              s"Log file was malformed: failed to read correct log version from $versionText.")
        }
      if (version > 0 && version <= maxSupportedVersion) {
        return version
      }
      if (version > maxSupportedVersion) {
        throw new IllegalStateException(
          s"UnsupportedLogVersion: maximum supported log version " +
            s"is v$maxSupportedVersion, but encountered v$version. " +
            s"The log file was produced by a newer version of Spark and cannot be read by this version. " +
            s"Please upgrade.")
      }
    }
    throw new IllegalStateException(
      s"Log file was malformed: failed to read correct log version from $versionText.")
  }
}
