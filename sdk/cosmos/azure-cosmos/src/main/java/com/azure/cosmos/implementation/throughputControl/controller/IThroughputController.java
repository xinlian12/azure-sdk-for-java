// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.throughputControl.controller;

import com.azure.cosmos.implementation.RxDocumentServiceRequest;
import reactor.core.publisher.Mono;

public interface IThroughputController {
    Mono<Void> close();
    <T> Mono<T> processRequest(RxDocumentServiceRequest request, Mono<T> nextRequestMono);
}
