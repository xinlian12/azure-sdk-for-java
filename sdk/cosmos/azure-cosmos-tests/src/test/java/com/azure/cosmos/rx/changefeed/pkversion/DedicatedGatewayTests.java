package com.azure.cosmos.rx.changefeed.pkversion;

import com.azure.cosmos.ChangeFeedProcessor;
import com.azure.cosmos.ChangeFeedProcessorBuilder;
import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.implementation.TestConfigurations;
import com.azure.cosmos.implementation.throughputControl.TestItem;
import com.azure.cosmos.models.ChangeFeedProcessorOptions;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class DedicatedGatewayTests {
    private final static Logger log = LoggerFactory.getLogger(DedicatedGatewayTests.class);

    @Test
    public void changeFeedProcessorTests() throws InterruptedException {

        Map<String, JsonNode> receivedDocuments = new ConcurrentHashMap<>();
        CosmosAsyncClient client = new CosmosClientBuilder()
            .key(TestConfigurations.MASTER_KEY)
            .endpoint(TestConfigurations.HOST)
            .gatewayMode()
            .consistencyLevel(ConsistencyLevel.EVENTUAL)
            .contentResponseOnWriteEnabled(true)
            .buildAsyncClient();


        CosmosAsyncContainer feedContainer = client.getDatabase("TestDatabase").getContainer("TestContainer2");
        CosmosAsyncContainer leaseContainer = client.getDatabase("TestDatabase").getContainer("leaseContainer");

        System.out.println("creating items in the feed container");
        for (int i = 0; i < 100; i++) {
            feedContainer.createItem(TestItem.createNewItem()).block();
        }

        ChangeFeedProcessor changeFeedProcessor = new ChangeFeedProcessorBuilder()
            .hostName("test1")
            .handleChanges(changeFeedProcessorHandler(receivedDocuments))
            .feedContainer(feedContainer)
            .leaseContainer(leaseContainer)
            .options(new ChangeFeedProcessorOptions()
                .setLeaseRenewInterval(Duration.ofSeconds(20))
                .setLeaseAcquireInterval(Duration.ofSeconds(10))
                .setLeaseExpirationInterval(Duration.ofSeconds(30))
                .setFeedPollDelay(Duration.ofSeconds(2))
                .setLeasePrefix("TEST")
                .setMaxItemCount(10)
                .setStartFromBeginning(true)
                .setMaxScaleCount(0) // unlimited
            )
            .buildChangeFeedProcessor();

        ChangeFeedProcessor changeFeedProcessor2 = new ChangeFeedProcessorBuilder()
            .hostName("test2")
            .handleChanges(changeFeedProcessorHandler(receivedDocuments))
            .feedContainer(feedContainer)
            .leaseContainer(leaseContainer)
            .options(new ChangeFeedProcessorOptions()
                .setLeaseRenewInterval(Duration.ofSeconds(20))
                .setLeaseAcquireInterval(Duration.ofSeconds(10))
                .setLeaseExpirationInterval(Duration.ofSeconds(30))
                .setFeedPollDelay(Duration.ofSeconds(2))
                .setLeasePrefix("TEST")
                .setMaxItemCount(10)
                .setStartFromBeginning(true)
                .setMaxScaleCount(0) // unlimited
            )
            .buildChangeFeedProcessor();

        changeFeedProcessor.start().subscribeOn(Schedulers.boundedElastic()).subscribe();
        changeFeedProcessor2.start().subscribeOn(Schedulers.boundedElastic()).subscribe();

        Thread.sleep(Duration.ofMinutes(10).toMillis());
    }

    @Test
    public void test() {
        CosmosAsyncClient client = new CosmosClientBuilder()
            .key(TestConfigurations.MASTER_KEY)
            .endpoint(TestConfigurations.HOST)
            .gatewayMode()
            .consistencyLevel(ConsistencyLevel.EVENTUAL)
            .contentResponseOnWriteEnabled(true)
            .buildAsyncClient();

        CosmosAsyncContainer container = client.getDatabase("TestDatabase").getContainer("TestContainer");
        CosmosItemResponse<TestItem> testItemResponse =
            container.readItem(
                "935852ff-91b0-48f6-81b6-96aa85655d89",
                new PartitionKey("935852ff-91b0-48f6-81b6-96aa85655d89"), TestItem.class)
                .block();

        TestItem testItem = testItemResponse.getItem();
        System.out.println("Original prop value: " + testItemResponse.getItem().getProp());

        testItemResponse.getItem().setProp(UUID.randomUUID().toString());
        System.out.println("New Prop value: " + testItemResponse.getItem().getProp());

        CosmosItemRequestOptions cosmosItemRequestOptions = new CosmosItemRequestOptions();
        String oldEtag = testItemResponse.getETag();
        cosmosItemRequestOptions.setIfMatchETag(testItemResponse.getETag());

        container.replaceItem(testItem, testItem.getId(), new PartitionKey(testItem.getId()), cosmosItemRequestOptions).block();

        for (int i = 0; i < 10; i++) {
            String query = "select * from c where c.id = '935852ff-91b0-48f6-81b6-96aa85655d89'";
            container.queryItems(query, TestItem.class)
                .byPage(1)
                .flatMap(response -> {
                    for (TestItem result : response.getResults()) {
                        System.out.println("Query result : " + result.getProp());
                    }

                    return Mono.empty();
                })
                .blockLast();
        }

    }

    private Consumer<List<JsonNode>> changeFeedProcessorHandler(Map<String, JsonNode> receivedDocuments) {
        return docs -> {
            log.info("START processing from thread in test {}", Thread.currentThread().getId());
            for (JsonNode item : docs) {
                processItem(item, receivedDocuments);
            }
            log.info("END processing from thread {}", Thread.currentThread().getId());
        };
    }

    private static synchronized void processItem(JsonNode item, Map<String, JsonNode> receivedDocuments) {
        receivedDocuments.put(item.get("id").asText(), item);
    }
}
