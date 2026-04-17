// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

// Forked from azure-cosmos-spark_3 to handle SPARK-52787 package reorganization
// Original: ../azure-cosmos-spark_3/src/test/scala/com/azure/cosmos/spark/SparkInternalsBridgeTest.scala

package com.azure.cosmos.spark

import org.apache.spark.executor.TaskMetrics
import org.apache.spark.sql.execution.metric.SQLMetric
import org.apache.spark.util.{AccumulatorV2, CollectionAccumulator}
import org.mockito.Mockito._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

import java.lang.reflect.Method
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.collection.mutable

/**
 * Comprehensive unit tests for SparkInternalsBridge covering:
 * - Reflection access control flags
 * - Method caching behavior
 * - Exception handling when reflection fails
 * - Fallback behavior when reflection is disabled
 * 
 * These tests address review finding F3 regarding missing test coverage
 * for complex reflection logic that could break with Spark version changes.
 */
class SparkInternalsBridgeTest extends AnyFlatSpec with Matchers with MockitoSugar with CosmosLoggingTrait {

  behavior of "SparkInternalsBridge"

  it should "return empty map when reflection access is disabled" in {
    // Create a spy to monitor internal state
    val bridge = spy(SparkInternalsBridge)
    
    // Force reflection access to be disabled
    val reflectionField = classOf[SparkInternalsBridge.type].getDeclaredField("reflectionAccessAllowed")
    reflectionField.setAccessible(true)
    val reflectionAccessAllowed = reflectionField.get(bridge).asInstanceOf[AtomicBoolean]
    reflectionAccessAllowed.set(false)

    val mockTaskMetrics = mock[TaskMetrics]
    val knownMetricNames = Set("cosmosMetric1", "cosmosMetric2")

    val result = bridge.getInternalCustomTaskMetricsAsSQLMetric(knownMetricNames, mockTaskMetrics)

    result shouldBe empty
    // Verify that the internal method was not called
    verify(bridge, never()).getInternalCustomTaskMetricsAsSQLMetricInternal(any(), any())
  }

  it should "cache method instances to avoid reflection overhead" in {
    val bridge = SparkInternalsBridge
    
    // Get the accumulatorsMethod field
    val methodField = classOf[SparkInternalsBridge.type].getDeclaredField("accumulatorsMethod")
    methodField.setAccessible(true)
    val accumulatorsMethod = methodField.get(bridge).asInstanceOf[AtomicReference[Method]]
    
    // Reset method cache
    accumulatorsMethod.set(null)
    
    val mockTaskMetrics = mock[TaskMetrics]
    
    // Create a mock Method to return
    val mockMethod = mock[Method]
    when(mockMethod.invoke(mockTaskMetrics)).thenReturn(Seq.empty[AccumulatorV2[_, _]])
    
    // Mock the class to return our mock method
    val mockClass = mock[Class[_]]
    when(mockTaskMetrics.getClass).thenReturn(mockClass.asInstanceOf[Class[TaskMetrics]])
    when(mockClass.getMethod("accumulators")).thenReturn(mockMethod)

    val knownMetricNames = Set("cosmosMetric1")
    
    // First call should cache the method
    bridge.getInternalCustomTaskMetricsAsSQLMetric(knownMetricNames, mockTaskMetrics)
    
    // Verify method was cached
    accumulatorsMethod.get() should not be null
    
    // Second call should use cached method
    bridge.getInternalCustomTaskMetricsAsSQLMetric(knownMetricNames, mockTaskMetrics)
    
    // Verify getMethod was called only once (caching worked)
    verify(mockClass, times(1)).getMethod("accumulators")
  }

  it should "handle reflection failures gracefully and disable future access" in {
    val bridge = SparkInternalsBridge
    
    // Reset reflection access flag
    val reflectionField = classOf[SparkInternalsBridge.type].getDeclaredField("reflectionAccessAllowed")
    reflectionField.setAccessible(true)
    val reflectionAccessAllowed = reflectionField.get(bridge).asInstanceOf[AtomicBoolean]
    reflectionAccessAllowed.set(true)
    
    // Reset method cache
    val methodField = classOf[SparkInternalsBridge.type].getDeclaredField("accumulatorsMethod")
    methodField.setAccessible(true)
    val accumulatorsMethod = methodField.get(bridge).asInstanceOf[AtomicReference[Method]]
    accumulatorsMethod.set(null)

    // Create a TaskMetrics that will throw when accessed via reflection
    val mockTaskMetrics = mock[TaskMetrics]
    when(mockTaskMetrics.getClass).thenThrow(new RuntimeException("Reflection access denied"))

    val knownMetricNames = Set("cosmosMetric1")

    val result = bridge.getInternalCustomTaskMetricsAsSQLMetric(knownMetricNames, mockTaskMetrics)

    // Should return empty map on failure
    result shouldBe empty
    
    // Reflection access should now be disabled for future calls
    reflectionAccessAllowed.get() shouldBe false
  }

