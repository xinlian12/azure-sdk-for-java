// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.throughputControl.controller.group;

import com.azure.cosmos.ConnectionMode;
import com.azure.cosmos.ThroughputControlGroupConfig;
import com.azure.cosmos.ThroughputBudgetGroupControlMode;
import com.azure.cosmos.implementation.caches.RxPartitionKeyRangeCache;
import com.azure.cosmos.implementation.throughputControl.ThroughputBudgetGroupConfigInternal;

public class ThroughputBudgetGroupControllerFactory {

    public static ThroughputGroupControllerBase createController(
        ConnectionMode connectionMode,
        ThroughputControlGroupConfig groupConfig,
        Integer maxContainerThroughput,
        RxPartitionKeyRangeCache partitionKeyRangeCache,
        String targetCollectionRid) {

        ThroughputBudgetGroupConfigInternal groupConfigInternal = new ThroughputBudgetGroupConfigInternal(groupConfig, targetCollectionRid);

        if (groupConfig.getControlMode() == ThroughputBudgetGroupControlMode.LOCAL) {
            return new ThroughputGroupLocalController(
                connectionMode,
                groupConfigInternal,
                maxContainerThroughput,
                partitionKeyRangeCache);
        } else if (groupConfig.getControlMode() == ThroughputBudgetGroupControlMode.DISTRIBUTED) {
            return new ThroughputGroupDistributedController(
                connectionMode,
                groupConfigInternal,
                maxContainerThroughput,
                partitionKeyRangeCache);
        }

        throw new IllegalArgumentException(String.format("Throughput budget group control mode %s is not supported", groupConfig.getControlMode()));
    }
}
