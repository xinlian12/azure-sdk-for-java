package com.azure.cosmos.implementation.throughputControl;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.PartitionKey;
import org.testng.annotations.Test;

import java.util.UUID;

public class RandomTests {

    @Test
    public void testGLSN() {
        CosmosAsyncClient client = new CosmosClientBuilder()
            .endpoint("")
            .key("")
            .consistencyLevel(ConsistencyLevel.STRONG)
            .buildAsyncClient();


        CosmosAsyncContainer container = client.getDatabase("testdb").getContainer("testContainer");

        try {
            //container.readItem(UUID.randomUUID().toString(), new PartitionKey("mypk"), TestItem.class).block();
            container.readItem("a1302ed6-7e8e-4571-8644-73594d0dda2e", new PartitionKey("mypk"), TestItem.class).block();
        }
        catch (Exception e) {
            System.out.println(e.getClass().toString());
            if (e instanceof CosmosException) {
                System.out.println(((CosmosException) e).getStatusCode() + ":" + ((CosmosException) e).getSubStatusCode());
            }
        }
    }
}
