package com.azure.cosmos.models;

import com.azure.cosmos.implementation.query.PartitionedQueryExecutionInfo;

public class FaultInjectionConditionBuilder {
    private String region;
    private String protocol;
    private String databaseName;
    private String containerName;
    private PartitionKey partitionKey;
    private String operationType;

    public FaultInjectionConditionBuilder region(String region) {
        this.region = region;
        return this;
    }

    public FaultInjectionConditionBuilder protocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    public FaultInjectionConditionBuilder databaseName(String databaseName) {
        this.databaseName = databaseName;
        return this;
    }

    public FaultInjectionConditionBuilder containerName(String containerName) {
        this.containerName = containerName;
        return this;
    }

    public FaultInjectionConditionBuilder partitionKey(PartitionKey partitionKey) {
        this.partitionKey = partitionKey;
        return this;
    }

    public FaultInjectionConditionBuilder operationType(String operationType) {
        this.operationType = operationType;
        return this;
    }

    public FaultInjectionCondition build() {
        return new FaultInjectionCondition(region, protocol, databaseName, containerName, partitionKey, operationType);
    }
}
