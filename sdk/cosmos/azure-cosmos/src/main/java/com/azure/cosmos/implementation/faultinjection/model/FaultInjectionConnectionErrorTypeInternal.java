package com.azure.cosmos.implementation.faultinjection.model;

public enum FaultInjectionConnectionErrorTypeInternal {
    /***
     * Simulate connection close exception.
     */
    CONNECTION_CLOSE,

    /***
     * Simulate connection reset exception.
     */
    CONNECTION_RESET
}
