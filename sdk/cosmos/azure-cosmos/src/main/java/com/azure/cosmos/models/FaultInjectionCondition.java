package com.azure.cosmos.models;

public class FaultInjectionCondition {
    private String region;
    private String protocol;
    private String databaseName;
    private String containerName;
    private PartitionKey partitionKey;
    private String operationType;

    public FaultInjectionCondition(
        String region,
        String protocol,
        String databaseName,
        String containerName,
        PartitionKey partitionKey,
        String operationType) {
        this.region = region;
        this.protocol = protocol;
        this.databaseName = databaseName;
        this.containerName = containerName;
        this.partitionKey = partitionKey;
        this.operationType = operationType;
    }

    public String getRegion() {
        return region;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getContainerName() {
        return containerName;
    }

    public PartitionKey getPartitionKey() {
        return partitionKey;
    }

    public String getOperationType() {
        return operationType;
    }
}
