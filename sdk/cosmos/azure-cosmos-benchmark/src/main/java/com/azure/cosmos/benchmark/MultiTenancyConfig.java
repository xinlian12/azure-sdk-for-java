// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.benchmark;

import com.beust.jcommander.Parameter;

/**
 * CLI configuration for the multi-tenancy benchmark orchestrator.
 * These are orchestrator-level params — workload config comes from tenants.json globalDefaults.
 * See §4.8 of the test plan.
 */
public class MultiTenancyConfig {

    @Parameter(names = "--tenantsFile", description = "Path to tenants.json", required = true)
    private String tenantsFile;

    @Parameter(names = "--scenario", description = "Test scenario: SCALING, CHURN, CACHE_GROWTH, POOL_PRESSURE, SOAK")
    private String scenario = "SCALING";

    @Parameter(names = "--monitorIntervalSec", description = "Resource monitor sampling interval in seconds")
    private int monitorIntervalSec = 10;

    @Parameter(names = "--outputDir", description = "Directory for CSVs and summary output")
    private String outputDir = "./results";

    @Parameter(names = "--churnCycles", description = "Number of create/destroy cycles for CHURN scenario")
    private int churnCycles = 100;

    @Parameter(names = "--distinctQueries", description = "Number of distinct queries for CACHE_GROWTH scenario")
    private int distinctQueries = 100;

    @Parameter(names = "--appInsightsConnectionString", description = "Override for Application Insights connection string")
    private String appInsightsConnectionString;

    @Parameter(names = "--skipWarmup", description = "Skip warmup phase (for idle scaling tests)")
    private boolean skipWarmup = false;

    // ── Diagnostics ──

    @Parameter(names = "--threadDumpIntervalSec", description = "If >0, capture a thread dump every N seconds")
    private int threadDumpIntervalSec = 0;

    @Parameter(names = "--threadDumpOnEvent", description = "Comma-separated lifecycle events for thread dump: PRE_CREATE,POST_CREATE,POST_WORKLOAD,POST_CLOSE")
    private String threadDumpOnEvent;

    @Parameter(names = "--heapDumpOnEvent", description = "Comma-separated lifecycle events for heap dump: POST_CREATE,POST_WORKLOAD,POST_CLOSE")
    private String heapDumpOnEvent;

    @Parameter(names = "--jfrDurationSec", description = "Duration of JFR recording (0 = disabled)")
    private int jfrDurationSec = 300;

    // ── Git metadata ──

    @Parameter(names = "--branch", description = "Git branch name (auto-detected if not set)")
    private String branch;

    @Parameter(names = "--commitId", description = "Git commit SHA (auto-detected if not set)")
    private String commitId;

    @Parameter(names = "--prNumber", description = "GitHub PR number for tracking")
    private String prNumber;

    // ── Result storage ──

    @Parameter(names = "--resultSink", description = "Where to store results: CSV, COSMOS, KUSTO, ALL")
    private String resultSink = "CSV";

    @Parameter(names = "--resultCosmosEndpoint", description = "Cosmos DB endpoint for result storage")
    private String resultCosmosEndpoint;

    @Parameter(names = "--resultCosmosDatabase", description = "Database name for result storage")
    private String resultCosmosDatabase = "benchresults";

    @Parameter(names = "--resultCosmosContainer", description = "Container name for result storage")
    private String resultCosmosContainer = "runs";

    @Parameter(names = "--resultKustoCluster", description = "Kusto cluster URI for result storage")
    private String resultKustoCluster;

    @Parameter(names = "--resultKustoDatabase", description = "Kusto database name")
    private String resultKustoDatabase = "BenchmarkResults";

    @Parameter(names = {"-h", "--help"}, description = "Show help", help = true)
    private boolean help = false;

    // ── Getters ──

    public String getTenantsFile() {
        return tenantsFile;
    }

    public String getScenario() {
        return scenario;
    }

    public int getMonitorIntervalSec() {
        return monitorIntervalSec;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public int getChurnCycles() {
        return churnCycles;
    }

    public int getDistinctQueries() {
        return distinctQueries;
    }

    public String getAppInsightsConnectionString() {
        return appInsightsConnectionString;
    }

    public boolean isSkipWarmup() {
        return skipWarmup;
    }

    public int getThreadDumpIntervalSec() {
        return threadDumpIntervalSec;
    }

    public String getThreadDumpOnEvent() {
        return threadDumpOnEvent;
    }

    public String getHeapDumpOnEvent() {
        return heapDumpOnEvent;
    }

    public int getJfrDurationSec() {
        return jfrDurationSec;
    }

    public String getBranch() {
        return branch;
    }

    public String getCommitId() {
        return commitId;
    }

    public String getPrNumber() {
        return prNumber;
    }

    public String getResultSink() {
        return resultSink;
    }

    public String getResultCosmosEndpoint() {
        return resultCosmosEndpoint;
    }

    public String getResultCosmosDatabase() {
        return resultCosmosDatabase;
    }

    public String getResultCosmosContainer() {
        return resultCosmosContainer;
    }

    public String getResultKustoCluster() {
        return resultKustoCluster;
    }

    public String getResultKustoDatabase() {
        return resultKustoDatabase;
    }

    public boolean isHelp() {
        return help;
    }

    /**
     * Check if a specific lifecycle event is in the thread dump event list.
     */
    public boolean shouldThreadDumpOnEvent(String event) {
        return threadDumpOnEvent != null && threadDumpOnEvent.contains(event);
    }

    /**
     * Check if a specific lifecycle event is in the heap dump event list.
     */
    public boolean shouldHeapDumpOnEvent(String event) {
        return heapDumpOnEvent != null && heapDumpOnEvent.contains(event);
    }
}
