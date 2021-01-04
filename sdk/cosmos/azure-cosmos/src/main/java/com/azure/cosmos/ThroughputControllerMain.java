// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos;

import java.time.Duration;

public class ThroughputControllerMain {

    public static void main(String[] args) {

        CosmosAsyncClient client = new CosmosClientBuilder()
            .endpoint("https://localhost:8081")
            .key("C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==")
            .consistencyLevel(ConsistencyLevel.EVENTUAL)
            .directMode()
            .buildAsyncClient();

        CosmosAsyncDatabase database = client.getDatabase("testDB");
        CosmosAsyncContainer feedContainer = database.getContainer("testContainer");
        CosmosAsyncContainer throughputControlContainer = client.getDatabase("testDB").getContainer("throughputControlContainer");

        // Throughput control group with hard limit and set by default
        ThroughputControlGroupConfig group1 =
            new ThroughputControlGroupConfig()
                .groupName("group-1")
                .targetContainer(feedContainer)
                .targetThroughput(10000)
                .localControlMode()
                .useByDefault();

        // Throughput control group with limit threshold
        ThroughputControlGroupConfig group2 = new ThroughputControlGroupConfig()
            .groupName("group-2")
            .targetContainer(feedContainer)
            .targetThroughputThreshold(0.9)
            .localControlMode();

//        ThroughputControlGroupConfig group3 =
//            new ThroughputControlGroupConfig()
//                .groupName("group-3")
//                .targetContainer(feedContainer)
//                .targetThroughputThreshold(0.9)
//                .distributedControlMode(
//                    new DistributedThroughputControlConfig()
//                    .controlContainer(throughputControlContainer) // can be under the same account as the feed container or different account
//                    .documentRenewalInterval(Duration.ofSeconds(10))
//                    .documentExpireInterval(Duration.ofSeconds(10)));

        // after build, can use in the client
        client.enableThroughputControl(group1, group2);
    }
}
