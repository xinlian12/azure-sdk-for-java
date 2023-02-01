package com.azure.cosmos.implementation.faultinjection;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.implementation.OperationType;
import com.azure.cosmos.models.FaultInjectionCondition;
import com.azure.cosmos.models.FaultInjectionConditionBuilder;
import com.azure.cosmos.models.FaultInjectionResult;
import com.azure.cosmos.models.FaultInjectionRule;
import com.azure.cosmos.models.FaultInjectionRuleBuilder;
import com.azure.cosmos.models.FaultInjectionServerErrorResult;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.ServerErrorTypes;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Arrays;

public class FaultInjectionTests {

    @Test
    public void faultInjectionTest() {
        String endpoint = "";
        String key = "";
        String databaseName = "";
        String containerName = "";

        CosmosAsyncClient client = new CosmosClientBuilder()
            .endpoint(endpoint)
            .key(key)
            .buildAsyncClient();

        CosmosAsyncContainer container = client.getDatabase(databaseName).getContainer(containerName);

        // capability to disable the rules
        FaultInjectionRule faultInjectionRule =
            new FaultInjectionRuleBuilder()
                .condition(
                    new FaultInjectionConditionBuilder()
                        .region("East US")
                        .containerName("TestDB")
                        .databaseName("DatabaseName")
                        .operationType("Read") // meta request
                        .protocol("Tcp")
                        .partitionKey(new PartitionKey("TestPk"))
                       // .primaryReplica(false)
                        .build())
                .result(new FaultInjectionServerErrorResult(ServerErrorTypes.INTERNAL_SERVER_ERROR, 3))
                .duration(Duration.ofSeconds(10))
                // total count
                .build();

        // gateway will be more useful for metadata

        container.applyFaultInjectionRules(Arrays.asList(faultInjectionRule)); // separate bridge class which can be used for walmart - this API should be private
        // 1. Where the fault injection rules should be configured? container level as need to filter down on pk
        // 2. All the server generated errors are mocked directly in RntbdRequestManager
        // 3. Can not inject two rules at the same time on the same injection condition
        // 4. Partition split -> instead of using fullRange and generating a real error, using mocked response - leave it for now
        // 5. Connection - delay, connectionTimeout, connectionReset

        // clear all()
    }
}
