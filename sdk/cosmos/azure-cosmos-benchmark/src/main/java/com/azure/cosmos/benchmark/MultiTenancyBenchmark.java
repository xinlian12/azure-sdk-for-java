// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.benchmark;

import com.beust.jcommander.JCommander;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Multi-tenancy benchmark orchestrator.
 * Creates N {@link AsyncBenchmark} instances (one per tenant), runs them in parallel,
 * collects JVM/OS resource metrics, and writes results via {@link ResultSink}.
 *
 * See §4 of MULTI_TENANCY_TEST_PLAN.md for the architecture.
 */
public class MultiTenancyBenchmark {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenancyBenchmark.class);

    public static void main(String[] args) throws Exception {
        MultiTenancyConfig config = new MultiTenancyConfig();
        JCommander jcommander = new JCommander(config, args);
        if (config.isHelp()) {
            jcommander.usage();
            return;
        }

        new MultiTenancyBenchmark().run(config);
    }

    public void run(MultiTenancyConfig config) throws Exception {
        String testRunId = String.format("%s-%s",
            config.getScenario(), Instant.now().toString().replace(':', '-'));
        Path outputDir = Paths.get(config.getOutputDir());

        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info(" Multi-Tenancy Benchmark");
        logger.info(" Scenario:  {}", config.getScenario());
        logger.info(" Run ID:    {}", testRunId);
        logger.info(" Output:    {}", outputDir);
        logger.info("═══════════════════════════════════════════════════════════════");

        // ── Parse tenants ──
        TenantAccountInfo.TenantsConfig tenantsConfig =
            TenantAccountInfo.parseTenantsFile(new File(config.getTenantsFile()));
        List<TenantAccountInfo> tenants = tenantsConfig.getTenants();
        Map<String, String> globalDefaults = tenantsConfig.getGlobalDefaults();

        if (tenants.isEmpty()) {
            logger.error("No tenants found in {}", config.getTenantsFile());
            return;
        }
        logger.info("Loaded {} tenants", tenants.size());

        // ── Set system properties ONCE from globalDefaults ──
        setGlobalSystemProperties(globalDefaults);

        // ── Resource monitor ──
        ResourceMonitor monitor = new ResourceMonitor(outputDir);
        List<ResourceMonitor.ResourceSnapshot> snapshots = new ArrayList<>();

        // ── Result sink ──
        ResultSink resultSink = createResultSink(config, outputDir);

        // ── PRE_CREATE snapshot ──
        logger.info("Capturing PRE_CREATE baseline...");
        snapshots.add(monitor.snapshot());
        if (config.shouldThreadDumpOnEvent("PRE_CREATE")) {
            monitor.captureThreadDump("PRE_CREATE");
        }
        if (config.shouldHeapDumpOnEvent("PRE_CREATE")) {
            monitor.captureHeapDump("PRE_CREATE");
        }

        // ── Create benchmark instances ──
        logger.info("Creating {} benchmark instances...", tenants.size());
        List<AsyncBenchmark<?>> benchmarks = new ArrayList<>();
        for (TenantAccountInfo tenant : tenants) {
            Configuration tenantConfig = buildTenantConfiguration(tenant, globalDefaults);
            AsyncBenchmark<?> benchmark = createBenchmarkForOperation(tenantConfig);
            benchmarks.add(benchmark);
            logger.info("  Created benchmark for tenant: {} (endpoint: {})",
                tenant.getId(), tenant.getServiceEndpoint());
        }

        // ── POST_CREATE snapshot ──
        logger.info("All benchmarks created. Settling for 5 seconds...");
        Thread.sleep(5000);
        System.gc();
        Thread.sleep(2000);
        snapshots.add(monitor.snapshot());
        if (config.shouldThreadDumpOnEvent("POST_CREATE")) {
            monitor.captureThreadDump("POST_CREATE");
        }
        if (config.shouldHeapDumpOnEvent("POST_CREATE")) {
            monitor.captureHeapDump("POST_CREATE");
        }

        // ── Start periodic monitoring ──
        ScheduledExecutorService monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "resource-monitor");
            t.setDaemon(true);
            return t;
        });
        monitorExecutor.scheduleAtFixedRate(() -> {
            try {
                snapshots.add(monitor.snapshot());
            } catch (Exception e) {
                logger.error("Monitor snapshot failed", e);
            }
        }, config.getMonitorIntervalSec(), config.getMonitorIntervalSec(), TimeUnit.SECONDS);

        // ── Periodic thread dumps ──
        ScheduledExecutorService threadDumpExecutor = null;
        if (config.getThreadDumpIntervalSec() > 0) {
            threadDumpExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "thread-dump-scheduler");
                t.setDaemon(true);
                return t;
            });
            threadDumpExecutor.scheduleAtFixedRate(
                () -> monitor.captureThreadDump("periodic"),
                config.getThreadDumpIntervalSec(),
                config.getThreadDumpIntervalSec(),
                TimeUnit.SECONDS);
        }

        // ── Run all benchmarks concurrently ──
        if (!config.isSkipWarmup()) {
            logger.info("Starting {} benchmarks concurrently...", benchmarks.size());
        } else {
            logger.info("Starting {} benchmarks (warmup skipped)...", benchmarks.size());
        }

        long startTime = System.currentTimeMillis();
        ExecutorService tenantExecutor = Executors.newFixedThreadPool(benchmarks.size(), r -> {
            Thread t = new Thread(r, "tenant-worker");
            t.setDaemon(false);
            return t;
        });

        List<Future<?>> futures = new ArrayList<>();
        for (AsyncBenchmark<?> benchmark : benchmarks) {
            futures.add(tenantExecutor.submit(() -> {
                try {
                    benchmark.run();
                } catch (Exception e) {
                    logger.error("Benchmark failed for tenant", e);
                }
            }));
        }

        // Wait for all to finish
        for (Future<?> f : futures) {
            f.get();
        }
        long endTime = System.currentTimeMillis();
        long durationSec = (endTime - startTime) / 1000;

        // ── POST_WORKLOAD snapshot ──
        logger.info("All benchmarks completed in {} seconds.", durationSec);
        System.gc();
        Thread.sleep(2000);
        snapshots.add(monitor.snapshot());
        if (config.shouldThreadDumpOnEvent("POST_WORKLOAD")) {
            monitor.captureThreadDump("POST_WORKLOAD");
        }
        if (config.shouldHeapDumpOnEvent("POST_WORKLOAD")) {
            monitor.captureHeapDump("POST_WORKLOAD");
        }

        // ── Shutdown ──
        monitorExecutor.shutdown();
        if (threadDumpExecutor != null) {
            threadDumpExecutor.shutdown();
        }
        tenantExecutor.shutdown();

        logger.info("Shutting down benchmarks...");
        for (AsyncBenchmark<?> benchmark : benchmarks) {
            try {
                benchmark.shutdown();
            } catch (Exception e) {
                logger.error("Shutdown failed for benchmark", e);
            }
        }

        // ── POST_CLOSE snapshot ──
        Thread.sleep(5000);
        System.gc();
        Thread.sleep(2000);
        snapshots.add(monitor.snapshot());
        if (config.shouldThreadDumpOnEvent("POST_CLOSE")) {
            monitor.captureThreadDump("POST_CLOSE");
        }
        if (config.shouldHeapDumpOnEvent("POST_CLOSE")) {
            monitor.captureHeapDump("POST_CLOSE");
        }

        // ── Write results ──
        resultSink.uploadSnapshots(testRunId, snapshots);
        resultSink.close();

        // ── Summary ──
        printSummary(config, tenants.size(), durationSec, snapshots);
    }

    private Configuration buildTenantConfiguration(TenantAccountInfo tenant,
                                                    Map<String, String> globalDefaults) {
        // Start from CLI defaults, then overlay globalDefaults, then tenant-specifics
        Configuration cfg = new Configuration();
        cfg.tryGetValuesFromSystem();

        // Apply globalDefaults
        applyOverrides(cfg, globalDefaults);

        // Apply tenant-specific fields
        cfg.setServiceEndpoint(tenant.getServiceEndpoint());
        if (tenant.getMasterKey() != null) {
            cfg.setMasterKey(tenant.getMasterKey());
        }
        cfg.setDatabaseId(tenant.getDatabaseId());
        cfg.setCollectionId(tenant.getContainerId());

        // Apply tenant overrides
        applyOverrides(cfg, tenant.getOverrides());

        // Multi-tenancy flags
        cfg.setSkipSystemPropertyInit(true);
        cfg.setSuppressReporter(true);

        // Per-tenant applicationName for userAgentSuffix
        String baseName = cfg.getApplicationName();
        String tenantSuffix = StringUtils.isNotEmpty(baseName)
            ? baseName + "-" + tenant.getId()
            : "mt-bench-" + tenant.getId();
        cfg.setApplicationName(tenantSuffix);

        return cfg;
    }

    private void applyOverrides(Configuration cfg, Map<String, String> overrides) {
        if (overrides == null) return;

        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            try {
                switch (key) {
                    // Identity / endpoint
                    case "serviceEndpoint": cfg.setServiceEndpoint(value); break;
                    case "masterKey": cfg.setMasterKey(value); break;
                    case "databaseId": cfg.setDatabaseId(value); break;
                    case "collectionId":
                    case "containerId": cfg.setCollectionId(value); break;
                    case "applicationName": cfg.setApplicationName(value); break;

                    // AAD
                    case "aadLoginEndpoint": cfg.setAadLoginEndpoint(value); break;
                    case "aadTenantId": cfg.setAadTenantId(value); break;
                    case "aadManagedIdentityClientId": cfg.setAadManagedIdentityClientId(value); break;

                    // Workload
                    case "operation": cfg.setOperationFromString(value); break;
                    case "concurrency": cfg.setConcurrency(Integer.parseInt(value)); break;
                    case "numberOfOperations": cfg.setNumberOfOperations(Integer.parseInt(value)); break;
                    case "numberOfPreCreatedDocuments": cfg.setNumberOfPreCreatedDocuments(Integer.parseInt(value)); break;
                    case "skipWarmUpOperations": cfg.setSkipWarmUpOperations(Integer.parseInt(value)); break;
                    case "throughput": cfg.setThroughput(Integer.parseInt(value)); break;

                    // Connection
                    case "connectionMode":
                        cfg.setConnectionMode(com.azure.cosmos.ConnectionMode.valueOf(value.toUpperCase()));
                        break;
                    case "consistencyLevel":
                        cfg.setConsistencyLevel(com.azure.cosmos.ConsistencyLevel.valueOf(value.toUpperCase()));
                        break;
                    case "maxConnectionPoolSize": cfg.setMaxConnectionPoolSize(Integer.parseInt(value)); break;
                    case "preferredRegionsList": cfg.setPreferredRegionsList(value); break;
                    case "manageDatabase": cfg.setManageDatabase(Boolean.parseBoolean(value)); break;

                    // Flags that are string-backed booleans in Configuration
                    case "connectionSharingAcrossClientsEnabled":
                    case "http2Enabled":
                    case "isManagedIdentityRequired":
                    case "isPartitionLevelCircuitBreakerEnabled":
                    case "isPerPartitionAutomaticFailoverRequired":
                    case "contentResponseOnWriteEnabled":
                    case "defaultLog4jLoggerEnabled":
                        // These are string fields in Configuration set via JCommander.
                        // For now, log them — they need individual setters if we want to override.
                        logger.debug("String-flag override '{}' = '{}' (applied via JCommander defaults)", key, value);
                        break;

                    default:
                        logger.debug("Override key '{}' not mapped to Configuration setter (value: {})", key, value);
                        break;
                }
            } catch (Exception e) {
                logger.warn("Failed to apply override '{}' = '{}': {}", key, value, e.getMessage());
            }
        }
    }

    private AsyncBenchmark<?> createBenchmarkForOperation(Configuration cfg) {
        switch (cfg.getOperationType()) {
            case ReadThroughput:
            case ReadLatency:
                return new AsyncReadBenchmark(cfg);
            case WriteThroughput:
            case WriteLatency:
                return new AsyncWriteBenchmark(cfg);
            case QueryCross:
            case QuerySingle:
            case QueryParallel:
            case QueryOrderby:
            case QueryAggregate:
            case QueryTopOrderby:
            case QueryAggregateTopOrderby:
            case QueryInClauseParallel:
            case ReadAllItemsOfLogicalPartition:
                return new AsyncQueryBenchmark(cfg);
            case ReadManyLatency:
            case ReadManyThroughput:
                return new AsyncReadManyBenchmark(cfg);
            case Mixed:
                return new AsyncMixedBenchmark(cfg);
            case QuerySingleMany:
                return new AsyncQuerySinglePartitionMultiple(cfg);
            case ReadMyWrites:
                return new ReadMyWriteWorkflow(cfg);
            default:
                throw new IllegalArgumentException("Unsupported operation: " + cfg.getOperationType());
        }
    }

    private void setGlobalSystemProperties(Map<String, String> globalDefaults) {
        // Only set the COSMOS.* system properties that AsyncBenchmark normally sets.
        // These are JVM-global, so set them once here.
        String circuitBreakerEnabled = globalDefaults.getOrDefault(
            "isPartitionLevelCircuitBreakerEnabled", "true");
        if (Boolean.parseBoolean(circuitBreakerEnabled)) {
            System.setProperty("COSMOS.PARTITION_LEVEL_CIRCUIT_BREAKER_CONFIG",
                "{\"isPartitionLevelCircuitBreakerEnabled\": true, "
                    + "\"circuitBreakerType\": \"CONSECUTIVE_EXCEPTION_COUNT_BASED\","
                    + "\"consecutiveExceptionCountToleratedForReads\": 10,"
                    + "\"consecutiveExceptionCountToleratedForWrites\": 5}");
            System.setProperty("COSMOS.STALE_PARTITION_UNAVAILABILITY_REFRESH_INTERVAL_IN_SECONDS", "60");
            System.setProperty("COSMOS.ALLOWED_PARTITION_UNAVAILABILITY_DURATION_IN_SECONDS", "30");
        }

        String ppafEnabled = globalDefaults.getOrDefault(
            "isPerPartitionAutomaticFailoverRequired", "true");
        if (Boolean.parseBoolean(ppafEnabled)) {
            System.setProperty("COSMOS.IS_PER_PARTITION_AUTOMATIC_FAILOVER_ENABLED", "true");
            System.setProperty("COSMOS.IS_SESSION_TOKEN_FALSE_PROGRESS_MERGE_ENABLED", "true");
            System.setProperty("COSMOS.E2E_TIMEOUT_ERROR_HIT_THRESHOLD_FOR_PPAF", "5");
            System.setProperty("COSMOS.E2E_TIMEOUT_ERROR_HIT_TIME_WINDOW_IN_SECONDS_FOR_PPAF", "120");
        }

        logger.info("Global system properties set (circuit breaker: {}, PPAF: {})",
            circuitBreakerEnabled, ppafEnabled);
    }

    private ResultSink createResultSink(MultiTenancyConfig config, Path outputDir) {
        // Phase 1: always CSV. Cosmos/Kusto sinks can be added later.
        return new CsvResultSink(outputDir);
    }

    private void printSummary(MultiTenancyConfig config, int numTenants,
                              long durationSec, List<ResourceMonitor.ResourceSnapshot> snapshots) {
        if (snapshots.size() < 2) return;

        ResourceMonitor.ResourceSnapshot baseline = snapshots.get(0);     // PRE_CREATE
        ResourceMonitor.ResourceSnapshot peak = snapshots.stream()
            .max((a, b) -> Long.compare(a.usedHeapBytes, b.usedHeapBytes))
            .orElse(baseline);
        ResourceMonitor.ResourceSnapshot afterClose = snapshots.get(snapshots.size() - 1);  // POST_CLOSE

        long heapPerClientMB = numTenants > 0
            ? (peak.usedHeapBytes - baseline.usedHeapBytes) / numTenants / (1024 * 1024)
            : 0;

        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info(" Multi-Tenancy Baseline Report");
        logger.info(" Scenario:     {}", config.getScenario());
        logger.info(" Clients:      {}", numTenants);
        logger.info(" Duration:     {}s", durationSec);
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info(" HEAP:         Baseline={}MB  Peak={}MB  Per-client={}MB",
            baseline.usedHeapBytes / (1024 * 1024),
            peak.usedHeapBytes / (1024 * 1024),
            heapPerClientMB);
        logger.info(" DIRECT MEM:   Peak={}MB", peak.nettyDirectMemBytes / (1024 * 1024));
        logger.info(" THREADS:      Baseline={}  Peak={}  Per-client={:.1f}",
            baseline.liveThreadCount, peak.liveThreadCount,
            numTenants > 0
                ? (double)(peak.liveThreadCount - baseline.liveThreadCount) / numTenants
                : 0.0);
        logger.info(" FILE DESCS:   Peak={}", peak.openFileDescriptors);
        logger.info(" GC PAUSES:    Count={}  Total={}ms",
            afterClose.gcCount, afterClose.gcTimeMs);
        logger.info("─────────────────────────────────────────────────────────────");
        logger.info(" LEAK CHECK:   Heap after close: {}MB (delta from baseline: {}MB)",
            afterClose.usedHeapBytes / (1024 * 1024),
            (afterClose.usedHeapBytes - baseline.usedHeapBytes) / (1024 * 1024));
        logger.info("               Threads after close: {} (delta: {})",
            afterClose.liveThreadCount,
            afterClose.liveThreadCount - baseline.liveThreadCount);
        logger.info("═══════════════════════════════════════════════════════════════");
    }
}
