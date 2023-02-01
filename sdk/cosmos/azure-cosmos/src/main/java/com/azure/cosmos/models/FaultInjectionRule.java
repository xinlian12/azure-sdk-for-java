package com.azure.cosmos.models;

import com.azure.cosmos.implementation.RxDocumentServiceRequest;

import java.time.Duration;

public class FaultInjectionRule {
    private FaultInjectionCondition condition;
    private FaultInjectionResult result;

    private Duration duration;

    public FaultInjectionRule(FaultInjectionCondition condition, FaultInjectionResult result, Duration duration) {
        this.condition = condition;
        this.result = result;
        this.duration = duration;
    }

    public FaultInjectionCondition getCondition() {
        return condition;
    }

    public void setCondition(FaultInjectionCondition condition) {
        this.condition = condition;
    }

    public FaultInjectionResult getResult() {
        return result;
    }

    public void setResult(FaultInjectionResult result) {
        this.result = result;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public boolean canApply(RxDocumentServiceRequest request) {
        // check whether condition match
        return false;
    }
}
