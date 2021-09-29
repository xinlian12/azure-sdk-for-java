package com.azure.cosmos.implementation.throughputControl;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosException;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

public class RandomTests {

    @Test
    public void addressRefreshTests() {
        Scheduler scheduler = Schedulers.newParallel(
            "test",
            Schedulers.DEFAULT_POOL_SIZE,
            false);

        CosmosAsyncClient client = new CosmosClientBuilder()
            .endpoint("")
            .key("")
            .buildAsyncClient();

        CosmosAsyncContainer container = client.getDatabase("RateLimiterPOC").getContainer("testContainer2");
        Mono.delay(Duration.ofMinutes(5))
            .flatMap(t -> {
                return container.createItem(TestItem.createNewItem())
                    .doOnNext(response -> {
                        System.out.println(response.getDiagnostics());
                    })
                    .onErrorResume(throwable -> {
                        if (throwable instanceof CosmosException) {
                            System.out.println(((CosmosException) throwable).getDiagnostics().toString());
                        }
                        return Mono.empty();
                    });
            })
            .onErrorResume(throwable -> {
                System.out.println(throwable.getMessage());
                return Mono.empty();
            })
            .repeat()
            .subscribeOn(scheduler)
            .blockLast();
    }
}
