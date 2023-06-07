package com.azure.cosmos.test.implementation.faultinjection;

import com.azure.cosmos.CosmosException;
import com.azure.cosmos.implementation.RxDocumentServiceRequest;
import com.azure.cosmos.implementation.Utils;
import com.azure.cosmos.implementation.directconnectivity.rntbd.RntbdRequestArgs;
import com.azure.cosmos.implementation.faultinjection.IGatewayServerErrorInjector;

import java.time.Duration;
import java.util.function.Consumer;

import static com.azure.cosmos.implementation.guava25.base.Preconditions.checkNotNull;

public class GatewayServerErrorInjector implements IGatewayServerErrorInjector {
    private final FaultInjectionRuleStore ruleStore;

    public GatewayServerErrorInjector(FaultInjectionRuleStore ruleStore) {
        checkNotNull(ruleStore, "Argument 'ruleStore' can not be null");

        this.ruleStore = ruleStore;
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
    public boolean injectGatewayServerResponseError(RxDocumentServiceRequest serviceRequest, Utils.ValueHolder<CosmosException> exceptionHolder) {

        FaultInjectionServerErrorRule serverResponseErrorRule =
            this.ruleStore.findGatewayServerResponseErrorRule(serviceRequest);

        if (serverResponseErrorRule != null) {
            serviceRequest.faultInjectionRequestContext
                    .applyFaultInjectionRule(
                        serviceRequest.getActivityId().toString(),
                        serverResponseErrorRule.getId()
                    );

            CosmosException cosmosException = serverResponseErrorRule.getInjectedServerError(serviceRequest);
            exceptionHolder.v = cosmosException;
            return true;
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
