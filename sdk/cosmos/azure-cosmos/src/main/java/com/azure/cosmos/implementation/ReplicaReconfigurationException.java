// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation;

import com.azure.cosmos.BridgeInternal;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.implementation.directconnectivity.WFConstants;

import java.util.Map;

/**
 * While this class is public, but it is not part of our published public APIs.
 * This is meant to be internally used only by our sdk.
 */
public class ReplicaReconfigurationException extends CosmosException {
    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new replica reconfiguration exception.
     */
    public ReplicaReconfigurationException() {
        this(RMResources.Gone);
    }

    /**
     * Instantiates a new replica reconfiguration exception.
     *
     * @param cosmosError the cosmos error
     * @param lsn the lsn
     * @param partitionKeyRangeId the partition key range id
     * @param responseHeaders the response headers
     */
    public ReplicaReconfigurationException(CosmosError cosmosError, long lsn, String partitionKeyRangeId,
                                                 Map<String, String> responseHeaders) {
        super(HttpConstants.StatusCodes.GONE, cosmosError, responseHeaders);
        BridgeInternal.setLSN(this, lsn);
        BridgeInternal.setPartitionKeyRangeId(this, partitionKeyRangeId);
    }

    public ReplicaReconfigurationException(
        CosmosError cosmosError,
        long lsn,
        String partitionKeyRangeId,
        String resourceAddress,
        Map<String, String> responseHeaders) {

        super(HttpConstants.StatusCodes.GONE, cosmosError, responseHeaders);
        BridgeInternal.setLSN(this, lsn);
        BridgeInternal.setPartitionKeyRangeId(this, partitionKeyRangeId);
        BridgeInternal.setResourceAddress(this, resourceAddress);
    }

    ReplicaReconfigurationException(String msg) {
        super(HttpConstants.StatusCodes.GONE, msg);
        setSubStatus();
    }

    ReplicaReconfigurationException(String msg, String resourceAddress) {
        super(msg, null, null, HttpConstants.StatusCodes.GONE, resourceAddress);
        setSubStatus();
    }

    /**
     * Instantiates a new replica reconfiguration exception.
     *
     * @param message the message
     * @param headers the headers
     * @param requestUri the request uri
     */
    public ReplicaReconfigurationException(String message, Map<String, String> headers, String requestUri) {
        this(message, null, headers, requestUri);
    }

    public ReplicaReconfigurationException(Exception innerException) {
        this(RMResources.Gone, innerException, null, null);
    }

    public ReplicaReconfigurationException(String message,
                                          Exception innerException,
                                          Map<String, String> headers,
                                          String requestUri) {
        super(String.format("%s: %s", RMResources.Gone, message),
            innerException,
            headers,
            HttpConstants.StatusCodes.GONE,
            requestUri);

        setSubStatus();
    }

    private void setSubStatus() {
        this.getResponseHeaders().put(
            WFConstants.BackendHeaders.SUB_STATUS,
            Integer.toString(HttpConstants.SubStatusCodes.REPLICA_RECONFIGURATION));
    }
}
