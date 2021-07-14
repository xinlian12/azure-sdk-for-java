package com.azure.cosmos.implementation.throughputControl;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.CosmosAsyncStoredProcedure;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosStoredProcedureRequestOptions;
import com.azure.cosmos.models.CosmosStoredProcedureResponse;
import com.azure.cosmos.models.PartitionKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RateLimiterPOCTesting {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterPOCTesting.class);
    private static final int MaxRate = 200;
    private static final int DurationInSeconds = 20;
    private static final String TestAccountPrefix = "RateLimiter-test-";
    private static final Random random = new Random();

    public static void main(String[] args) {
        CosmosAsyncClient cosmosAsyncClient = new CosmosClientBuilder()
            .key("ne7c3BVLRk60cqERjIPlllln5I8VSj7b8Y4lRQpXqLR8ikTixzZLsFEXdtKVroD2LfRImqE9MT4lwW8nellQ3w==")
            .endpoint("https://cosmos-sdk-tests-3.documents.azure.com:443/")
            .contentResponseOnWriteEnabled(true)
            .buildAsyncClient();

        CosmosAsyncDatabase database = cosmosAsyncClient.getDatabase("AzureSampleFamilyDB");
        CosmosAsyncContainer container = database.getContainer("FamilyContainer");

        CosmosAsyncStoredProcedure  storedProcedure = container.getScripts().getStoredProcedure("updateCounterStoredProcedure");

        for (int i = 0; i < 200000; i++) {
            Flux.range(1, 1)
                .flatMap(t -> executeStoredProcedure(random.nextInt(200000), storedProcedure))
                .subscribeOn(Schedulers.boundedElastic())
                .blockLast();
        }

        for (int index = 0; index < 200000; index++) {
            executeStoredProcedure(index, storedProcedure).block();
        }
    }

    private static Mono<CosmosStoredProcedureResponse> executeStoredProcedure(int index, CosmosAsyncStoredProcedure cosmosAsyncStoredProcedure) {
        List<Object> parameters = new ArrayList<>();
        String id = TestAccountPrefix + index;
        parameters.add(id);
        parameters.add(10);
        parameters.add(MaxRate);
        parameters.add(DurationInSeconds);
        RequestCounter requestCounter = new RequestCounter(id, id, MaxRate, Instant.now().toEpochMilli() + DurationInSeconds*1000);
        parameters.add(requestCounter);

        CosmosStoredProcedureRequestOptions options = new CosmosStoredProcedureRequestOptions();
        options.setPartitionKey(new PartitionKey(id));

        return cosmosAsyncStoredProcedure.execute(parameters, options)
            .doOnNext(response -> {
                logger.info("RequestCharge: {}, latency: {}, left counter: {}", response.getRequestCharge(), response.getDuration().toMillis(), response.getResponseAsString());
            });
    }
}
