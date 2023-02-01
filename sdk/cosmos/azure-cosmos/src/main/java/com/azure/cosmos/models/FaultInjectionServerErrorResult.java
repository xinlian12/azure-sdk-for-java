package com.azure.cosmos.models;

public class FaultInjectionServerErrorResult implements FaultInjectionResult {
    private final ServerErrorTypes serverErrorTypes;
    private final int times;

    public FaultInjectionServerErrorResult(ServerErrorTypes serverErrorTypes) {
        this.serverErrorTypes = serverErrorTypes;
        this.times = Integer.MAX_VALUE;
    }

    public FaultInjectionServerErrorResult(ServerErrorTypes serverErrorTypes, int times) {
        this.serverErrorTypes = serverErrorTypes;
        this.times = times;
    }
}