  it should "filter accumulators correctly for known Cosmos metrics" in {
    val bridge = SparkInternalsBridge
    
    // Enable reflection access
    val reflectionField = classOf[SparkInternalsBridge.type].getDeclaredField("reflectionAccessAllowed")
    reflectionField.setAccessible(true)
    val reflectionAccessAllowed = reflectionField.get(bridge).asInstanceOf[AtomicBoolean]
    reflectionAccessAllowed.set(true)

    // Create mock SQL metrics
    val cosmosMetric1 = mock[SQLMetric]
    when(cosmosMetric1.isInstanceOf[SQLMetric]).thenReturn(true)
    when(cosmosMetric1.name).thenReturn(Some("cosmosMetric1"))

    val cosmosMetric2 = mock[SQLMetric]
    when(cosmosMetric2.isInstanceOf[SQLMetric]).thenReturn(true)
    when(cosmosMetric2.name).thenReturn(Some("cosmosMetric2"))

    val nonCosmosMetric = mock[SQLMetric]
    when(nonCosmosMetric.isInstanceOf[SQLMetric]).thenReturn(true)
    when(nonCosmosMetric.name).thenReturn(Some("otherMetric"))

    val metricWithoutName = mock[SQLMetric]
    when(metricWithoutName.isInstanceOf[SQLMetric]).thenReturn(true)
    when(metricWithoutName.name).thenReturn(None)

    val nonSQLMetric = mock[CollectionAccumulator[String]]
    when(nonSQLMetric.isInstanceOf[SQLMetric]).thenReturn(false)

    val allAccumulators = Seq(
      cosmosMetric1.asInstanceOf[AccumulatorV2[_, _]],
      cosmosMetric2.asInstanceOf[AccumulatorV2[_, _]],
      nonCosmosMetric.asInstanceOf[AccumulatorV2[_, _]],
      metricWithoutName.asInstanceOf[AccumulatorV2[_, _]],
      nonSQLMetric.asInstanceOf[AccumulatorV2[_, _]]
    )

    // Create a working TaskMetrics mock
    val mockTaskMetrics = mock[TaskMetrics]
    val mockMethod = mock[Method]
    when(mockMethod.invoke(mockTaskMetrics)).thenReturn(allAccumulators)
    
    val mockClass = mock[Class[_]]
    when(mockTaskMetrics.getClass).thenReturn(mockClass.asInstanceOf[Class[TaskMetrics]])
    when(mockClass.getMethod("accumulators")).thenReturn(mockMethod)

    val knownMetricNames = Set("cosmosMetric1", "cosmosMetric2")

    val result = bridge.getInternalCustomTaskMetricsAsSQLMetric(knownMetricNames, mockTaskMetrics)

    // Should only return the Cosmos metrics
    result should have size 2
    result should contain key "cosmosMetric1"
    result should contain key "cosmosMetric2"
    result should not contain key "otherMetric"
  }

  it should "handle method invocation failures" in {
    val bridge = SparkInternalsBridge
    
    // Enable reflection access
    val reflectionField = classOf[SparkInternalsBridge.type].getDeclaredField("reflectionAccessAllowed")
    reflectionField.setAccessible(true)
    val reflectionAccessAllowed = reflectionField.get(bridge).asInstanceOf[AtomicBoolean]
    reflectionAccessAllowed.set(true)
    
    // Reset method cache
    val methodField = classOf[SparkInternalsBridge.type].getDeclaredField("accumulatorsMethod")
    methodField.setAccessible(true)
    val accumulatorsMethod = methodField.get(bridge).asInstanceOf[AtomicReference[Method]]
    accumulatorsMethod.set(null)

    val mockTaskMetrics = mock[TaskMetrics]
    val mockMethod = mock[Method]
    
    // Make method invocation fail
    when(mockMethod.invoke(mockTaskMetrics)).thenThrow(new RuntimeException("Method invocation failed"))
    
    val mockClass = mock[Class[_]]
    when(mockTaskMetrics.getClass).thenReturn(mockClass.asInstanceOf[Class[TaskMetrics]])
    when(mockClass.getMethod("accumulators")).thenReturn(mockMethod)

    val knownMetricNames = Set("cosmosMetric1")

    val result = bridge.getInternalCustomTaskMetricsAsSQLMetric(knownMetricNames, mockTaskMetrics)

    // Should return empty map on method invocation failure
    result shouldBe empty
    
    // Reflection access should be disabled after failure
    reflectionAccessAllowed.get() shouldBe false
  }

