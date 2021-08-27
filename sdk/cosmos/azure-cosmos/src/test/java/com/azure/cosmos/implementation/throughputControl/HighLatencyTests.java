package com.azure.cosmos.implementation.throughputControl;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Random;

public class HighLatencyTests {
    private static Random random = new Random();
    private static String id = "9968e431-d053-4376-821f-6ec0f009c317";

    @Test
    public void HighLatencyTests() {
        CosmosAsyncClient client = new CosmosClientBuilder()
            .endpoint("https://cosmos-sdk-tests-3.documents.azure.com:443/")
            .key("")
            .buildAsyncClient();

        CosmosAsyncContainer container = client.getDatabase("SampleDatabase").getContainer("GreenTaxiRecords");
        container.openConnectionsAndInitCaches();

        String id = "9968e431-d053-4376-821f-6ec0f009c317";

        Mono.just(this)
            .flatMapMany(t -> readItems(container))
            .onErrorResume(throwable -> {
                System.out.println("Exception: " + throwable.getMessage());
                return Mono.empty();
            })
            .publishOn(Schedulers.boundedElastic())
            .flatMap(response -> {
                System.out.println(response.getDiagnostics().getDuration().toMillis());
                if (response.getDiagnostics().getDuration().toMillis() > 100) {
                    System.out.println(response.getDiagnostics());
                }
                return Mono.just(response);
            })
            .delayElements(Duration.ofMillis(10000))
            .repeat(1000000)
            .blockLast();

        client.close();
    }

    private Flux<CosmosItemResponse<ObjectNode>> readItems(CosmosAsyncContainer container) {
        int concurrency = Math.max(1, random.nextInt(5));
        return Flux.range(1, concurrency)
            .flatMap(t -> container.readItem(id, new PartitionKey(id), ObjectNode.class));
    }
}
