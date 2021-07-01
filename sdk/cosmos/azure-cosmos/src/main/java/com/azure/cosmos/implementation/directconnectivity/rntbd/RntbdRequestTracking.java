package com.azure.cosmos.implementation.directconnectivity.rntbd;

import java.util.concurrent.atomic.AtomicInteger;

public class RntbdRequestTracking {

    private static final AtomicInteger totalRequests = new AtomicInteger(0);

    public static int trackRequest() {
        return totalRequests.incrementAndGet();
    }
}
