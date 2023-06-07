package com.azure.cosmos.implementation.faultinjection;

import com.azure.cosmos.CosmosException;
import com.azure.cosmos.implementation.RxDocumentServiceRequest;
import com.azure.cosmos.implementation.Utils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.azure.cosmos.implementation.guava25.base.Preconditions.checkNotNull;

public class GatewayServerErrorInjector implements IGatewayServerErrorInjector {

    private List<IGatewayServerErrorInjector> faultInjectors = new ArrayList<>();

    public void registerServerErrorInjector(IGatewayServerErrorInjector serverErrorInjector) {
        checkNotNull(serverErrorInjector, "Argument 'serverErrorInjector' can not be null");
        this.faultInjectors.add(serverErrorInjector);
    }

    @Override
    public boolean injectGatewayServerResponseDelayBeforeProcessing(
        RxDocumentServiceRequest serviceRequest,
        Utils.ValueHolder<Duration> durationValueHolder) {
        return false;
    }

    @Override
    public boolean injectGatewayServerResponseDelayAfterProcessing(
        RxDocumentServiceRequest serviceRequest,
        Utils.ValueHolder<Duration> durationValueHolder) {
        return false;
    }

    @Override
    public boolean injectGatewayServerResponseError(
        RxDocumentServiceRequest serviceRequest,
        Utils.ValueHolder<CosmosException> exceptionHolder) {
        for (IGatewayServerErrorInjector injector : this.faultInjectors) {
            if (injector.injectGatewayServerResponseError(serviceRequest, exceptionHolder)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean injectGatewayServerConnectionDelay(
        RxDocumentServiceRequest serviceRequest,
        Consumer<Duration> openConnectionWithDelayConsumer) {
        return false;
    }
}
