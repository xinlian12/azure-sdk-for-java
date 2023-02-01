package com.azure.cosmos.models;

import java.time.Duration;

public class FaultInjectionRuleBuilder {
    private FaultInjectionCondition condition;
    private FaultInjectionResult result;
    private Duration duration;

    public FaultInjectionRuleBuilder condition(FaultInjectionCondition faultInjectionCondition) {
        this.condition = faultInjectionCondition;
        return this;
    }

    public FaultInjectionRuleBuilder result(FaultInjectionResult faultInjectionResult) {
        this.result = faultInjectionResult;
        return this;
    }

    public FaultInjectionRuleBuilder duration(Duration faultInjectionDuration) {
        this.duration = faultInjectionDuration;
        return this;
    }

    public FaultInjectionRule build() {
        return new FaultInjectionRule(condition, result, duration);
    }
}
