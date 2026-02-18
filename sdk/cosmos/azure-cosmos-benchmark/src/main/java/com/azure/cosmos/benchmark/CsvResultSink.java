// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes benchmark results to local CSV files.
 * Always enabled as the baseline output format.
 * See §7.2 of the test plan for file layout.
 */
public class CsvResultSink implements ResultSink {

    private static final Logger logger = LoggerFactory.getLogger(CsvResultSink.class);
    private final Path outputDir;

    public CsvResultSink(Path outputDir) {
        this.outputDir = outputDir;
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            logger.error("Failed to create output directory: {}", outputDir, e);
        }
    }

    @Override
    public void uploadSnapshots(String testRunId, List<ResourceMonitor.ResourceSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            logger.warn("No snapshots to write for run {}", testRunId);
            return;
        }

        Path csvFile = outputDir.resolve("resource_snapshots.csv");
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(csvFile))) {
            writer.println(ResourceMonitor.ResourceSnapshot.csvHeader());
            for (ResourceMonitor.ResourceSnapshot snapshot : snapshots) {
                writer.println(snapshot.toCsvLine());
            }
            logger.info("Wrote {} snapshots to {}", snapshots.size(), csvFile);
        } catch (IOException e) {
            logger.error("Failed to write snapshots CSV: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        // No resources to close for CSV
    }
}
