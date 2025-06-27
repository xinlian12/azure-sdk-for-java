// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.azure.cosmos.implementation;

import com.azure.cosmos.BridgeInternal;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.implementation.apachecommons.lang.StringUtils;
import com.azure.cosmos.implementation.caches.RxCollectionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.azure.cosmos.implementation.HttpConstants.HttpHeaders.INTENDED_COLLECTION_RID_HEADER;

/**
 * While this class is public, but it is not part of our published public APIs.
 * This is meant to be internally used only by our sdk.
 *
 */
public class StaledResourceRetryPolicy extends DocumentClientRetryPolicy {
    private static final Logger logger = LoggerFactory.getLogger(StaledResourceRetryPolicy.class);
    private final RxCollectionCache clientCollectionCache;
    private final ISessionContainer sessionContainer;
    private final DocumentClientRetryPolicy nextPolicy;
    private final String collectionLink;
    private final Map<String, Object> requestOptionProperties;
    private RxDocumentServiceRequest request;
    private final AtomicBoolean shouldSuppressRetry = new AtomicBoolean(false);

    private volatile boolean retried = false;

    public StaledResourceRetryPolicy(
            RxCollectionCache collectionCache,
            ISessionContainer sessionContainer,
            DocumentClientRetryPolicy nextPolicy,
            String resourceFullName,
            Map<String, Object> requestOptionProperties,
            Map<String, String> requestCustomHeaders) {

        this.clientCollectionCache = collectionCache;
        this.sessionContainer = sessionContainer;
        this.nextPolicy = nextPolicy;

        // TODO the resource address should be inferred from exception
        this.collectionLink = Utils.getCollectionName(resourceFullName);
        this.requestOptionProperties = requestOptionProperties;
        if (requestCustomHeaders != null) {
            this.shouldSuppressRetry.set(this.shouldSuppressRetry(requestCustomHeaders));
        }
    }

    @Override
    public void onBeforeSendRequest(RxDocumentServiceRequest request) {
        this.request = request;
        if (this.nextPolicy != null) {
            this.nextPolicy.onBeforeSendRequest(request);
        }
    }

    @Override
    public RetryContext getRetryContext() {
        if (this.nextPolicy != null) {
            return this.nextPolicy.getRetryContext();
        } else {
            return null;
        }
    }

    @Override
    public Mono<ShouldRetryResult> shouldRetry(Exception e) {
        CosmosException clientException = Utils.as(e, CosmosException.class);
        if (isServerNameCacheStaledException(clientException)
            || isGatewayIncorrectContainerException(clientException)) {
            if (!this.retried) {
                AtomicReference<String> requestCollectionRid = new AtomicReference<>();
                if (request != null && request.requestContext != null) {
                    requestCollectionRid.set(request.requestContext.resolvedCollectionRid);
                }

                return this.clientCollectionCache
                    .resolveByNameAsync(
                        getMetadataDiagnosticsContext(),
                        collectionLink,
                        requestOptionProperties)
                    .flatMap(documentCollection -> {
                        // only refresh the cache for the following three situations:
                        //  - 1. request is null
                        //  - 2. when the resolvedCollectionRid for the request is unknown
                        //  - 3. if the resolvedCollectionRid == the collectionRid in the cache
                        if (requestCollectionRid.get() == null
                            || documentCollection.getResourceId().equals(requestCollectionRid.get())) {
                            // refresh the cache
                            this.clientCollectionCache.refresh(this.getMetadataDiagnosticsContext(), this.collectionLink, this.requestOptionProperties);
                            return this.clientCollectionCache
                                .resolveByNameAsync(
                                    getMetadataDiagnosticsContext(),
                                    collectionLink,
                                    requestOptionProperties)
                                .map(DocumentCollection::getResourceId);
                        }

                        return Mono.just(documentCollection.getResourceId());
                    })
                    .flatMap(newCollectionRid -> {
                        if (!StringUtils.equals(newCollectionRid, requestCollectionRid.get())) {
                            logger.info(
                                    "Container recreate, going to clean up session container for request containerRid {}",
                                    requestCollectionRid.get());

                            if(this.request != null
                                    && StringUtils.isNotEmpty(request.getHeaders().get(INTENDED_COLLECTION_RID_HEADER))) {
                                request.getHeaders().remove(INTENDED_COLLECTION_RID_HEADER);
                            }

                            // clear the session token mapped to the old collection rid
                            this.sessionContainer.clearTokenByResourceId(requestCollectionRid.get());
                        }

                        // if customer/client passed in INTENDED_COLLECTION_RID_HEADER, then bubble error back so client can also refresh corresponding cache
                        // mechanism mostly used in encryption library
                        if (this.shouldSuppressRetry.get()) {
                            return Mono.just(ShouldRetryResult.error(e));
                        }

                        return Mono.just(ShouldRetryResult.retryAfter(Duration.ZERO));
                    });
            } else {
                return Mono.just(ShouldRetryResult.error(e));
            }
        }

        if (this.nextPolicy != null) {
            return this.nextPolicy.shouldRetry(e);
        }
        return Mono.just(ShouldRetryResult.error(e));
    }

    private MetadataDiagnosticsContext getMetadataDiagnosticsContext() {
        return request != null
            ? BridgeInternal.getMetaDataDiagnosticContext(this.request.requestContext.cosmosDiagnostics) : null;
    }

    private boolean isServerNameCacheStaledException(CosmosException cosmosException) {
        return cosmosException != null &&
            Exceptions.isStatusCode(cosmosException, HttpConstants.StatusCodes.GONE) &&
            Exceptions.isSubStatusCode(cosmosException, HttpConstants.SubStatusCodes.NAME_CACHE_IS_STALE);
    }

    private boolean isGatewayIncorrectContainerException(CosmosException cosmosException) {
        return cosmosException != null &&
            Exceptions.isStatusCode(cosmosException, HttpConstants.StatusCodes.BADREQUEST) &&
            Exceptions.isSubStatusCode(cosmosException, HttpConstants.SubStatusCodes.INCORRECT_CONTAINER_RID_SUB_STATUS);
    }

    private boolean shouldSuppressRetry(Map<String, String> requestCustomHeaders) {
        return requestCustomHeaders != null
            && !StringUtils.isEmpty(requestCustomHeaders.get(HttpConstants.HttpHeaders.INTENDED_COLLECTION_RID_HEADER));
    }
}