  it should "handle setAccessible security restrictions" in {
    val bridge = SparkInternalsBridge
    
    // Enable reflection access
    val reflectionField = classOf[SparkInternalsBridge.type].getDeclaredField("reflectionAccessAllowed")
    reflectionField.setAccessible(true)
    val reflectionAccessAllowed = reflectionField.get(bridge).asInstanceOf[AtomicBoolean]
    reflectionAccessAllowed.set(true)
    
    // Reset method cache
    val methodField = classOf[SparkInternalsBridge.type].getDeclaredField("accumulatorsMethod")
    methodField.setAccessible(true)
    val accumulatorsMethod = methodField.get(bridge).asInstanceOf[AtomicReference[Method]]
    accumulatorsMethod.set(null)

    val mockTaskMetrics = mock[TaskMetrics]
    val mockMethod = mock[Method]
    
    // Make setAccessible fail
    when(mockMethod.setAccessible(true)).thenThrow(new SecurityException("Access denied"))
    
    val mockClass = mock[Class[_]]
    when(mockTaskMetrics.getClass).thenReturn(mockClass.asInstanceOf[Class[TaskMetrics]])
    when(mockClass.getMethod("accumulators")).thenReturn(mockMethod)

    val knownMetricNames = Set("cosmosMetric1")

    val result = bridge.getInternalCustomTaskMetricsAsSQLMetric(knownMetricNames, mockTaskMetrics)

    // Should return empty map on security restriction
    result shouldBe empty
    
    // Reflection access should be disabled after failure
    reflectionAccessAllowed.get() shouldBe false
  }

  it should "maintain thread safety with concurrent access" in {
    val bridge = SparkInternalsBridge
    
    // Enable reflection access
    val reflectionField = classOf[SparkInternalsBridge.type].getDeclaredField("reflectionAccessAllowed")
    reflectionField.setAccessible(true)
    val reflectionAccessAllowed = reflectionField.get(bridge).asInstanceOf[AtomicBoolean]
    reflectionAccessAllowed.set(true)
    
    // Reset method cache
    val methodField = classOf[SparkInternalsBridge.type].getDeclaredField("accumulatorsMethod")
    methodField.setAccessible(true)
    val accumulatorsMethod = methodField.get(bridge).asInstanceOf[AtomicReference[Method]]
    accumulatorsMethod.set(null)

    val mockTaskMetrics = mock[TaskMetrics]
    val mockMethod = mock[Method]
    when(mockMethod.invoke(mockTaskMetrics)).thenReturn(Seq.empty[AccumulatorV2[_, _]])
    
    val mockClass = mock[Class[_]]
    when(mockTaskMetrics.getClass).thenReturn(mockClass.asInstanceOf[Class[TaskMetrics]])
    when(mockClass.getMethod("accumulators")).thenReturn(mockMethod)

    val knownMetricNames = Set("cosmosMetric1")
    
    // Simulate concurrent access
    val results = mutable.ListBuffer[Map[String, SQLMetric]]()
    val threads = (1 to 10).map(_ => {
      new Thread(() => {
        val result = bridge.getInternalCustomTaskMetricsAsSQLMetric(knownMetricNames, mockTaskMetrics)
        results.synchronized {
          results += result
        }
      })
    })

    threads.foreach(_.start())
    threads.foreach(_.join())

    // All calls should complete successfully
    results should have size 10
    results.foreach(_ shouldBe empty) // Empty because mock returns empty seq
    
    // Method should be cached exactly once despite concurrent access
    accumulatorsMethod.get() should not be null
    verify(mockClass, times(1)).getMethod("accumulators")
  }
}