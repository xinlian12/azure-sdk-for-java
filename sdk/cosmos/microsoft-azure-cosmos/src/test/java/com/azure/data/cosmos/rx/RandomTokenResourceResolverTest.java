package com.azure.data.cosmos.rx;


import com.azure.data.cosmos.ConnectionMode;
import com.azure.data.cosmos.ConnectionPolicy;
import com.azure.data.cosmos.CosmosDatabase;
import com.azure.data.cosmos.CosmosUser;
import com.azure.data.cosmos.FeedOptions;
import com.azure.data.cosmos.FeedResponse;
import com.azure.data.cosmos.PartitionKeyDefinition;
import com.azure.data.cosmos.PermissionMode;
import com.azure.data.cosmos.internal.AsyncDocumentClient;
import com.azure.data.cosmos.internal.AsyncDocumentClient.Builder;
import com.azure.data.cosmos.internal.Database;
import com.azure.data.cosmos.internal.Document;
import com.azure.data.cosmos.internal.DocumentCollection;
import com.azure.data.cosmos.internal.Permission;
import com.azure.data.cosmos.internal.User;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.UUID;

public class RandomTokenResourceResolverTest {

    @Test
    public void randomTokenResourceTest() {

        String databaseId="testDb";
        String collectionId="testCollection";
        String userId="testUser";
        String permissionForAll="PermissionForAll";

        ConnectionPolicy connectionPolicy = new ConnectionPolicy();
        connectionPolicy.connectionMode(ConnectionMode.GATEWAY);
        AsyncDocumentClient client = new Builder()
            .withServiceEndpoint("value")
            .withMasterKeyOrResourceToken("key")
            .withConnectionPolicy(connectionPolicy)
            .build();

        // create database
        Database dbDef = new Database();
        dbDef.id(databaseId);
        Database db = client.createDatabase(dbDef, null).single().block().getResource();

        // create collection
        PartitionKeyDefinition partitionKeyDef = new PartitionKeyDefinition();
        ArrayList<String> paths = new ArrayList<String>();
        paths.add("/mypk");
        partitionKeyDef.paths(paths);

        DocumentCollection collectionDefinition = new DocumentCollection();
        collectionDefinition.id(collectionId);
        collectionDefinition.setPartitionKey(partitionKeyDef);
        DocumentCollection collection =
            client.createCollection("dbs/" + databaseId, collectionDefinition, null).single().block().getResource();

        // create permission for all user
        User userDefinition = new User();
        userDefinition.id(userId);
        User user = client.createUser("dbs/" + databaseId, userDefinition, null).single().block().getResource();

        Permission permissionDefinition = new Permission();
        permissionDefinition.id(permissionForAll);
        permissionDefinition.setPermissionMode(PermissionMode.ALL);
        permissionDefinition.setResourceLink(collection.selfLink());
        Permission allPermission = client.createPermission(user.selfLink(), permissionDefinition, null).single().block().getResource();


        // create second client with resource token
        AsyncDocumentClient client2 = new Builder()
            .withServiceEndpoint("https://cosmos-sdk-test-southeastasia.documents.azure.com:443/")
            .withMasterKeyOrResourceToken(allPermission.getToken())
            .withConnectionPolicy(connectionPolicy)
            .build();

        FeedOptions options = new FeedOptions();
        options.maxItemCount(3);
        options.enableCrossPartitionQuery(true);
        while(true) {
            try{
                client2.queryDocuments(getCollectionLink(collection, databaseId), "Select * from root", options).blockLast();
                System.out.println("complete request");
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }


    private String getCollectionLink(DocumentCollection collection, String databaseId) {
        return "dbs/" + databaseId + "/colls/" + collection.id();
    }
}
