// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.throughputControl.controller.group;

import com.azure.cosmos.ConnectionMode;
import com.azure.cosmos.implementation.caches.RxPartitionKeyRangeCache;
import com.azure.cosmos.implementation.changefeed.CancellationToken;
import com.azure.cosmos.implementation.throughputControl.ThroughputBudgetGroupConfigInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

public class ThroughputGroupLocalController extends ThroughputGroupControllerBase {
    private static final Logger logger = LoggerFactory.getLogger(ThroughputGroupLocalController.class);
    public ThroughputGroupLocalController(
        ConnectionMode connectionMode,
        ThroughputBudgetGroupConfigInternal group,
        Integer maxContainerThroughput,
        RxPartitionKeyRangeCache partitionKeyRangeCache) {

        super(connectionMode, group, maxContainerThroughput, partitionKeyRangeCache);
    }

    @Override
    Flux<Void> calculateThroughputTask(CancellationToken cancellationToken) {
        return Flux.empty();
    }
}
