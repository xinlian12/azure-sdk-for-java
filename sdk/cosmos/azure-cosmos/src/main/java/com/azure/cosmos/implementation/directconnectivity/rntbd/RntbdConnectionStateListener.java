// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.directconnectivity.rntbd;

import com.azure.cosmos.implementation.ReplicaReconfigurationException;
import com.azure.cosmos.implementation.RxDocumentServiceRequest;
import com.azure.cosmos.implementation.directconnectivity.IAddressResolver;
import com.azure.cosmos.implementation.routing.PartitionKeyRangeIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.azure.cosmos.implementation.guava25.base.Preconditions.checkNotNull;

public class RntbdConnectionStateListener {

    // region Fields

    private static final Logger logger = LoggerFactory.getLogger(RntbdConnectionStateListener.class);

    private final IAddressResolver addressResolver;
    private final RntbdEndpoint endpoint;
    private final Set<PartitionKeyRangeIdentity> partitionAddressCache;
    private final AtomicBoolean updatingAddressCache = new AtomicBoolean(false);

    // endregion

    // region Constructors

    public RntbdConnectionStateListener(final IAddressResolver addressResolver, final RntbdEndpoint endpoint) {
        this.addressResolver = checkNotNull(addressResolver, "expected non-null addressResolver");
        this.endpoint = checkNotNull(endpoint, "expected non-null endpoint");
        this.partitionAddressCache = ConcurrentHashMap.newKeySet();
    }

    // endregion

    // region Methods

    public void onException(final RxDocumentServiceRequest request, Throwable exception) {
        checkNotNull(request, "expect non-null request");
        checkNotNull(exception, "expect non-null error");

        if (exception == null) {
            return;
        }
        if (exception instanceof ReplicaReconfigurationException) {
            logger.warn(
                "dropping connection to {} because the service is being discontinued or reconfigured {}",
                endpoint.remoteURI(),
                request.getActivityId());
//
//<<<<<<< Updated upstream
//        // TODO : Annie: Or should we just check WebExceptionUtility.isNetworkFailure(exception)?
//        // ConnectionTimeout exception does not necessary mean the server is in upgrade, should we aggressively remove the address?
//        if (exception instanceof GoneException) {
//            final Throwable cause = exception.getCause();
//            if (cause != null) {
//                // GoneException was produced by the client, not the server
//                //
//                // This could occur when:
//                //
//                // * an operation fails due to an IOException which indicates a connection reset by the server,
//                // * a channel closes unexpectedly because the server stopped taking requests, or
//                // * an error was detected by the transport client (e.g., IllegalStateException)
//                // * a request timed out in pending acquisition queue
//                // * a request failed fast in admission control layer due to high load
//                //
//                // We only consider the following scenario which might relates to replica movement.
//                final Class<?> type = cause.getClass();
//
//                if (type == ClosedChannelException.class) {
//                    this.onConnectionEvent(RntbdConnectionEvent.READ_EOF, request, cause);
//                } else if (type == ConnectTimeoutException.class || type == IOException.class) {
//                    this.onConnectionEvent(RntbdConnectionEvent.READ_FAILURE, request, cause); // aggressive
//                } else{
//                    return;
//                }
//
//                logger.warn("connection to {} {} lost caused by {}", request.getActivityId(), endpoint.remoteURI(), cause);
//            }
//=======
            this.onConnectionEvent(request, exception);
        }
    }

    public void updateConnectionState(final RxDocumentServiceRequest request) {

        checkNotNull("expect non-null request");

        PartitionKeyRangeIdentity partitionKeyRangeIdentity = this.getPartitionKeyRangeIdentity(request);
        checkNotNull(partitionKeyRangeIdentity, "expected non-null partitionKeyRangeIdentity");

        this.partitionAddressCache.add(partitionKeyRangeIdentity);

        if (logger.isDebugEnabled()) {
            logger.debug(
                "updateConnectionState({\"time\":{},\"endpoint\":{},\"partitionKeyRangeIdentity\":{}})",
                RntbdObjectMapper.toJson(Instant.now()),
                RntbdObjectMapper.toJson(endpoint),
                RntbdObjectMapper.toJson(partitionKeyRangeIdentity));
        }
    }

    // endregion

    // region Privates

    private PartitionKeyRangeIdentity getPartitionKeyRangeIdentity(final RxDocumentServiceRequest request) {
        checkNotNull(request, "expect non-null request");

        PartitionKeyRangeIdentity partitionKeyRangeIdentity = request.getPartitionKeyRangeIdentity();

        if (partitionKeyRangeIdentity == null) {

            final String partitionKeyRange = checkNotNull(
                request.requestContext.resolvedPartitionKeyRange, "expected non-null resolvedPartitionKeyRange").getId();

            final String collectionRid = request.requestContext.resolvedCollectionRid;

            partitionKeyRangeIdentity = collectionRid != null
                ? new PartitionKeyRangeIdentity(collectionRid, partitionKeyRange)
                : new PartitionKeyRangeIdentity(partitionKeyRange);
        }

        return partitionKeyRangeIdentity;
    }

    private void onConnectionEvent(final RxDocumentServiceRequest request, final Throwable exception) {

        checkNotNull(request, "expected non-null exception");
        checkNotNull(exception, "expected non-null exception");

        if (!this.endpoint.isClosed()) {

            if (logger.isDebugEnabled()) {
                logger.debug("onConnectionEvent({\"time\":{},\"endpoint\":{},\"cause\":{})",
                    RntbdObjectMapper.toJson(Instant.now()),
                    RntbdObjectMapper.toJson(this.endpoint),
                    RntbdObjectMapper.toJson(exception));
            }

            this.updateAddressCache(request);
        }
    }

    private void updateAddressCache(final RxDocumentServiceRequest request) {
        try{
            if (this.updatingAddressCache.compareAndSet(false, true)) {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                        "updateAddressCache ({\"time\":{},\"endpoint\":{},\"partitionAddressCache\":{}})",
                        RntbdObjectMapper.toJson(Instant.now()),
                        RntbdObjectMapper.toJson(this.endpoint),
                        RntbdObjectMapper.toJson(this.partitionAddressCache));
                }

                // TODO: should we remove address? What if gateway has issue? Cache change about reusing old value will not work.
                // TODO : Annie: Should we close channel/close endpoint here?
                // this.addressResolver.remove(request, this.endpoint.remoteURI(), this.partitionAddressCache);
                this.addressResolver.expire(request, this.partitionAddressCache);
                this.partitionAddressCache.clear();
            }
        } finally {
            this.updatingAddressCache.set(false);
        }
    }
    // endregion
}
