// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collects JVM and OS-level resource metrics for multi-tenancy benchmarks.
 * See §4.7 of the test plan for the full metric inventory.
 */
public class ResourceMonitor {

    private static final Logger logger = LoggerFactory.getLogger(ResourceMonitor.class);

    private final Path outputDir;
    private final ThreadMXBean threadMXBean;
    private final MemoryMXBean memoryMXBean;
    private final OperatingSystemMXBean osMXBean;
    private final List<GarbageCollectorMXBean> gcBeans;

    public ResourceMonitor(Path outputDir) {
        this.outputDir = outputDir;
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.osMXBean = ManagementFactory.getOperatingSystemMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    }

    /**
     * Capture a point-in-time snapshot of all JVM/OS resource metrics.
     */
    public ResourceSnapshot snapshot() {
        ResourceSnapshot s = new ResourceSnapshot();
        s.timestamp = Instant.now();

        // Heap
        Runtime rt = Runtime.getRuntime();
        s.usedHeapBytes = rt.totalMemory() - rt.freeMemory();
        s.maxHeapBytes = rt.maxMemory();

        // Direct memory — try Netty PlatformDependent, fall back gracefully
        s.nettyDirectMemBytes = getNettyDirectMemory();
        s.jdkDirectPoolBytes = getBufferPoolMemory("direct");
        s.jdkMappedPoolBytes = getBufferPoolMemory("mapped");

        // Threads
        s.liveThreadCount = threadMXBean.getThreadCount();
        s.daemonThreadCount = threadMXBean.getDaemonThreadCount();
        s.threadsByPrefix = getThreadsByPrefix();

        // CPU
        s.processCpuLoad = getProcessCpuLoad();
        s.systemCpuLoad = getSystemCpuLoad();

        // GC
        long totalGcCount = 0;
        long totalGcTimeMs = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            totalGcCount += gc.getCollectionCount();
            totalGcTimeMs += gc.getCollectionTime();
        }
        s.gcCount = totalGcCount;
        s.gcTimeMs = totalGcTimeMs;

        // File descriptors (Linux only)
        s.openFileDescriptors = getOpenFileDescriptors();
        s.maxFileDescriptors = getMaxFileDescriptors();

