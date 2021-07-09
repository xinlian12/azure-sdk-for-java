package com.azure.cosmos.implementation.throughputControl;

import com.azure.cosmos.BridgeInternal;
import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.DirectConnectionConfig;
import com.azure.cosmos.GatewayConnectionConfig;
import com.azure.cosmos.ThrottlingRetryOptions;
import com.azure.cosmos.implementation.AsyncDocumentClient;
import com.azure.cosmos.implementation.ItemOperations;
import com.azure.cosmos.implementation.PartitionKeyRange;
import com.azure.cosmos.implementation.apachecommons.lang.tuple.Pair;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.models.ThroughputResponse;
import com.azure.cosmos.util.CosmosPagedFlux;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicAsyncDemo {
    private static final Logger logger = LoggerFactory.getLogger(BasicAsyncDemo.class);
    private static final String DATABASE_NAME = "perfdb";
    private static final String READ_CONTAINER_NAME = "perfcontRead";
    private static final String READ_SMALL_CONTAINER_NAME = "perfcontReadSmall";
    private static final String QUERY_CONTAINER_NAME = "perfcontQuery";
    String HOST = RMW_HOST;
    String KEY = RMW_KEY;
    int itemCount = 0;
    List<String> countries = Arrays.asList("US", "CA", "JP", "IN", "SG", "SWZ", "GE", "FR");
    Random random;
    private CosmosAsyncClient client;
    private CosmosAsyncDatabase database;
    private CosmosAsyncContainer container;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        BasicAsyncDemo demo = new BasicAsyncDemo();
        demo.start();
    }

    private void start() {
        random = new Random();

        // Get client
        DirectConnectionConfig directConnectionConfig = new DirectConnectionConfig();
//        directConnectionConfig.setMaxConnectionsPerEndpoint(1);
//        directConnectionConfig.setMaxRequestsPerConnection(1);
//2
        GatewayConnectionConfig gatewayConnectionConfig = new GatewayConnectionConfig();
//                                                              .setProxy(new ProxyOptions(ProxyOptions.Type.HTTP,
//                                                                                         new InetSocketAddress(
//                                                                                            "127.0.0.1",
//                                                                                             8866)));
        client = new CosmosClientBuilder()
            .endpoint(HOST)
            .key(KEY)
            .consistencyLevel(ConsistencyLevel.EVENTUAL)
            .directMode(directConnectionConfig, gatewayConnectionConfig)
            .throttlingRetryOptions(new ThrottlingRetryOptions().setMaxRetryAttemptsOnThrottledRequests(0))
//                     .gatewayMode(gatewayConnectionConfig)
            .buildAsyncClient();

//        //CREATE a database and a container
//        createDbAndContainerBlocking();
//
//        //Get a proxy reference to container
//        container = client.getDatabase(DATABASE_NAME).getContainer(CONTAINER_NAME);
//
//        CosmosContainer container = client.getDatabase(DATABASE_NAME).getContainer(CONTAINER_NAME);
//        TestObject testObject = new TestObject("item_new_id_1", "test", "test description", "US");
//        TestObject testObject2 = new TestObject("item_new_id_2", "test2", "test description2", "CA");
//
//        //CREATE an Item async
//
//        CosmosItemResponse<TestObject> itemResponseMono = container.createItem(testObject);
//        //CREATE another Item async
//        CosmosItemResponse<TestObject> itemResponseMono1 = container.createItem(testObject2);
//
//        createAndReplaceItem();
//        buildUsingBuilder();
//        splitTest();
//        queryItems();
//        readMany();
//        queryWithContinuationToken();

        //Close client
//        client.close();
        System.setProperty("COSMOS.QUERYPLAN_CACHING_ENABLED", "true");
//        runQuery(-1);
//        try {
//            Thread.sleep(10 * 1000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
       // insertSimpleItemsForRead(2000);
        runReadItem(READ_CONTAINER_NAME);
        //   runQueryTest();
        log("Completed");
    }
    final String query = "select * from c";

    private void runQueryTest(){
        for (int i = 0; i < 500; i++) {
            System.out.println("Launching run # " + i);
            runQuery(i);
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void runReadItem(String containername) {
        container = client.getDatabase(DATABASE_NAME).getContainer(containername);
//
//        String itemQuery = "Select VALUE c.id from c";
//        List<String> allItems = new ArrayList<>();
//        container.queryItems(itemQuery, String.class)
//            .byPage(500)
//            .flatMap(response -> {
//                allItems.addAll(response.getResults());
//                return Mono.empty();
//            })
//            .blockLast();
//
//        System.out.println("ItemCount: " + allItems.size());

        Random random = new Random();
//        for(int i = 0; i< 200; i++) {
//            System.out.println("warmup:" + i);
//            int index = random.nextInt(largeDocuments.size());
//            String readId = largeDocuments.get(index);
//            container.readItem(readId, new PartitionKey(readId), ObjectNode.class)
//                .onErrorResume(throwable -> Mono.empty())
//                .block();
//        }
//
//        try {
//            Thread.sleep(3000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        System.out.println("Finished warm up");



        AtomicReference<Long> totalLatency = new AtomicReference<>(0L);
        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);

        for(int k = 0; k < 1; k++) {
            for(int i = 0; i < 500; i++) {
             //   int index = random.nextInt(largeDocuments.size());
                String readId;
                if (containername.equals(READ_SMALL_CONTAINER_NAME)) {
                    readId = SmallDocuments.documents.get(i);
                } else{
                    readId = LargeDocuments.documents.get(i);
                }
//            container.readItem("a659e646-6aca-4a37-aa30-18a1766284d6", new PartitionKey("a659e646-6aca-4a37-aa30-18a1766284d6"), ObjectNode.class)
//                .onErrorResume(throwable -> Mono.empty())
//                .publishOn(Schedulers.boundedElastic())
//                .subscribe();

                container.readItem(readId, new PartitionKey(readId), ObjectNode.class)
                    .publishOn(Schedulers.boundedElastic())
                    .doOnSuccess(itemResponse -> {
                        successfulRequests.incrementAndGet();
                       // logger.info("Latency:{} {}", itemResponse.getDuration().toMillis(), itemResponse.getDiagnostics().toString());

                    })
                    .onErrorResume(throwable -> {
                        failedRequests.incrementAndGet();
                        return Mono.empty();
                    })
                    .subscribe();
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

//
//                container.readItem("a659e646-6aca-4a37-aa30-18a1766284d6", new PartitionKey("a659e646-6aca-4a37-aa30-18a1766284d6"), ObjectNode.class)
//                    .block();

        while(successfulRequests.get() + failedRequests.get() < 2500) {

            System.out.println(successfulRequests.get() + "|" + failedRequests.get());
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Requests: " + successfulRequests.get() + "|" + failedRequests.get());
    }

    private void runQuery(int runId) {

        container = client.getDatabase(DATABASE_NAME).getContainer(QUERY_CONTAINER_NAME);
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
//        options.setPartitionKey(new PartitionKey("xxyy"));
        Flux<Integer> integerFlux = Flux.range(1, 2);

        integerFlux.flatMap(integer -> Flux.defer(() -> {

//            return getCosmosItemResponseMono(runId, integer);
            return getFeedResponseFlux(runId, options, integer);

        }), 1).subscribe();

    }

    @NotNull
    private Flux<FeedResponse<JsonNode>> getFeedResponseFlux(
        int runId, CosmosQueryRequestOptions options, Integer integer) {
        return container.queryItems(query, options, JsonNode.class)
            .byPage(200)
            .publishOn(Schedulers.parallel())
            .subscribeOn(Schedulers.parallel())
            .onErrorResume(throwable -> Mono.empty()).doOnComplete(() -> {
                System.out.println("++completed: " + runId + " : " + integer);
            }).publishOn(Schedulers.parallel())
            .subscribeOn(Schedulers.parallel());
    }

    @NotNull
    private Mono<CosmosItemResponse<JsonNode>> getCosmosItemResponseMono(int runId, Integer integer) {
        return container.readItem("testobject", new PartitionKey("testobject"),
            JsonNode.class).publishOn(Schedulers.parallel())
            .subscribeOn(Schedulers.parallel())
            .doOnError(System.out::println)
            .doOnSuccess(succes -> {
//                           if (integer % 1000 == 0){
//                               System.out.println("succes = " + succes.getDiagnostics());
//                           }
                System.out.println("++completed: " + runId + " : " + integer);
            }).publishOn(Schedulers.parallel())
            .subscribeOn(Schedulers.parallel());
    }

    private void splitTest() {
        CosmosAsyncDatabase createdDatabase = client.getDatabase(DATABASE_NAME);
        String containerId = "splittestcontainer_" + UUID.randomUUID();

        //Create container
        CosmosContainerProperties containerProperties = new CosmosContainerProperties(containerId, "/country");
        CosmosContainerResponse containerResponse = createdDatabase.createContainer(containerProperties).block();
        CosmosAsyncContainer container = createdDatabase.getContainer(containerId);

        //Insert some documents
        insertItems(container,10, "CA");
        insertItems(container, 10, "US");

        // Read number of partitions
        List<PartitionKeyRange> partitionKeyRanges = getPartitionKeyRanges(containerId);
        System.out.println("Num partitions = " + partitionKeyRanges.size());

        //Query and get first page and get the continuation token
        String requestContinuation = null;
        int elementCount = 0;
        String query = "select * from root";
        System.out.println("Querying:");
        FeedResponse<JsonNode> jsonNodeFeedResponse = container
            .queryItems(query, new CosmosQueryRequestOptions(), JsonNode.class)
            .byPage(15).blockFirst();
        elementCount = jsonNodeFeedResponse.getResults().size();
        requestContinuation = jsonNodeFeedResponse.getContinuationToken();
        System.out.println("requestContinuation = " + requestContinuation);

        // Scale up the throughput for a split
        System.out.println("Scaling up throughput for split");
        ThroughputProperties throughputProperties = ThroughputProperties.createManualThroughput(13000);
        ThroughputResponse throughputResponse = container.replaceThroughput(throughputProperties).block();
        System.out.println("Throughput replace request submitted for: " + throughputResponse.getProperties().getManualThroughput());

        throughputResponse = container.readThroughput().block();

        // Wait for the throughput update to complete so that we get the partition split
        while (true) {
            assert throughputResponse != null;
            if (!throughputResponse.isReplacePending()) {
                break;
            }
            System.out.println("Waiting for split to complete");
            try {
                Thread.sleep(10 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            throughputResponse = container.readThroughput().block();
        }

        System.out.println("Split done");

        System.out.println("Resuming query from the continuation");
        // Read number of partitions. Should be greater than one
        List<PartitionKeyRange> partitionKeyRangesAfterSplit = getPartitionKeyRanges(containerId);
        assert partitionKeyRangesAfterSplit.size() > 1;
        System.out.println("After split num partitions = " + partitionKeyRangesAfterSplit.size());
        System.out.println(" \tpartitionKeyRangesAfterSplit = " + partitionKeyRangesAfterSplit);


        // Reading item to refresh cache
        container.readItem(objectList.get(0).getId(), new PartitionKey(objectList.get(0).getCountry()),
            JsonNode.class).block();


        // Resume the query with continuation token saved above and make sure you get all the documents
        Flux<FeedResponse<JsonNode>> feedResponseFlux = container
            .queryItems(query, new CosmosQueryRequestOptions(), JsonNode.class)
            .byPage(requestContinuation, 10);

        for (FeedResponse<JsonNode> nodeFeedResponse : feedResponseFlux.toIterable()) {
            elementCount += nodeFeedResponse.getResults().size();
            System.out.println("elementCount = " + elementCount);
        }


        assertThat (elementCount).isEqualTo(20);

//        container.delete().block();
        System.out.println("Deleted test container");
    }

    List<TestObject> objectList = new ArrayList<>();
    private void insertItems(CosmosAsyncContainer container, int count, String ca) {
        System.out.println("BasicSyncDemo.insertItems: trying to insert items");
        for (int i = 0; i < count; i++) {

            TestObject testObject = new TestObject(UUID.randomUUID().toString(),
                "test" + i,
                "test description " + i,
                getRandomCountry(),
                random.nextInt(count));
            CosmosItemResponse<TestObject> item = container.createItem(testObject,
                new CosmosItemRequestOptions()
                    .setConsistencyLevel(ConsistencyLevel.STRONG))
                .block();
            objectList.add(testObject);

        }
    }


    private void insertItems(int count, String pk) {
        System.out.println("BasicSyncDemo.insertItems: trying to insert items");
        for (int i = 0; i < count; i++) {
            CosmosAsyncContainer container = client.getDatabase(DATABASE_NAME).getContainer(QUERY_CONTAINER_NAME);
            BasicAsyncDemo.TestObject testObject = new TestObject(UUID.randomUUID().toString(),
                "test" + i,
                "test description " + i,
                pk,
                random.nextInt(count));
            CosmosItemResponse<BasicAsyncDemo.TestObject> item = container.createItem(testObject,
                new CosmosItemRequestOptions())
                .block();
//            System.out.println("item.getRequestCharge() = " + item.getRequestCharge());

        }
    }

    private String getRandomCountry() {
        return countries.get(random.nextInt(countries.size() - 1));
    }

    @NotNull
    private List<PartitionKeyRange> getPartitionKeyRanges(String containerId) {
        AsyncDocumentClient asyncDocumentClient = BridgeInternal.getContextClient(this.client);
        CosmosAsyncDatabase createdDatabase = client.getDatabase(DATABASE_NAME);
        List<PartitionKeyRange> partitionKeyRanges = new ArrayList<>();
        List<FeedResponse<PartitionKeyRange>> partitionFeedResponseList = asyncDocumentClient
            .readPartitionKeyRanges("/dbs/" + DATABASE_NAME
                    + "/colls/" + containerId,
                new CosmosQueryRequestOptions())
            .collectList().block();
        partitionFeedResponseList.forEach(f -> partitionKeyRanges.addAll(f.getResults()));
        return partitionKeyRanges;
    }

    private void buildUsingBuilder() {
        /*

        SimpleQueryBuilder queryBuilder = new SimpleQueryBuilder();
        SqlQuerySpec sqlQuerySpec = queryBuilder
                                        .select("id", "myVal")
                                        .where("id", ConditionType.IS_EQUAL, "someval")
                                        .build();

        System.out.println("sqlQuerySpec.getQueryText() = " + sqlQuerySpec.getQueryText());

        */
    }

    private void createAndReplaceItem() {
        TestObject replaceObject = new TestObject("item_new_id_3", "test3", "test description3", "JP", 100);
        TestObject properties = null;
        //CREATE item sync
        try {
            properties = container.createItem(replaceObject).block()
                .getItem();
        } catch (RuntimeException e) {
            log("Couldn't create items due to above exceptions");
        }
        if (properties != null) {
            replaceObject.setName("new name test3");

            //REPLACE the item and wait for completion
            container.replaceItem(replaceObject,
                properties.getId(),
                new PartitionKey(replaceObject.getCountry()),
                new CosmosItemRequestOptions());
        }
    }

    private void createDbAndContainerBlocking() {
        CosmosDatabaseResponse dbResponse = client.createDatabaseIfNotExists(DATABASE_NAME).block();
        CosmosAsyncDatabase database = client.getDatabase(dbResponse.getProperties().getId());
        database.createContainerIfNotExists(new CosmosContainerProperties(QUERY_CONTAINER_NAME, "/country"));
    }

    private void readMany() {
        container = client.getDatabase(DATABASE_NAME).getContainer(QUERY_CONTAINER_NAME);

        List<Pair<String, PartitionKey>> pairList = new ArrayList<>();
        pairList.add(Pair.of("item_ca-2", new PartitionKey("CA")));

        FeedResponse<JsonNode> documentFeedResponse =
            ItemOperations.readManyAsync(container, pairList, JsonNode.class).block();
        assertThat(documentFeedResponse.getResults().size()).isEqualTo(pairList.size());
        assertThat(documentFeedResponse.getResults().stream().map(jsonNode -> jsonNode.get("id").textValue())
            .collect(Collectors
                .toList()))
            .containsAll(pairList.stream().map(p -> p.getLeft()).collect(Collectors.toList()));
        System.out.println("documentFeedResponse = " + documentFeedResponse.getResults().size());
    }

    private void queryItemsWithSpec() {
        String queryText = "SELECT TOP %d * FROM c WHERE c.feedKey = @feedKey" +
            "AND c.lastMessageId > %d AND c.lastMessageId < @lastMessageId\n" +
            "AND (ARRAY_CONTAINS(c.messagesByType, {\"type\": @messageType1}, true) // optional " +
            "filtering\n" +
            "  OR ARRAY_CONTAINS(c.messagesByType, {\"type\": @messageType2}, true))\n" +
            "AND c.threadId NOT IN (@messageType2) // optional muted threads filtering\n" +
            "ORDER BY c.feedKey DESC, c.lastMessageId DESC\n";

        List<SqlParameter> parameterList = new ArrayList<>();
        parameterList.add(new SqlParameter("@feedKey", ""));
        parameterList.add(new SqlParameter("@lastMessageId", ""));
        parameterList.add(new SqlParameter("@messageType1", ""));
        parameterList.add(new SqlParameter("@messageType2", ""));
        parameterList.add(new SqlParameter("@messageType2", ""));
    }

    private void queryItems() {
        log("+ Querying the collection ");
        container = client.getDatabase(DATABASE_NAME).getContainer(QUERY_CONTAINER_NAME);


//        SimpleQueryBuilder queryBuilder = new SimpleQueryBuilder();
//        SqlQuerySpec sqlQuerySpec = queryBuilder
//                                        .select("id")
//                                        //.where()
//                                        .build();
//
//        System.out.println("sqlQuerySpec.getQueryText() = " + sqlQuerySpec.getQueryText());


        String query = "SELECT sum(root.propInt) from root offset 0 limit 2";
//         query = "SELECT * from root where root.myPk=\"CA\" ";
//         query = "SELECT * from root where root.id = \"item_new_id_2\"";
        query = "select * from root";
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
////        options.setPartitionKey(new PartitionKey("CA"));
//        options.setQueryMetricsEnabled(true);
        Locale.setDefault(Locale.GERMAN);
        log("tryng to query");
        String contToken = "{\n" +
            "\t\"token\": \"-RID:~21pRALdwV+gUAAAAAAAAAA==#RT:4#TRC:20#ISV:2#IEO:65551" +
            "\",\n" +
            "\t\"range\": \"{\\\"min\\\":\\\"\\\",\\\"max\\\":\\\"05C1DFFFFFFFFC\\\"," +
            "\\\"isMinInclusive\\\":true,\\\"isMaxInclusive\\\":false}\"\n" +
            "}";
        contToken = "{\"token\":\"-RID:~21pRALdwV+gFAAAAAAAAAA==#RT:1#TRC:5#ISV:2#IEO:65551\"," +
            "\"range\":\"{\\\"min\\\":\\\"\\\",\\\"max\\\":\\\"05C1DFFFFFFFFC\\\"," +
            "\\\"isMinInclusive\\\":true,\\\"isMaxInclusive\\\":false}\"}";

        List<String> continuationTokenList = new ArrayList<>();
        do {
//            options.setRequestContinuation(contToken);
            System.out.println("----------------------------------BasicASyncDemo.queryItems");
            CosmosPagedFlux<JsonNode> responseFlux = container.queryItems(query, options, JsonNode.class);
            FeedResponse<JsonNode> feedResponse = responseFlux.byPage(contToken, 5).blockFirst();
//            FeedResponse<JsonNode> next = feedResponses.iterator().next();
            System.out.println("next.getContinuationToken() = " + feedResponse.getContinuationToken());
            contToken = feedResponse.getContinuationToken();
            itemCount += feedResponse.getResults().size();

//            feedResponses.forEach(feedResponse ->{
//                contToken = feedResponse.getContinuationToken();
//                System.out.println(contToken);
//            });

            System.out.println("queriedItemCount = " + itemCount);
            continuationTokenList.add(contToken);
        } while (contToken != null && itemCount < 25);

        //AsyncDocumentClient asyncDocumentClient = client.getContextClient();

//        String requestContinuation = "{\n" +
//                                         "\t\"token\": \"-RID:~21pRALdwV+gUAAAAAAAAAA==#RT:4#TRC:20#ISV:2#IEO:65551" +
//                                         "\",\n" +
//                                         "\t\"range\": \"{\\\"min\\\":\\\"\\\",\\\"max\\\":\\\"05C1DFFFFFFFFC\\\"," +
//                                         "\\\"isMinInclusive\\\":true,\\\"isMaxInclusive\\\":false}\"\n" +
//                                         "}";
    /*    List<String> continuationTokens = new ArrayList<String>();
        List<InternalObjectNode> receivedDocuments = new ArrayList<InternalObjectNode>();
        String collectionLInk = "/dbs/" + DATABASE_NAME + "/cols/" + CONTAINER_NAME;
        do {
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
            options.setRequestContinuation(requestContinuation);

            //options.setMaxDegreeOfParallelism(2);
            System.out.println("\n\n----------------------------------BasicASyncDemo.queryItems");
            CosmosPagedFlux<InternalObjectNode> queryObservable = container.queryItems(query, options,
                                                                               InternalObjectNode.class);
//            asyncDocumentClient.queryDocuments(collectionLink, query, options);

            TestSubscriber<FeedResponse<InternalObjectNode>> testSubscriber = new TestSubscriber<>();
            queryObservable.byPage(requestContinuation, 5).subscribe(testSubscriber);
            testSubscriber.awaitTerminalEvent(40000, TimeUnit.MILLISECONDS);
            testSubscriber.assertNoErrors();
            testSubscriber.assertComplete();

            FeedResponse<InternalObjectNode> firstPage = (FeedResponse<InternalObjectNode>) testSubscriber.getEvents
            ().get(0).get(0);
            requestContinuation = firstPage.getContinuationToken();
            itemCount += firstPage.getResults().size();
            receivedDocuments.addAll(firstPage.getResults());
            continuationTokens.add(requestContinuation);
        } while (requestContinuation != null && itemCount < 25);

        System.out.println("receivedDocuments.size() = " + receivedDocuments.size());*/

    }

    private void queryWithContinuationToken() {
        log("+ Query with paging using continuation token");
        String query = "SELECT * from root r ";
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setQueryMetricsEnabled(true);
        String continuation = null;
//        do {
//            CosmosPagedFlux<TestObject> queryFlux = container.queryItems(query, options, TestObject.class);
//            FeedResponse<TestObject> page = queryFlux.byPage(continuation, 1).blockFirst();
//            assert page != null;
//            log(page.getResults());
//            continuation = page.getContinuationToken();
//        } while (continuation != null);

    }

    private void log(Object object) {
        System.out.println(object);
    }

    private void log(String msg, Throwable throwable) {
        if (throwable instanceof CosmosException) {
            log(msg + ": " + ((CosmosException) throwable).getStatusCode());
        }
    }

    static String longString =
        "wiqkclagxrdqclmvuzcsomihrkxbbikwypqrcgrhpgkylztdxxirzwwkmleovdrikggqupfcclpaowyetbywvbeyegniacruvzncxflfzrwnhtdubzrezefqhtyznagotfxtcynnyderhryvbtpoxbuyfkkwsmoydfmglwzgqobraysfidipfsybbgsromwwiuygkuzbitkwzvzuenfjvxkklcjomrasmddllqfiqmgmcbmnmikpvyzbdbuitfjuohwbqfyueqjvbwehgwhosooehtglncoyiundhhqrsbkazhuxjzqoteouwexvzchkhuukpkqkdlkfhhqbgcvrhzizljrkfujebedsfdrvzdvavxrwpkyqvnvbbqvoylvxejetqvzqaakrsvbnilfngjgdavkdwfgxqvhjatrccvsbjpueenjljgouzoqdweckcjjhfiloefucsmloniyklseuztfhkzufbzntnvlaziaqqoayilyxssmfafvawxxnhyyaosnlsujsemzwvpirnbkjtfbggaamzcocdvsebdkxkyfvkakvangpgmrqqctlrbblrmmwchqliypeuuqtbiozgtfjtwjdhhowetaltnlpwqokckxpbfrpyshcygzrxdzhnffjagppnaeyrzuhrofoyurrythnpefgtiybfmckzwvsgntsudnqfqcudeporevbhgvlkaavdypfiahusbzwcgsigspfpxlqbxhqfzbrrcrgtlcbokvrtrghobknoxcaelnhchwaekpqepimanaqagajdpzbsaoepnkkfrxsavjkpxeawhrhacnnutphuzngxnpzrtujcfwvmhxuskdmmoyjdtrmqvbgehwpfseiyjsmugjpcbndigkufynwnuolasltqxfirxvkqcibculpqbarejhtufquoqzvvpwfszzpsjovmajrfviuozdscztuhavlszbdncrxdsmoikkmxwenfkqoomdzxbvw";

    private void insertSimpleItemsForRead(int itemCount) {
        for (int i = 0; i < itemCount; i++) {
            CosmosAsyncContainer container = client.getDatabase(DATABASE_NAME).getContainer(READ_CONTAINER_NAME);
            try {
                String id = UUID.randomUUID().toString();
                String jsonStr = String.format("{\"id\": \"%s\"}", id);
                System.out.println("jsonStr = " + jsonStr);
                ObjectNode objectNode = (ObjectNode) OBJECT_MAPPER
                    .readTree(jsonStr);

                String finalMyProp = "";
                for (int k =0 ; k< 1000; k++) {
                    finalMyProp += longString;
                }
                objectNode.put("myprop", finalMyProp);
                container.createItem(objectNode,
                    new CosmosItemRequestOptions().setConsistencyLevel(ConsistencyLevel.STRONG)).block();
                System.out.println("Successfully insert: " + i);
                LargeDocuments.documents.add(id);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Finished creating item");
    }


    private void insertSimpleItemsForQuery(int itemCount) {
        for (int i = 0; i < itemCount; i++) {
            CosmosAsyncContainer container = client.getDatabase(DATABASE_NAME).getContainer(QUERY_CONTAINER_NAME);
            try {
                String id = UUID.randomUUID().toString();
                String jsonStr = String.format("{\"id\": \"%s\"}", id);
                System.out.println("jsonStr = " + jsonStr);
                ObjectNode objectNode = (ObjectNode) OBJECT_MAPPER
                    .readTree(jsonStr);

                objectNode.put("myprop", longString);
                container.createItem(objectNode,
                    new CosmosItemRequestOptions().setConsistencyLevel(ConsistencyLevel.STRONG)).block();
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Finished creating item");
    }

    static class TestObject {
        String id;
        String name;
        String description;
        String country;
        int intProp;

        public TestObject() {
        }

        public TestObject(String id, String name, String description, String country, int intProp) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.country = country;
            this.intProp = intProp;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        /**
         * Getter for property 'intProp'.
         *
         * @return Value for property 'intProp'.
         */
        public int getIntProp() {
            return intProp;
        }

        /**
         * Setter for property 'intProp'.
         *
         * @param intProp Value to set for property 'intProp'.
         */
        public void setIntProp(final int intProp) {
            this.intProp = intProp;
        }
    }
}
