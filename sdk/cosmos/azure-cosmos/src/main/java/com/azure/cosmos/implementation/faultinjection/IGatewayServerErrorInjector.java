package com.azure.cosmos.implementation.faultinjection;

import com.azure.cosmos.CosmosException;
import com.azure.cosmos.implementation.RxDocumentServiceRequest;
import com.azure.cosmos.implementation.Utils;

import java.time.Duration;
import java.util.function.Consumer;

public interface IGatewayServerErrorInjector {
    boolean injectGatewayServerResponseDelayBeforeProcessing(
        RxDocumentServiceRequest serviceRequest,
        Utils.ValueHolder<Duration> durationValueHolder);

    boolean injectGatewayServerResponseDelayAfterProcessing(
        RxDocumentServiceRequest serviceRequest,
        Utils.ValueHolder<Duration> durationValueHolder);

    boolean injectGatewayServerResponseError(RxDocumentServiceRequest serviceRequest, Utils.ValueHolder<CosmosException> exceptionHolder);

    boolean injectGatewayServerConnectionDelay(
        RxDocumentServiceRequest serviceRequest,
        Consumer<Duration> openConnectionWithDelayConsumer);
}
