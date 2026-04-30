// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.benchmark;

import reactor.core.publisher.Mono;

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
        return true;
    }
}
