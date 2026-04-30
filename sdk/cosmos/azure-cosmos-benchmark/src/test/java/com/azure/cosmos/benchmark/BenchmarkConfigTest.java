// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.benchmark;

import org.testng.annotations.Test;

import java.io.File;
import java.io.FileWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BenchmarkConfig} dispatch configuration validation.
 * These tests do not require a Cosmos DB connection.
 */
public class BenchmarkConfigTest {

    @Test(groups = "unit")
    public void maxRunningTimeDuration_zeroDuration_throwsIllegalArgument() throws Exception {
        File configFile = createConfigFile("{ \"maxRunningTimeDuration\": \"PT0S\", \"tenants\": [] }");
        try {
            BenchmarkConfig config = new BenchmarkConfig();
            assertThatThrownBy(() -> config.loadDispatchConfigFromFile(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
        } finally {
            configFile.delete();
        }
    }

    @Test(groups = "unit")
    public void maxRunningTimeDuration_negativeDuration_throwsIllegalArgument() throws Exception {
        File configFile = createConfigFile("{ \"maxRunningTimeDuration\": \"-PT10S\", \"tenants\": [] }");
        try {
            BenchmarkConfig config = new BenchmarkConfig();
            assertThatThrownBy(() -> config.loadDispatchConfigFromFile(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
        } finally {
            configFile.delete();
        }
    }

    @Test(groups = "unit")
    public void maxRunningTimeDuration_malformedDuration_throwsIllegalArgument() throws Exception {
        File configFile = createConfigFile("{ \"maxRunningTimeDuration\": \"notADuration\", \"tenants\": [] }");
        try {
            BenchmarkConfig config = new BenchmarkConfig();
            assertThatThrownBy(() -> config.loadDispatchConfigFromFile(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid ISO-8601 duration");
        } finally {
            configFile.delete();
        }
    }

    @Test(groups = "unit")
    public void maxRunningTimeDuration_validDuration_parsesSuccessfully() throws Exception {
        File configFile = createConfigFile("{ \"maxRunningTimeDuration\": \"PT30S\", \"tenants\": [] }");
        try {
            BenchmarkConfig config = new BenchmarkConfig();
            config.loadDispatchConfigFromFile(configFile);
            assertThat(config.getMaxRunningTimeDuration()).isEqualTo("PT30S");
            assertThat(config.getMaxRunningTimeDurationParsed()).isEqualTo(java.time.Duration.ofSeconds(30));
        } finally {
            configFile.delete();
        }
    }

    @Test(groups = "unit")
    public void concurrency_zeroConcurrency_throwsIllegalArgument() throws Exception {
        File configFile = createConfigFile("{ \"concurrency\": 0, \"tenants\": [] }");
        try {
            BenchmarkConfig config = new BenchmarkConfig();
            assertThatThrownBy(() -> config.loadDispatchConfigFromFile(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("concurrency must be a positive integer");
        } finally {
            configFile.delete();
        }
    }

    private File createConfigFile(String json) throws Exception {
        File tempFile = File.createTempFile("benchmark-config-test-", ".json");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(json);
        }
        return tempFile;
    }
}
