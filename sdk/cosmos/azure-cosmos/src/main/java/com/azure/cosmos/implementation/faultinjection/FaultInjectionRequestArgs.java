// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.faultinjection;

import com.azure.cosmos.implementation.RxDocumentServiceRequest;

import java.net.URI;
import java.util.List;

import static com.azure.cosmos.implementation.guava25.base.Preconditions.checkNotNull;

public abstract class FaultInjectionRequestArgs {
    private final long transportRequestId;
    private final String requestURIString;
    private final RxDocumentServiceRequest serviceRequest;
    private boolean isPrimary;

    public FaultInjectionRequestArgs(
        long transportRequestId,
        URI requestURI,
        boolean isPrimary,
        RxDocumentServiceRequest serviceRequest) {

        this(transportRequestId, requestURI != null ? requestURI.toString() : null, isPrimary, serviceRequest);
    }

    public FaultInjectionRequestArgs(
        long transportRequestId,
        String requestURIString,
        boolean isPrimary,
        RxDocumentServiceRequest serviceRequest) {

        checkNotNull(requestURIString, "Argument 'requestURIString' can not null");
        checkNotNull(serviceRequest, "Argument 'serviceRequest' can not be null");

        this.transportRequestId = transportRequestId;
        this.requestURIString = requestURIString;
        this.isPrimary = isPrimary;
        this.serviceRequest = serviceRequest;
    }

    public long getTransportRequestId() {
        return this.transportRequestId;
    }

    public URI getRequestURI() {
        return URI.create(this.requestURIString);
    }

    public String getRequestURIString() {
        return this.requestURIString;
    }

    public RxDocumentServiceRequest getServiceRequest() {
        return this.serviceRequest;
    }

    public boolean isPrimary() {
        return this.isPrimary;
    }

    public abstract List<String> getPartitionKeyRangeIds();
    public abstract String getCollectionRid();
}
