package com.azure.cosmos.implementation.faultinjection;

import com.azure.cosmos.implementation.RxDocumentClientImpl;
import com.azure.cosmos.implementation.RxDocumentServiceRequest;
import com.azure.cosmos.models.FaultInjectionRule;
import com.azure.cosmos.models.FaultInjectionServerErrorResult;

import java.util.ArrayList;
import java.util.List;

public class RntbdFaultInjector implements IFaultInjector{
    private List<FaultInjectionRule> serverErrorFaultInjectionRules = new ArrayList<>();
    private List<FaultInjectionRule> connectionErrorFaultInjectionRules = new ArrayList<>();

    @Override
    public boolean configureRule(FaultInjectionRule rule) {
        if (rule.getCondition().getProtocol() == "Tcp") {
            if (rule.getResult() instanceof FaultInjectionServerErrorResult) {
                this.serverErrorFaultInjectionRules.add(rule);
            } else {
                this.connectionErrorFaultInjectionRules.add(rule);
            }

            return true;
        }

        return false;
    }

    public boolean applyRule(RxDocumentServiceRequest serviceRequest) {
        // no-op
        return false;
    }
}
