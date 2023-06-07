// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.azure.cosmos.faultinjection;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.implementation.TestConfigurations;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.test.faultinjection.FaultInjectionCondition;
import com.azure.cosmos.test.faultinjection.FaultInjectionConditionBuilder;
import com.azure.cosmos.test.faultinjection.FaultInjectionConnectionType;
import com.azure.cosmos.test.faultinjection.FaultInjectionOperationType;
import com.azure.cosmos.test.faultinjection.FaultInjectionResultBuilders;
import com.azure.cosmos.test.faultinjection.FaultInjectionRule;
import com.azure.cosmos.test.faultinjection.FaultInjectionRuleBuilder;
import com.azure.cosmos.test.faultinjection.FaultInjectionServerErrorType;
import com.fasterxml.jackson.databind.JsonNode;
import org.assertj.core.api.Assertions;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;

public class FaultInjectionUnitTest {

    @Test(groups = "unit")
    public void testFaultInjectionBuilder() {
        String ruleId = "rule_id_1";
        FaultInjectionCondition faultInjectionCondition = new FaultInjectionConditionBuilder()
            .operationType(FaultInjectionOperationType.CREATE_ITEM)
            .connectionType(FaultInjectionConnectionType.DIRECT)
            .build();
        FaultInjectionRule faultInjectionRule = new FaultInjectionRuleBuilder(ruleId)
            .condition(faultInjectionCondition)
            .duration(Duration.ofSeconds(1))
            .result(FaultInjectionResultBuilders
                .getResultBuilder(FaultInjectionServerErrorType.CONNECTION_DELAY)
                .delay(Duration.ofSeconds(6)) // default connection timeout is 5s
                .times(1)
                .build())
            .build();

        Assertions.assertThat(faultInjectionRule.getId()).isEqualTo(ruleId);
        Assertions.assertThat(faultInjectionRule.getCondition()).isEqualTo(faultInjectionCondition);
        Assertions.assertThat(faultInjectionRule.getDuration()).isEqualTo(Duration.ofSeconds(1));
        Assertions.assertThat(faultInjectionRule.getResult()).isNotNull();
    }

    @Test
    public void test() throws InterruptedException {
        CosmosAsyncClient client = new CosmosClientBuilder()
            .key(TestConfigurations.MASTER_KEY)
            .endpoint(TestConfigurations.HOST)
            .gatewayMode()
            .buildAsyncClient();

        CosmosAsyncContainer container = client.getDatabase("TestDatabase").getContainer("TestContainer");
        container.readItem("ee7f3ac5-5761-492a-aad9-5493f0b0ad3f", new PartitionKey("ee7f3ac5-5761-492a-aad9-5493f0b0ad3f"), JsonNode.class)
            .onErrorResume(throwable -> {
                System.out.println(((CosmosException)throwable).getDiagnostics());
                return Mono.empty();
            })
            .block();

        System.out.println("Read the second item");
        container.readItem("ee7f3ac5-5761-492a-aad9-5493f0b0ad3f", new PartitionKey("ee7f3ac5-5761-492a-aad9-5493f0b0ad3f"), JsonNode.class)
            .onErrorResume(throwable -> {
                System.out.println(((CosmosException)throwable).getDiagnostics());
                return Mono.empty();
            })
            .block();

        Thread.sleep(50000);

        System.out.println("Read the third item");
        container.readItem("ee7f3ac5-5761-492a-aad9-5493f0b0ad3f", new PartitionKey("ee7f3ac5-5761-492a-aad9-5493f0b0ad3f"), JsonNode.class)
            .onErrorResume(throwable -> {
                System.out.println(((CosmosException)throwable).getDiagnostics());
                return Mono.empty();
            })
            .block();

//        container.readItem("TESTsdk-generic-test-lx.documents.azure.com_TestDatabase_TestContainer..0", new PartitionKey("ESTsdk-generic-test-lx.documents.azure.com_TestDatabase_TestContainer..0"), JsonNode.class)
//            .onErrorResume(throwable -> {
//                System.out.println(((CosmosException)throwable).getDiagnostics());
//                return Mono.empty();
//            })
//            .block();
    }
}
