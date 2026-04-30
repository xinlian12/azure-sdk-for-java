// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Internal benchmark configuration built from CLI-parsed {@link Configuration}.
 * Contains lifecycle params, reporting config, and fully-resolved tenant workloads.
 *
 * <p>Each {@link TenantWorkloadConfig} carries its complete effective config
 * (account info + workload params), so no separate tenantDefaults map is needed.</p>
 *
 * <p>When {@code cycles > 1}, sensible defaults are applied automatically
 * unless explicitly overridden (settleTimeMs=90s, suppressCleanup=true).</p>
 */
public class BenchmarkConfig {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkConfig.class);
    private static final long DEFAULT_SETTLE_TIME_MS = 90_000;

    // -- Lifecycle --
    private int cycles = 1;
    private long settleTimeMs = 0;
    private boolean suppressCleanup = false;
    private boolean gcBetweenCycles = true;
    private boolean enableJvmStats = false;
    private boolean enableNettyHttpMetrics = false;

    // -- Reporting --
    private int printingInterval = 10;

    // At most one destination is configured (null = console-only)
    private CsvReporterConfig csvReporterConfig;
    private CosmosReporterConfig cosmosReporterConfig;
    private AppInsightsReporterConfig appInsightsReporterConfig;

    // -- JVM-global system properties (apply to all tenants, set once at startup) --
    private boolean isPartitionLevelCircuitBreakerEnabled = true;
    private boolean isPerPartitionAutomaticFailoverRequired = true;
    private int minConnectionPoolSizePerEndpoint = 0;

    // -- Orchestrator-level dispatch (controls total workload, not per-tenant) --
    // In dispatch mode, per-tenant concurrency and numberOfOperations in TenantWorkloadConfig
    // are ignored — the orchestrator-level values below control the total workload.
    private int concurrency = 1000;
    private long numberOfOperations = 100000;
    private boolean numberOfOperationsExplicitlySet = false;
    private String maxRunningTimeDuration;
    private java.time.Duration maxRunningTimeDurationParsed;

    // -- Tenants (each carries its full effective config) --
    private List<TenantWorkloadConfig> tenantWorkloads = Collections.emptyList();

    public BenchmarkConfig() {}

    /**
     * Build a BenchmarkConfig from CLI-parsed Configuration.
     */
    public static BenchmarkConfig fromConfiguration(Configuration cfg) throws IOException {
        BenchmarkConfig config = new BenchmarkConfig();

        // Lifecycle with smart defaults for cycles > 1
        config.cycles = cfg.getCycles();
        config.settleTimeMs = cfg.getSettleTimeMs();
        config.suppressCleanup = cfg.isSuppressCleanup();

        if (config.cycles > 1) {
            long configuredSettleTimeMs = cfg.getSettleTimeMs();
            // Only apply the default settle time when the configuration uses the sentinel -1.
            // An explicit value (including 0 to disable settling) should be respected.
            config.settleTimeMs = (configuredSettleTimeMs == -1)
                ? DEFAULT_SETTLE_TIME_MS
                : configuredSettleTimeMs;
            config.suppressCleanup = true; // suppress container/database cleanup
        }

        config.gcBetweenCycles = cfg.isGcBetweenCycles();

        // Workload config - ALWAYS from config file
        String workloadConfigPath = cfg.getWorkloadConfig();
        if (workloadConfigPath == null || !new File(workloadConfigPath).exists()) {
            throw new IllegalArgumentException(
                "A workload configuration file is required. Use -workloadConfig to specify the path."
                + (workloadConfigPath != null ? " File not found: " + workloadConfigPath : ""));
        }

        logger.info("Loading workload configs from {}.", workloadConfigPath);
        File workloadFile = new File(workloadConfigPath);
        config.tenantWorkloads = TenantWorkloadConfig.parseWorkloadConfig(workloadFile);
        config.loadWorkloadConfigSections(workloadFile);

        return config;
    }

    // ======== Getters ========

    public int getCycles() { return cycles; }
    public long getSettleTimeMs() { return settleTimeMs; }
    public boolean isSuppressCleanup() { return suppressCleanup; }
    public boolean isGcBetweenCycles() { return gcBetweenCycles; }
    public boolean isEnableJvmStats() { return enableJvmStats; }
    public boolean isEnableNettyHttpMetrics() { return enableNettyHttpMetrics; }

    public int getPrintingInterval() { return printingInterval; }
    public CsvReporterConfig getCsvReporterConfig() { return csvReporterConfig; }
    public CosmosReporterConfig getCosmosReporterConfig() { return cosmosReporterConfig; }
    public AppInsightsReporterConfig getAppInsightsReporterConfig() { return appInsightsReporterConfig; }

    /**
     * Determine the reporting destination from which config is present.
     */
    public ReportingDestination getReportingDestination() {
        if (csvReporterConfig != null) return ReportingDestination.CSV;
        if (cosmosReporterConfig != null) return ReportingDestination.COSMOSDB;
        if (appInsightsReporterConfig != null) return ReportingDestination.APPLICATION_INSIGHTS;
        return null;
    }

    public boolean isPartitionLevelCircuitBreakerEnabled() { return isPartitionLevelCircuitBreakerEnabled; }
    public boolean isPerPartitionAutomaticFailoverRequired() { return isPerPartitionAutomaticFailoverRequired; }
    public int getMinConnectionPoolSizePerEndpoint() { return minConnectionPoolSizePerEndpoint; }

    public int getConcurrency() { return concurrency; }
    public long getNumberOfOperations() { return numberOfOperations; }
    public boolean isNumberOfOperationsExplicitlySet() { return numberOfOperationsExplicitlySet; }
    public String getMaxRunningTimeDuration() { return maxRunningTimeDuration; }

    public java.time.Duration getMaxRunningTimeDurationParsed() {
        return maxRunningTimeDurationParsed;
    }

    public List<TenantWorkloadConfig> getTenantWorkloads() { return tenantWorkloads; }

    @Override
    public String toString() {
        return String.format(
            "BenchmarkConfig{cycles=%d, settleTimeMs=%d, suppressCleanup=%s, " +
            "gcBetweenCycles=%s, tenants=%d, concurrency=%d, numberOfOperations=%d, " +
            "maxRunningTimeDuration=%s, reportingDestination=%s, " +
            "circuitBreaker=%s, ppaf=%s, minConnPoolSize=%d}",
            cycles, settleTimeMs, suppressCleanup, gcBetweenCycles,
            tenantWorkloads.size(), concurrency, numberOfOperations,
            maxRunningTimeDuration, getReportingDestination(),
            isPartitionLevelCircuitBreakerEnabled, isPerPartitionAutomaticFailoverRequired,
            minConnectionPoolSizePerEndpoint);
    }

    /**
     * Loads all non-tenant sections from the workload config file:
     * JVM system properties, metrics config, result upload, and run metadata.
     */
    private void loadWorkloadConfigSections(File workloadConfigFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(workloadConfigFile);

        loadDispatchConfig(root);
        loadJvmSystemProperties(root);
        loadMetricsConfig(root);
    }

    /**
     * Loads dispatch config from a JSON file. Package-private for testing.
     */
    void loadDispatchConfigFromFile(File configFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(configFile);
        loadDispatchConfig(root);
    }

    /**
     * Orchestrator-level dispatch settings from the JSON root.
     * These control how many total operations to run and with what concurrency.
     */
    private void loadDispatchConfig(JsonNode root) {
        if (root.has("concurrency")) {
            concurrency = root.get("concurrency").asInt(concurrency);
        }
        if (root.has("numberOfOperations")) {
            numberOfOperations = root.get("numberOfOperations").asLong(numberOfOperations);
            numberOfOperationsExplicitlySet = true;
        }
        if (root.has("maxRunningTimeDuration")) {
            maxRunningTimeDuration = root.get("maxRunningTimeDuration").asText();
            // Validate eagerly so malformed values surface at config-load time
            try {
                java.time.Duration d = java.time.Duration.parse(maxRunningTimeDuration);
                if (d.isZero() || d.isNegative()) {
                    throw new IllegalArgumentException(
                        "maxRunningTimeDuration must be positive, got: '" + maxRunningTimeDuration + "'");
                }
                maxRunningTimeDurationParsed = d;
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalArgumentException(
                    "maxRunningTimeDuration is not a valid ISO-8601 duration: '"
                        + maxRunningTimeDuration + "'", e);
            }
        }

        if (concurrency <= 0) {
            throw new IllegalArgumentException(
                "concurrency must be a positive integer, got: " + concurrency);
        }
        if (numberOfOperations <= 0) {
            throw new IllegalArgumentException(
                "numberOfOperations must be a positive integer, got: " + numberOfOperations);
        }
    }

    /**
     * JVM-global system properties from the tenantDefaults section.
     * These are JVM-wide and cannot vary per tenant.
     */
    private void loadJvmSystemProperties(JsonNode root) {
        JsonNode defaults = root.get("tenantDefaults");
        if (defaults != null && defaults.isObject()) {
            if (defaults.has("isPartitionLevelCircuitBreakerEnabled")) {
                isPartitionLevelCircuitBreakerEnabled =
                    Boolean.parseBoolean(defaults.get("isPartitionLevelCircuitBreakerEnabled").asText());
            }
            if (defaults.has("isPerPartitionAutomaticFailoverRequired")) {
                isPerPartitionAutomaticFailoverRequired =
                    Boolean.parseBoolean(defaults.get("isPerPartitionAutomaticFailoverRequired").asText());
            }
            if (defaults.has("minConnectionPoolSizePerEndpoint")) {
                minConnectionPoolSizePerEndpoint =
                    Integer.parseInt(defaults.get("minConnectionPoolSizePerEndpoint").asText());
            }
        }
    }

    /**
     * Metrics and reporting settings from the top-level "metrics" section.
     * Reporter destination is determined by which key is present under "metrics.destination":
     * "csv", "cosmos", or "applicationInsights". At most one should be configured.
     */
    private void loadMetricsConfig(JsonNode root) {
        JsonNode metrics = root.get("metrics");
        if (metrics == null || !metrics.isObject()) {
            return;
        }

        if (metrics.has("enableJvmStats")) {
            enableJvmStats = Boolean.parseBoolean(metrics.get("enableJvmStats").asText());
        }
        if (metrics.has("enableNettyHttpMetrics")) {
            enableNettyHttpMetrics = Boolean.parseBoolean(metrics.get("enableNettyHttpMetrics").asText());
        }
        if (metrics.has("printingInterval")) {
            printingInterval = Integer.parseInt(metrics.get("printingInterval").asText());
        }

        JsonNode destination = metrics.get("destination");
        if (destination == null || !destination.isObject()) {
            return;
        }

        // CSV
        JsonNode csv = destination.get("csv");
        if (csv != null && csv.isObject()) {
            csvReporterConfig = new CsvReporterConfig(
                csv.has("reportingDirectory") ? csv.get("reportingDirectory").asText() : null);
        }

        // Cosmos DB
        JsonNode cosmos = destination.get("cosmos");
        if (cosmos != null && cosmos.isObject()) {
            cosmosReporterConfig = new CosmosReporterConfig(
                cosmos.has("serviceEndpoint") ? cosmos.get("serviceEndpoint").asText() : null,
                cosmos.has("masterKey") ? cosmos.get("masterKey").asText() : null,
                cosmos.has("database") ? cosmos.get("database").asText() : null,
                cosmos.has("container") ? cosmos.get("container").asText() : null,
                cosmos.has("testVariationName") ? cosmos.get("testVariationName").asText() : null,
                cosmos.has("branchName") ? cosmos.get("branchName").asText() : null,
                cosmos.has("commitId") ? cosmos.get("commitId").asText() : null);
        }

        // Application Insights
        JsonNode appInsights = destination.get("applicationInsights");
        if (appInsights != null && appInsights.isObject()) {
            appInsightsReporterConfig = new AppInsightsReporterConfig(
                appInsights.has("connectionString") ? appInsights.get("connectionString").asText() : null,
                appInsights.has("stepSeconds") ? Integer.parseInt(appInsights.get("stepSeconds").asText()) : 10,
                appInsights.has("testCategory") ? appInsights.get("testCategory").asText() : null);
        }

        // Warn if multiple destinations are configured — only the first match is used
        int configuredCount = (csvReporterConfig != null ? 1 : 0)
            + (cosmosReporterConfig != null ? 1 : 0)
            + (appInsightsReporterConfig != null ? 1 : 0);
        if (configuredCount > 1) {
            logger.warn("Multiple reporting destinations configured; only '{}' will be used. "
                + "Destinations are mutually exclusive.", getReportingDestination());
        }
    }
}
