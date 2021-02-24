package com.azure.cosmos.implementation.throughputControl;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.GlobalThroughputControlConfig;
import com.azure.cosmos.ThroughputControlGroupConfig;
import com.azure.cosmos.ThroughputControlGroupConfigBuilder;
import com.azure.cosmos.implementation.apachecommons.lang.StringUtils;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Random;
import java.util.UUID;

public class MultipleClientsTest {
   private final static Random random = new Random();
    @Test
    public void multipleClientsTest() {
        CosmosAsyncClient client1 = new CosmosClientBuilder()
            .endpoint("endpoint")
            .key("key")
            .buildAsyncClient();

        CosmosAsyncClient client2 = new CosmosClientBuilder()
            .endpoint("endpoint")
            .key("key")
            .buildAsyncClient();

        String databaseName = "TestDB";

        CosmosAsyncContainer asyncContainer = client1.getDatabase(databaseName).getContainer("TestContainer");
        CosmosAsyncContainer asyncContainer2 = client2.getDatabase(databaseName).getContainer("TestContainer");

        ThroughputControlGroupConfig groupConfig =
            new ThroughputControlGroupConfigBuilder()
                .setGroupName("group-" + UUID.randomUUID())
                .setTargetThroughputThreshold(0.5)
                .build();

        GlobalThroughputControlConfig globalControlConfig =
            client1.createGlobalThroughputControlConfigBuilder(databaseName, "ThroughputControlContainer")
            .setControlItemRenewInterval(Duration.ofSeconds(5))
            .setControlItemExpireInterval(Duration.ofSeconds(10))
            .build();
        asyncContainer.enableThroughputGlobalControlGroup(groupConfig, globalControlConfig);
        asyncContainer2.enableThroughputGlobalControlGroup(groupConfig, globalControlConfig);

        CosmosItemRequestOptions requestOptions = new CosmosItemRequestOptions();
        requestOptions.setContentResponseOnWriteEnabled(true);
        requestOptions.setThroughputControlGroupName(groupConfig.getGroupName());

        this.runRequests(asyncContainer, requestOptions).subscribeOn(Schedulers.boundedElastic()).subscribe();
        this.runRequests(asyncContainer2, requestOptions).subscribeOn(Schedulers.boundedElastic()).subscribe();

        while(true) {

        }
    }

    private Flux<Void> runRequests(CosmosAsyncContainer container, CosmosItemRequestOptions requestOptions) {
       return Mono.delay(Duration.ofMillis(1))
            .flatMap(t -> {
                return container.createItem(getDocumentDefinition(), requestOptions);
            })
            .then()
            .repeat(5);
    }

    private static TestItem getDocumentDefinition() {
        return getDocumentDefinition(null);
    }

    private static TestItem getDocumentDefinition(String partitionKey) {
        return new TestItem(
            UUID.randomUUID().toString(),
            StringUtils.isEmpty(partitionKey) ? UUID.randomUUID().toString() : partitionKey,
            UUID.randomUUID().toString()
        );
    }
}
