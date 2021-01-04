// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.throughputControl.controller;

import com.azure.cosmos.implementation.RxDocumentServiceRequest;
import reactor.core.publisher.Mono;

public interface ThroughputRequestController extends IThroughputController {
    Mono<Void> resetThroughput(double throughput);
    boolean canHandleRequest(RxDocumentServiceRequest request);
    Mono<ThroughputRequestController> init(double scheduledThroughput);
    Mono<Double> calculateLoadFactor();
}
