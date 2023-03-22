package com.azure.cosmos.implementation.throughputControl.config;

import com.azure.cosmos.CosmosAsyncContainer;

import java.util.concurrent.Callable;

public class GlobalThroughputControlSimpleGroup extends ThroughputControlGroupInternal {
    private final Callable<Integer> instanceCountCallable;

    public GlobalThroughputControlSimpleGroup(
        String groupName,
        CosmosAsyncContainer targetContainer,
        Integer targetThroughput,
        Double targetThroughputThreshold,
        boolean isDefault,
        boolean continueOnInitError,
        Callable<Integer> instanceCountCallable) {

        super(groupName, targetContainer, targetThroughput, targetThroughputThreshold, isDefault, continueOnInitError);
        this.instanceCountCallable = instanceCountCallable;
    }

    public Callable<Integer> getInstanceCountCallable() {
        return this.instanceCountCallable;
    }
}
