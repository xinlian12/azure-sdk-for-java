package com.azure.cosmos.implementation.faultinjection;

import com.azure.cosmos.implementation.RxDocumentServiceRequest;
import com.azure.cosmos.implementation.RxDocumentServiceResponse;
import com.azure.cosmos.models.FaultInjectionConnectionErrorResult;
import com.azure.cosmos.models.FaultInjectionRule;
import com.azure.cosmos.models.FaultInjectionServerErrorResult;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

public class GatewayFaultInjector implements IFaultInjector{
    private List<FaultInjectionRule> serverErrorInjectRules = new ArrayList<>();
    private List<FaultInjectionRule> connectionErrorInjectRules = new ArrayList<>();

    @Override
    public boolean configureRule(FaultInjectionRule rule) {
        if (rule.getCondition().getProtocol() == "Http") {
            if (rule.getResult() instanceof FaultInjectionServerErrorResult) {
                this.serverErrorInjectRules.add(rule);
            } else {
                this.connectionErrorInjectRules.add(rule);
            }

            return true;
        }

        return false;
    }

    public Mono<RxDocumentServiceResponse> applyRule(RxDocumentServiceRequest request) {
        for (FaultInjectionRule rule : serverErrorInjectRules) {
            if (rule.canApply(request)) {
                // return result
                return Mono.error(new IllegalStateException("Not supported"));
            }
        }

        return Mono.error(new IllegalStateException("Not supported"));
    }
}
