// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.benchmark;

import java.util.List;

/**
 * Interface for writing benchmark results to external storage.
 * Implementations: CsvResultSink, CosmosResultSink, KustoResultSink, CompositeResultSink.
 * See §9.5 of the test plan.
 */
public interface ResultSink {

    /**
     * Upload periodic resource snapshots collected during the run.
     *
     * @param testRunId unique identifier for this test run
     * @param snapshots list of periodic resource snapshots
     */
    void uploadSnapshots(String testRunId, List<ResourceMonitor.ResourceSnapshot> snapshots);

    /**
     * Flush and close any underlying connections.
     */
    void close();
}
