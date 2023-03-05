// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.faultinjection.model;

import java.time.Duration;

import static com.azure.cosmos.implementation.guava25.base.Preconditions.checkNotNull;

public class FaultInjectionConnectionErrorResultInternal {
    private final FaultInjectionConnectionErrorTypeInternal errorType;
    private Duration interval;
    private double threshold;

    public FaultInjectionConnectionErrorResultInternal(
        FaultInjectionConnectionErrorTypeInternal errorType,
        Duration interval,
        double threshold) {

        checkNotNull(errorType, "Argument 'errorType' can not be null");

        this.errorType = errorType;
        this.interval = interval;
        this.threshold = threshold;
    }

    public FaultInjectionConnectionErrorTypeInternal getErrorType() {
        return errorType;
    }

    public Duration getInterval() {
        return interval;
    }

    public double getThreshold() {
        return threshold;
    }
}
