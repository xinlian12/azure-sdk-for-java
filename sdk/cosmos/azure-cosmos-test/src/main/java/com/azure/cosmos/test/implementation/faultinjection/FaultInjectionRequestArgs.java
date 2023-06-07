package com.azure.cosmos.test.implementation.faultinjection;

import com.azure.cosmos.implementation.RxDocumentServiceRequest;
import com.azure.cosmos.implementation.directconnectivity.Uri;

public class FaultInjectionRequestArgs {
    private final String requestIdentifier;
    private final Uri physicalAddressUri;
    private final RxDocumentServiceRequest serviceRequest;

    public FaultInjectionRequestArgs(
        String requestIdentifier,
        RxDocumentServiceRequest request,
        Uri physicalAddressUri) {
        this.requestIdentifier = requestIdentifier;
        this.serviceRequest = request;
        this.physicalAddressUri = physicalAddressUri;
    }

    public String getRequestIdentifier() {
        return requestIdentifier;
    }

    public RxDocumentServiceRequest getServiceRequest() {
        return serviceRequest;
    }

    public Uri getPhysicalAddressUri() {
        return physicalAddressUri;
    }
}
