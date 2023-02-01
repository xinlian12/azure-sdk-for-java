package com.azure.cosmos.implementation.faultinjection;

import com.azure.cosmos.models.FaultInjectionRule;

public interface IFaultInjector {
    boolean configureRule(FaultInjectionRule rule);
}
