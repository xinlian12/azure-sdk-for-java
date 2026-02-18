// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.benchmark;

import java.util.Arrays;
import java.util.List;

/**
 * Delegates to multiple {@link ResultSink} implementations.
 * Used for {@code --resultSink ALL} to write to CSV + Cosmos + Kusto simultaneously.
 */
public class CompositeResultSink implements ResultSink {

    private final List<ResultSink> sinks;

    public CompositeResultSink(ResultSink... sinks) {
        this.sinks = Arrays.asList(sinks);
    }

    @Override
    public void uploadSnapshots(String testRunId, List<ResourceMonitor.ResourceSnapshot> snapshots) {
        for (ResultSink sink : sinks) {
            sink.uploadSnapshots(testRunId, snapshots);
        }
    }

    @Override
    public void close() {
        for (ResultSink sink : sinks) {
            sink.close();
        }
    }
}