        return s;
    }

    /**
     * Capture a thread dump to the output directory.
     * Uses ThreadMXBean for in-process capture (no external tools needed).
     */
    public void captureThreadDump(String label) {
        try {
            Path dumpDir = outputDir.resolve("thread-dumps");
            Files.createDirectories(dumpDir);
            Path dumpFile = dumpDir.resolve(String.format("threads-%s-%s.txt",
                label, Instant.now().toString().replace(':', '-')));

            ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(true, true);

            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(dumpFile))) {
                writer.printf("Thread Dump [%s] at %s%n", label, Instant.now());
                writer.printf("Total threads: %d (daemon: %d)%n%n",
                    threadMXBean.getThreadCount(), threadMXBean.getDaemonThreadCount());

                for (ThreadInfo info : threadInfos) {
                    writer.println(info.toString());
                }

                // Thread-name-prefix summary
                Map<String, Long> byPrefix = Arrays.stream(threadInfos)
                    .collect(Collectors.groupingBy(
                        ti -> ti.getThreadName().replaceAll("-?\\d+$", ""),
                        Collectors.counting()));
                writer.println("=== Thread Name Prefix Summary ===");
                byPrefix.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> writer.printf("  %-50s %d%n", e.getKey(), e.getValue()));
            }

            logger.info("Thread dump captured: {}", dumpFile);
        } catch (IOException e) {
            logger.error("Failed to capture thread dump: {}", e.getMessage());
        }
    }

    /**
     * Capture a heap dump using HotSpotDiagnosticMXBean.
     */
    public void captureHeapDump(String label) {
        try {
            Path dumpDir = outputDir.resolve("heap-dumps");
            Files.createDirectories(dumpDir);
            Path dumpFile = dumpDir.resolve(String.format("heap-%s-%s.hprof",
                label, Instant.now().toString().replace(':', '-')));

            com.sun.management.HotSpotDiagnosticMXBean diagnosticMXBean =
                ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
            diagnosticMXBean.dumpHeap(dumpFile.toString(), true);

            long sizeMB = Files.size(dumpFile) / (1024 * 1024);
            logger.info("Heap dump captured: {} ({} MB)", dumpFile, sizeMB);
        } catch (Exception e) {
            logger.error("Failed to capture heap dump: {}", e.getMessage());
        }
    }

    // ── Private helpers ──

    private Map<String, Integer> getThreadsByPrefix() {
        Map<String, Integer> result = new HashMap<>();
        Thread.getAllStackTraces().keySet().forEach(t -> {
            String prefix = t.getName().replaceAll("-?\\d+$", "");
            result.merge(prefix, 1, Integer::sum);
        });
        return result;
    }

    private long getNettyDirectMemory() {
        try {
            Class<?> clazz = Class.forName("io.netty.util.internal.PlatformDependent");
            return (long) clazz.getMethod("usedDirectMemory").invoke(null);
        } catch (Exception e) {
            return -1; // Netty not available
        }
    }

    private long getBufferPoolMemory(String poolName) {
        try {
            return ManagementFactory.getPlatformMXBeans(
                java.lang.management.BufferPoolMXBean.class).stream()
                .filter(p -> poolName.equals(p.getName()))
                .mapToLong(java.lang.management.BufferPoolMXBean::getMemoryUsed)
                .findFirst()
                .orElse(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    private double getProcessCpuLoad() {
        if (osMXBean instanceof com.sun.management.OperatingSystemMXBean) {
            return ((com.sun.management.OperatingSystemMXBean) osMXBean).getProcessCpuLoad() * 100;
        }
        return -1;
    }

    private double getSystemCpuLoad() {
        if (osMXBean instanceof com.sun.management.OperatingSystemMXBean) {
            return ((com.sun.management.OperatingSystemMXBean) osMXBean).getCpuLoad() * 100;
        }
        return -1;
    }

    private long getOpenFileDescriptors() {
        if (osMXBean instanceof com.sun.management.UnixOperatingSystemMXBean) {
            return ((com.sun.management.UnixOperatingSystemMXBean) osMXBean).getOpenFileDescriptorCount();
        }
        return -1;
    }

    private long getMaxFileDescriptors() {
        if (osMXBean instanceof com.sun.management.UnixOperatingSystemMXBean) {
            return ((com.sun.management.UnixOperatingSystemMXBean) osMXBean).getMaxFileDescriptorCount();
        }
        return -1;
    }

    /**
     * Point-in-time snapshot of all JVM/OS resource metrics.
     */
    public static class ResourceSnapshot {
        public Instant timestamp;

        // Heap
        public long usedHeapBytes;
        public long maxHeapBytes;

        // Direct memory
        public long nettyDirectMemBytes;
        public long jdkDirectPoolBytes;
        public long jdkMappedPoolBytes;

        // Threads
        public int liveThreadCount;
        public int daemonThreadCount;
        public Map<String, Integer> threadsByPrefix;

        // CPU
        public double processCpuLoad;
        public double systemCpuLoad;

        // GC
        public long gcCount;
        public long gcTimeMs;

        // File descriptors
        public long openFileDescriptors;
        public long maxFileDescriptors;

        /**
         * CSV header line.
         */
        public static String csvHeader() {
            return "timestamp,usedHeapBytes,maxHeapBytes,nettyDirectMemBytes,jdkDirectPoolBytes,"
                + "liveThreadCount,daemonThreadCount,processCpuPct,systemCpuPct,"
                + "gcCount,gcTimeMs,openFDs,maxFDs";
        }

        /**
         * CSV data line.
         */
        public String toCsvLine() {
            return String.format("%s,%d,%d,%d,%d,%d,%d,%.1f,%.1f,%d,%d,%d,%d",
                timestamp, usedHeapBytes, maxHeapBytes, nettyDirectMemBytes, jdkDirectPoolBytes,
                liveThreadCount, daemonThreadCount, processCpuLoad, systemCpuLoad,
                gcCount, gcTimeMs, openFileDescriptors, maxFileDescriptors);
        }
    }
}
