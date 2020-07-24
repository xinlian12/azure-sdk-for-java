package com.azure.cosmos;

import com.azure.cosmos.implementation.InternalObjectNode;
import com.azure.cosmos.implementation.TestConfigurations;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.ModelBridgeInternal;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedFlux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class ClientInitializationTest {
    private CosmosAsyncClient cosmosClient;
    private static Logger logger = LoggerFactory.getLogger(ClientInitializationTest.class);
    @Test
    public void AsyncClientInitialization() {
        logger.info("AsyncClientInitialization() start");
        logger.info("buildAsyncClient() start");
        cosmosClient = new CosmosClientBuilder()
            .endpoint(TestConfigurations.HOST)
            .key(TestConfigurations.MASTER_KEY)
            .contentResponseOnWriteEnabled(true)
            .consistencyLevel(ConsistencyLevel.EVENTUAL)
            .buildAsyncClient();
        logger.info("buildAsyncClient() finish");

//        logger.info("createDatabaseIfNotExists() start");
//        cosmosClient.createDatabaseIfNotExists("testDatabase").block();
//        logger.info("createDatabaseIfNotExists() finish");
//
//        logger.info("createDatabaseIfNotExists() start");
//        cosmosClient.createDatabaseIfNotExists("testDatabase").block();
//        logger.info("createDatabaseIfNotExists() finish");

        CosmosAsyncDatabase database = cosmosClient.getDatabase("testDatabase");
        CosmosAsyncContainer container = database.getContainer("testContainer");

        logger.info("readItem() start");
        CosmosItemResponse<InternalObjectNode> readResponse1 = container.readItem("6e9f91a5-974d-49d4-8ec2-4f02dc17b0ad",
            new PartitionKey("pk1"),
            new CosmosItemRequestOptions(),
            InternalObjectNode.class).block();
        logger.info("readItem() finish");


//        logger.info("queryItems() start");
//        CosmosPagedFlux<InternalObjectNode> results = container.queryItems("select * from c", new CosmosQueryRequestOptions(), InternalObjectNode.class);
//        results.byPage(5).blockFirst();
//        logger.info("queryItems() finish");
//
//        logger.info("queryItems() start");
//        CosmosPagedFlux<InternalObjectNode> results2 = container.queryItems("select * from c", new CosmosQueryRequestOptions(), InternalObjectNode.class);
//        results2.byPage(5).blockFirst();
//        logger.info("queryItems() finish");


        logger.info("AsyncClientInitialization() finish");
        cosmosClient.close();
    }

    @AfterMethod
    public void clear() {
       // cosmosClient.getDatabase("testDatabase").delete();
        cosmosClient.close();
    }
}
