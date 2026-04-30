// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.benchmark;

import org.slf4j.Logger;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

/**
 * Common contract for all benchmark workloads.
 * Implementations are created by {@link BenchmarkOrchestrator} and participate
 * in its lifecycle loop (create → run → shutdown → settle × N cycles).
 *
 * <p>Workloads that support per-operation dispatch by the orchestrator should
 * implement {@link #performSingleOperation(long)}. The orchestrator calls this
 * method for each operation slot, passing a <em>tenant-local</em> operation index.</p>
 *
 * <p>Workloads with complex lifecycles (e.g. {@code LICtlWorkload}) that cannot
 * be dispatched per-operation should override {@link #isDispatchable()} to return
 * {@code false}; the orchestrator will call {@link #run()} directly for those.</p>
 */
public interface Benchmark {
    /**
     * Run the full benchmark workload. For dispatchable benchmarks, this is the legacy
     * execution path retained for standalone / non-orchestrator usage. The orchestrator
     * uses {@link #performSingleOperation(long)} instead.
     */
    void run() throws Exception;
    void shutdown();

    /**
     * Execute a single operation for this benchmark. The orchestrator calls this
     * when randomly selecting this tenant for an operation slot.
     *
     * @param operationIndex tenant-local operation index (0-based, monotonically increasing per tenant)
     * @return a Mono that completes when the operation finishes
     */
    default Mono<?> performSingleOperation(long operationIndex) {
        return Mono.error(new UnsupportedOperationException(
            getClass().getSimpleName() + " does not support per-operation dispatch"));
    }

    /**
     * Whether this benchmark supports per-operation dispatch from the orchestrator.
     * Non-dispatchable benchmarks are run via {@link #run()} in their own thread.
     */
    default boolean isDispatchable() {
        return false;
    }

    /**
     * Wraps a dispatch Mono with standard success/error observability callbacks.
     * Centralizes the try-catch error-logging pattern used by AsyncBenchmark,
     * AsyncEncryptionBenchmark, and SyncBenchmark to avoid copy-paste drift.
     *
     * @param source     the operation Mono (already subscribeOn'd by the caller)
     * @param onSuccess  callback invoked on success (e.g. metrics increment)
     * @param onError    callback invoked on error (e.g. metrics increment)
     * @param logger     the caller's logger for error messages
     * @param <T>        the Mono element type
     * @return the wrapped Mono
     */
    static <T> Mono<T> wrapDispatchCallbacks(Mono<T> source, Runnable onSuccess,
                                              Consumer<Throwable> onError, Logger logger) {
        return source
            .doOnSuccess(v -> onSuccess.run())
            .doOnError(e -> {
                try {
                    logger.error("Encountered failure {} on thread {}",
                        e.getMessage(), Thread.currentThread().getName(), e);
                    onError.accept(e);
                } catch (Exception handlerEx) {
                    logger.error("onError handler threw for original error: {}", e.getMessage(), handlerEx);
                }
            });
    }
}
