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
import com.azure.cosmos.implementation.guava25.base.Stopwatch;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class BasicAsyncDemo {
    private static final String DATABASE_NAME = "perfdb";
    private static final String READ_CONTAINER_NAME = "perfcontRead";
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
    private static final List<String> largeDocuments = Arrays.asList(
        "a659e646-6aca-4a37-aa30-18a1766284d6",
        "a62a0e06-4750-4859-b0e6-99e7c12eda0a",
        "84ed2a9c-7cd3-4329-b167-80788727179d",
        "7aae0302-e378-485f-b41e-4b2aabc9714d",
        "6f4e84c3-0420-42b9-9b97-6dd059c87dd4",
        "2f3026ab-52e0-454f-b8a5-30e14e6defaf",
        "7fb8835f-bfaf-4baf-af75-67518ef3b8af",
        "911523ab-002b-4c6d-a7a1-876c7273869d",
        "8df2ec2d-7dee-4228-8fdc-69ffa5b088ac",
        "2d6f632c-e4f4-468f-b57a-4c824bdb5f3a",
        "960dd587-555d-48fd-9e3c-dac2fbc044ae",
        "99b8507c-3bd2-498c-a048-a3accec9ead3",
        "dda690d6-1b10-42bf-992d-bd9591fb9503",
        "33ec056c-ecd8-498f-8840-fd9b099e7708",
        "d99753b4-931f-449b-85a2-649feddd8c99",
        "bb43af40-6564-4c5d-aba0-4d1208bdaf59",
        "91512c6c-910a-4016-87b6-d6afafffff7b",
        "da49dbd5-0ba6-411f-8e1d-e3328d3648f4",
        "190672eb-017f-4b3a-bf33-5c02f8a557a5",
        "e2ed7f43-60d0-4691-9966-af8058a50c68",
        "10d057c1-3817-43a8-b8dc-ddb96c7a123b",
        "37ed2452-5b6b-46f4-8f76-a6d46c196709",
        "7e57c308-85fd-4535-8a35-d58ef58268ce",
        "0271511f-3312-4576-9232-fe8013cea3d5",
        "ab5c6070-f50f-43dc-88c5-eb1af7ded5f0",
        "f4018204-add1-48dc-9a8d-65b77ebb354a",
        "604a6a37-b1b3-471f-ba1a-41f1a3d4c57b",
        "a8a31535-f6b0-46ff-9d9c-f382dbe25c11",
        "e65c0410-e1be-4069-9c0c-98df9fa6ccdc",
        "7be1b735-eb5a-401e-a23a-729b3b204a65",
        "21fa849f-ed62-47a8-baa9-3b6346f0943d",
        "3f1bcbc5-07fb-4ee8-92d7-fc2704932911",
        "21a788ac-161c-4a04-8edf-665992a6673a",
        "45fc59b2-dae8-4b19-bb94-6d9e9e01f7ec",
        "4ce96512-069b-4276-a4a2-36ff1f7980b9",
        "660f5514-0a17-46b0-9632-ce4dc42c8c78",
        "d2a7111a-8d35-471b-a936-ac239352641a",
        "6f21fea8-b39d-4354-a2e0-20ce5486128d",
        "0546d748-e857-467c-b69f-fd6b78360bc7",
        "085827e3-7fe0-42c7-88f7-336e45b72142",
        "4d1b8654-6e9e-4f0d-bb70-6d2900b8f258",
        "2d201e24-97da-4f51-bfbc-9d78d8c7fffc",
        "668eea1d-b10e-44c1-a847-dc315bf580b1",
        "3e7ef58b-ace2-4942-ae22-728732dc6ccf",
        "c9f0f899-d492-47c3-a84a-f259e5c8d50a",
        "331e13c6-3cbb-47b8-8093-d72f92bcac3c",
        "572538e4-81a8-4c8c-9e65-a9b2e6b6c9a4",
        "8ca09781-20a4-4235-a0f4-f5fbcb631ae7",
        "46c37e95-9743-4573-9a4f-30ea4356b4ee",
        "ae0fb4fa-9aef-4f94-8249-1fc9158cdc79",
        "0a663ed1-a2d7-4907-b3d5-614641e91098",
        "b106a1dd-0aa9-40ce-8ba5-05cf64f91399",
        "cdc9a8c5-f7f0-4aa7-82d1-285a68314512",
        "b2f62a89-eae8-4546-ade4-88c7ff20ad7e",
        "c181d425-c3fa-4e0b-a18f-f8e559ab7364",
        "2f1a335b-e99f-4a7b-83d5-9930ba7f54ca",
        "42ee07eb-caf2-4863-a307-f875aab27011",
        "37f57255-8572-44dc-a54f-8052a74ac721",
        "98637937-dba3-4785-9fde-e20cb1bc067a",
        "49f6f3fd-cb5b-4cbe-a6c0-6abe8015e9c0",
        "683de84e-55ce-4a0f-8e4a-9c854e72948a",
        "e25cbd6e-568b-4f4b-a3c2-25c357012d4b",
        "79f15a2e-03fe-48c5-98fc-5072b8762089",
        "2af68c47-7126-4178-9c5d-19767e643d03",
        "88f439b9-5126-475b-89a3-d8685b78ee16",
        "4e7cb304-c631-440c-b304-90fdb60f2e68",
        "205398dd-b3c2-499c-8596-c5ab2a2ab8ff",
        "bb400577-683a-47ae-ae10-ad9c5ecd2b9c",
        "2e01e1f8-f05a-441d-aeb9-f3e442989aa4",
        "84fc9fb0-3c6f-4cc5-bd6b-217325427093",
        "a7bf4cdd-9045-4ae2-b5f6-0215519ccc6d",
        "ff7e4392-d588-4c18-aea3-70cecfcfe000",
        "715886a8-7481-4b3e-90ee-f0fe8005d6b0",
        "7775b8be-9866-4631-943d-fa9467b23eed",
        "48b6f104-07f5-4b17-a3af-4ee34f4a8ae7",
        "558b5ce4-90be-46cf-8a76-31b43140868a",
        "c57ec928-b8b0-4f71-8a4b-b5b239e74252",
        "5d7e8600-e181-4d7e-acc2-768285005ddf",
        "e17ce07b-7404-4282-85b1-2f163d63f4c0",
        "aab1f2cc-811c-46b4-8217-381a0ebc598e",
        "337eea1c-c00d-4496-b4dc-b4e524075bab",
        "445cebf1-e69f-42d6-a0b6-4b43d272b3ed",
        "f11a6bae-54a6-45b6-9b43-da45e7070d92",
        "77c0613b-d64a-483e-b76d-4afa878c75a1",
        "2baa4b12-8d4c-4e4a-8f4f-30a450bdc1f1",
        "ee4f75d2-4e4a-41a8-86df-10bf64fd8ceb",
        "54021b78-4938-4e94-9e2d-8cc41677ac7b",
        "4b9d4b18-b08c-4375-87f8-ac612c365a35",
        "6d983dd4-69e6-4dd2-a9a2-3293dc87eaee",
        "a3bb4ded-b647-4a6b-bd6c-e559412fb499",
        "d01f5c08-7064-4e1e-8232-711fc3e2fee1",
        "dc8c754a-069e-4cee-9e6b-277233a3e08a",
        "a3958764-ba42-4c2b-bd11-130b42079d08",
        "b5413651-5236-424f-ba5b-9712152fc812",
        "245f7c3a-5ee9-4b2f-b6ed-ef99fe70a0bc",
        "7b813579-d882-4c4e-b195-1f309511fdfc",
        "ba36dfef-b337-40b8-8a90-d9fde585ebe6",
        "a6d48325-9eaa-4b77-b19e-2275f87672f1",
        "09b78d8e-973b-4231-a773-28a8e2dca213",
        "14e08024-9c07-4b99-abcb-28fc5aceb44d",
        "28c20b3b-2c5b-4f0b-9045-9eafa91608b3",
        "8943bb65-7305-41b9-a10d-c21d70ebb032",
        "c48f22b7-ff6a-45a7-9f9b-9e05e0f58d1a",
        "707a3bfe-d1d9-41fb-99e3-4eb470014f2c",
        "3ce3d687-4074-46bd-93d8-0fcda0a2ab06",
        "dccab1d5-327c-4ac8-9d4b-ae585a1def88",
        "2c1f35b7-89ac-4ad6-87fb-ddf33e822053",
        "6e060261-0737-45e4-9136-5a9a6516d3c1",
        "1b88056b-0617-4eab-8c58-7273876baa2a",
        "5c69a9ce-d450-4a70-b35e-73e9cd3584b0",
        "d63f0503-d73a-4cdc-8877-2eb84df9a247",
        "0a1f4914-8daf-46de-9dd1-23f099d8bb3c",
        "41d72b7c-a179-45f8-aece-9747acddc264",
        "66837bd3-d22f-4560-9775-09ae566c0de3",
        "ba68790d-1006-43df-9e17-aaaca8cb5b56",
        "ced26ff3-c58a-4cdd-86ab-375583314a8c",
        "4495c560-4800-4c82-8d44-e4bd4a06ac13",
        "f71442ab-10f9-4e8e-82ef-e2f4b389e0c2",
        "e3a88bff-3490-4598-ad59-d8ca32ccf7b3",
        "e3db7516-e4d8-4bfb-aeda-9eda9d0c1da9",
        "0ced85ae-7079-46a1-938a-9cd9a7bf23b4",
        "329dced2-c7cf-4a2d-ac97-92574558afbb",
        "84c032ae-02ef-4826-9aab-4c62232d2498",
        "008cf061-310d-42dd-abf4-155692ba8181",
        "0cf1b07e-0493-4700-a9b9-69ced628ac3c",
        "1e52408f-d74a-40ed-8316-19ec6ddce295",
        "a91c7e3b-2719-4c76-91b0-4ff4d46a1716",
        "0f9988fb-707e-4df4-9d7b-7f8239133cc2",
        "a7d50b07-c5e0-4367-98a5-b1eda7a7d328",
        "7f510728-73d8-4d5d-b6ac-f48113eea3f6",
        "52aad9ce-c904-45a0-99f3-016392c5f585",
        "3127d584-737a-4e06-b1b0-3d5c8a8e7813",
        "0bfc676e-e6e5-444e-a0b6-1acd1e227e33",
        "1f5e572d-008f-4555-9b06-df0dfd0f02cb",
        "d6197853-a468-4c3c-afc6-8abd2b9b9ba4",
        "6d315958-f613-4797-aae2-b8d3e5aaeded",
        "c7a5b130-abbc-4aa2-a920-85079f9dbd4e",
        "da9155b4-06b3-4a2e-8401-7b7c5fb92cb2",
        "ee03c9fa-dae8-4ef1-ac44-d5e40b7fed77",
        "25472b43-e986-4cef-bca3-1c91ead792c2",
        "a4ce69ad-b8f1-4ef5-978e-ebfcbd560898",
        "4c3f26c5-3f3e-43a4-913d-96d594a201a9",
        "0f76ad25-70f7-40e1-8d5a-41ede9248b76",
        "f086b8b4-77da-4efe-b6a0-e5a457ab6b4d",
        "eacbeb35-3f6a-4d67-87de-72bb9efa445f",
        "9c65277c-54c6-4f05-bab9-b1cd7aea5ff8",
        "ada3ebc4-f089-482c-9b36-18535354794a",
        "42149871-f935-4b0e-ac39-6d3a532056ac",
        "f107cdbb-8721-4c60-806b-ec03c841cb16",
        "6b4afd50-7980-41ed-b16e-e90addf36675",
        "97351cf8-bfa9-4e45-a140-e5880875029f",
        "48d591a6-5cc1-41b2-9ccc-46c52f0bde67",
        "7b59380c-89a0-45e0-a9bb-1adbe340efcf",
        "d72be51b-e3e4-436e-911c-55830e3c6b99",
        "6cd2758a-1860-4ade-81f7-704e170cceba",
        "6f8a113b-2eb9-49fb-a8d3-245c37f1b61f",
        "ada2f0da-9778-45d7-843a-8a40a8e2386b",
        "29d306aa-dbfa-4e1a-86fa-c6b492b3d34d",
        "bc61e67c-e9fa-4a20-b8d1-679389f8f9ab",
        "585bde27-9e2a-4f7f-a417-eeaa110476a4",
        "5588013c-cb6e-46b3-8323-fdcaa5af920d",
        "f39febc8-841f-4e09-b91b-a2cebc3b8cbb",
        "ade74d6d-fc5a-4bd1-8177-c7507baf745f",
        "ed5dac58-181a-40d2-92d7-337f7b2a3212",
        "40a56796-4f10-4c36-9be3-ed5df793eb5d",
        "8b28bf97-d754-4d70-9acd-5b3397f6436f",
        "182e047b-3ced-4a46-8c39-1eef1bb3aced",
        "6197af80-01b0-40b5-b091-b3fb3d91dc71",
        "875355c2-3965-4a5e-a8b3-d086de0b64b0",
        "ab248e93-0ac9-4e64-8fd8-21859dd896e3",
        "7ed4ebff-7876-4672-bb7a-e7369a532a76",
        "ac5907a3-11fb-4a35-b8d7-1921f4851c36",
        "f933ffba-fef9-4c7b-809d-ebd6d9f590b5",
        "0b4cd6b8-21e0-47c4-be14-617b4433a49b",
        "f210f3e7-deb7-467e-9088-088dc196ad61",
        "f3719adf-ab06-496e-ac3c-9f79c1b97573",
        "6a417eaa-7677-408b-8121-967636eb9ad1",
        "ff9e5f2e-f68b-4040-aa05-2eb674d388e9",
        "9055d9fb-fef4-4287-bfe7-d920c0891c57",
        "36737c7c-353c-4897-9597-9edf7470f1ae",
        "a982fe86-0722-4ad9-b2b1-ef17436d6ac5",
        "d49014b4-ed5c-42ed-88d2-bef152f787e4",
        "afdfd7e9-fc71-4997-b9f5-8aaa9935e620",
        "a56c68af-b7a7-40e9-ad83-6883afe7763c",
        "8e0da1df-e76c-4e28-bc30-2784af58cb74",
        "e604bae1-4e10-4c2c-873d-8ae76bd584de",
        "3b983c1f-b1f7-49ff-a78d-0b07a3aa61a2",
        "48b5f735-21d3-4d0b-aad0-1b791119c91d",
        "cf47f8d6-43e7-4036-afb4-fc643abbc403",
        "0f8f8330-b84e-42c5-8ac0-39d2d1542a9a",
        "9bbcc496-9e2d-48e3-a6cc-9ec9ec02ea15",
        "a5307cad-49df-462d-bc6d-cc1249b0b902",
        "95db944e-5205-44bc-99fa-bbe734a4891a",
        "ef5790ed-5b33-41cb-8b9d-5dca0af78342",
        "6ed84ee0-087a-4b2e-9f2b-a0428fa2b4a1",
        "b25f7abd-db43-41b3-95bb-41c9b3cb16da",
        "dd8f9f77-d9cf-4a25-875e-80f492fa4af5",
        "ea7a3461-3555-4434-b222-967d2cfc9976",
        "f8b72063-e549-4279-bab8-54903b7f375a",
        "b08f8985-50be-44e0-839b-80f90ea1eff9",
        "c63e4717-1215-4a9f-82df-7535486f34a1",
        "e680390e-222f-4242-8f24-575af500bf82",
        "a8358205-eda4-4d94-924a-8a7ea2e8d6af",
        "f65d6894-a576-431f-bf3f-bac17d5c70de",
        "3a92c00e-7fcc-4add-946b-3fd6764997ee",
        "c17cd5ae-46d2-45eb-b78a-3aecb8f6bf94",
        "55a4a0d9-8da0-4a6b-b22d-eecb024689f6",
        "64617066-8e8f-4915-87ea-4eb2bae5be38",
        "9b54ab40-2c87-4b85-9ba3-326ebd86af79",
        "ede39d07-d0f5-4f25-bab5-ed4e6ed62953",
        "f2a360d9-ba0c-4ac6-a999-fda0499b93c1",
        "65d1a26d-1523-418b-9e34-7dfd17e9491f",
        "99222336-5d65-44bc-b10c-59f57a97b251",
        "24ca22bf-9549-4e36-964d-ee543ec7d779",
        "6f8cb564-512b-449f-a554-4a6644b77c74",
        "57341688-99f8-44f3-ac6c-f1f4c6d74efe",
        "6b2342f5-4857-4662-b29f-80cd3a368067",
        "954cc2cc-0f44-4836-bbdd-bcee0bd0341c",
        "4debaf50-eef9-453f-96bd-1f7a90e37916",
        "f3d64a19-0fb5-4140-87de-5ce9d88f4af5",
        "62b133fb-e04a-47c6-a8b8-26e0d50da374",
        "ea3a07b2-1ccc-4f18-8012-e3f35f2c95e8",
        "53cfc48a-dfc0-4acf-919c-791bbe2201e2",
        "07210335-89ba-4ae7-bd69-6ca98beee1ad",
        "97fe1527-03e9-4831-88d1-d6d8dbe11176",
        "68c29067-f720-435d-899a-12bec2e33d41",
        "1c891a4d-603a-46f0-80b6-88ca65fe6df6",
        "e1be2829-0ceb-47be-ac38-0196ef4e8ba5",
        "6e866de6-ace0-424c-a87d-1cece2ee8ad6",
        "9f325331-124b-4e7b-ad28-ef8fc5720243",
        "f66b1490-6ce9-4a73-a762-ff870ea9425f",
        "ee5188f2-f097-4240-9ca7-18af6c23daa2",
        "7b7405f9-b048-4386-890d-83cc9da4b405",
        "f73995bd-8c1d-46de-a6eb-2c4852e7cff0",
        "a71e3a3a-73f6-4a0b-aa4f-5101fe7d23e4",
        "318dec78-3ce3-406c-a44e-a2abcd886781",
        "ae203d4c-2a58-4ff2-9159-a6159ec12caf",
        "511fff00-0b0c-4dc3-bbb0-d1f76ddb6f70",
        "bd4be57d-dd13-4682-b99e-8677fa535d3a",
        "7311930d-4173-48f4-9add-169293b07095",
        "d6ef8cb9-efbe-4809-9b62-79f7290e93d5",
        "5f27ca5e-c662-44cf-8cbe-3341d22c012e",
        "ca7a2035-01d9-4dbf-86c8-22c218c3210f",
        "55039978-8121-49ef-a995-1b60927cd11e",
        "91f3cb67-e5d3-449d-b4e1-e1402bf996c2",
        "66cff181-23ad-490a-95ab-92e98384cd70",
        "eeca2682-eb43-4a06-8f0c-b52b12a54e8a",
        "1224504e-691e-4424-95de-eb3802987d4f",
        "30be2956-783e-451f-84ec-7e64d7d379ea",
        "031854d5-3ef6-492f-b182-c3db10406584",
        "115b1492-acb5-4e7e-abd0-5bcbd8f5de70",
        "601ebc4b-6966-4e16-b7f8-4226e07f4beb",
        "52e0e546-fe20-459f-ab1c-9be5eb21dcce",
        "8d25bbfb-c46a-4286-8962-207a48ac9984",
        "97b996e9-a4f5-4d31-acbb-664b408eff8b",
        "67bea464-a1ac-48ab-bfa2-1a47629b6235",
        "56ceb597-19c9-49e1-9665-ce48c7be8f98",
        "b03fcc5a-6877-45c7-ba09-8295f4863dcc",
        "7d3b067d-174a-4157-ae57-11e80c18bd73",
        "697b53d9-d9ae-471b-b70e-e26246296440",
        "91698536-22b4-4c13-bed7-f64213f8fa05",
        "2302c05f-f660-4264-926d-fe714955f973",
        "480afa08-c1d7-4aff-ba34-46e947f7cc7a",
        "251ca5de-4ae0-43f4-b9ad-41f4be704fad",
        "945b0a99-be73-45c2-83eb-c877c62ef09e",
        "ede94eb0-f7f2-4b0f-89a1-d6d62eeb0301",
        "522ab4b3-31e3-4dc2-8f7a-b9ee2ae2d400",
        "dc756978-197b-4d02-a5b8-0f8af57f2440",
        "02022891-7f9c-4c0e-8202-f8e855b6ffea",
        "28f7a82a-b771-4417-9b4b-bf944145d4ac",
        "357e9535-245d-44e0-9184-94be719715c7",
        "4e3b2016-a9fa-4e37-902a-297cdc153988",
        "d48a45eb-5870-4993-8031-0c1d842ca0d4",
        "7cdfc660-baf2-4da8-a4e3-94ae91b30c5d",
        "49042c52-a5bd-43a0-acd8-3e52af41bad5",
        "7d0e7e29-dbdd-4996-8f31-e9b3d39e6fb2",
        "22f615ac-3d83-4dcc-9a83-88f90a6b2a48",
        "18bd1309-5119-4fe5-bf35-b34b9fcf9728",
        "8d97a474-0e09-47c9-9957-51fd4c9bb6dd",
        "783a165f-eef4-4835-9cad-54a9be286b80",
        "6f90ef1a-c2fe-4638-9985-37367ae20d06",
        "8311d7b0-0f5b-4501-be86-86a8cac9ef6b",
        "7f0d15d6-b536-45cb-9370-e14101cd6568",
        "5031adc8-7b9a-468b-bee7-0e8d2ea6e043",
        "e474c2ad-fffc-4648-ab75-90bc9bae4910",
        "66ab5e3f-a339-499f-a610-c68088833864",
        "5d3a2378-c87a-4ebf-bcf4-3a1c0f7a77f9",
        "60378681-7a4d-4214-a354-90ea7c18421a",
        "4fb3cf93-55c2-4779-b2d6-f494fd9d5e59",
        "2c16ab6b-efbf-4195-a074-12099068323b",
        "c0ed4109-c25b-4523-a7ba-b9b2f4ff0846",
        "625ad5b3-a28c-4c37-bfc4-c59157c3eb52",
        "11c11a77-e27c-4b5d-a189-20880b3ca789",
        "e9e0282f-3ff4-49bc-ad94-4e07ffbcf1ef",
        "a123d0d2-3b7c-4ad5-b1d8-3b63a7eec2b5",
        "394b8879-a177-425c-9fda-55c7821158c9",
        "78c326fd-31bb-4361-ba03-7cd9b6db70bc",
        "cb31ce83-15b7-4a18-a3ea-b32d2fdacad1",
        "cb284ee4-6425-4788-9c7a-13824f98a232",
        "f32149cb-f487-4b37-ac17-15030ebe3e57",
        "28b33e87-43d2-4d8d-9b55-a7fae61c46cd",
        "b034dc99-8abd-46de-a1fd-656827a49091",
        "978de917-5681-4311-bf6f-6f4cff81bd15",
        "1599e2de-872d-42bd-bd80-9baf2f63398d",
        "4947b452-85a8-4595-829d-ea1a0be700c4",
        "8bf729f9-9d64-4b08-8a9b-1ab73e96f881",
        "cf0a7811-c3a1-4cef-92e2-e8fa945f9340",
        "57a6d269-3048-4242-89c6-f1d0c425fbba",
        "ce5b5caa-15e2-4ef4-bb5b-8232a4bcfe6d",
        "2002ccd6-f884-4779-8ed0-1210fefed90d",
        "d8b9fd89-5607-4026-be4a-f1d0b9aa72f1",
        "9a5b6096-4e4c-4dcf-9b3c-3f372ff1c882",
        "afa0adad-79a7-4b2f-850c-dabe51f21935",
        "1ae6bacf-cbfe-4463-b312-df4e843fc977",
        "cb176a99-f1f7-4fc2-9412-c7f757335b69",
        "9ce91dbe-70a1-4d9d-a03a-8240fcd17b66",
        "b60e2531-ae80-4fcf-8f12-a3340701ee2c",
        "a05e4c82-1b58-4479-9f2c-ce9516959e07",
        "82685cf9-f224-4ea4-a141-3c6c7222ed21",
        "d78f32c9-7fe8-47fd-85b6-fd7b8c72ea27",
        "d4feb33a-36c0-49f6-beef-cef812c47544",
        "3c7482ab-e467-45e2-b0cb-f59fd02df085",
        "2e579e25-f025-4da6-9cdf-4b8fed00c47b",
        "038f9de9-1a04-4b5e-b28d-5e7aa79aff40",
        "fbb3c8df-aa0c-46ef-b57d-b45683fc2166",
        "4bb4906e-90dc-4644-b38d-c9dcafdb47da",
        "fb663d29-c8e5-4ab4-bffe-25055a0d17d6",
        "62559e5f-44af-4992-93c6-380ecbbf274a",
        "41937e98-8f74-4072-9a3d-0b6be08028f5",
        "eff08f48-1333-46dc-8e4a-2f71154e6c3c",
        "97225d69-1570-4894-a273-e437f53c257b",
        "0a4d7695-87bb-4e7d-a741-34ed18707649",
        "bab83a83-b459-4451-8278-9edd0bca7f77",
        "6f21c4d4-ae63-42fd-a277-1a51a0a7f773",
        "b9e5aec5-184b-40ed-acc1-efb201eb0d38",
        "0e970d5d-50e5-44c8-9c15-a3d1f102fb04",
        "e9985675-434e-4745-862c-ccd916f3417d",
        "d0f072cd-9b89-431e-947f-d56c54df57a7",
        "026a8523-2835-4e50-ace7-038f7e6d5acd",
        "65ca5cc9-0143-4f12-ba3d-e56759b3d6ab",
        "883eb51e-ad08-4ac4-a5f2-4e14904e4e48",
        "1e02b029-b7e7-4a73-8864-9268f70fd49f",
        "823573cf-4173-4681-9c5e-a1094ac34303",
        "b702a435-6b97-475d-95d4-cb75ddf5abdc",
        "96f0c818-d5dc-453a-b03c-7a4386d86ba4",
        "321d994e-002c-4bc0-907f-032d10ace8c9",
        "8a4a8ec8-9dec-4a32-ac6d-53074bce2430",
        "8e0a6c8d-1705-46c0-8979-1573ab6368c2",
        "79a67e57-259a-465e-80a6-bdc4697cc95b",
        "3efbd3a2-d205-45ae-820c-62fd4518957e",
        "aa44b92b-27a8-4f06-916c-0e4c1066f4d6",
        "59286c24-7923-4abd-b7ba-d3ad01c89786",
        "a226596f-ed5f-424e-bd61-383e2b27610d",
        "3cf5958a-f84b-459e-8eff-58771232cf67",
        "12c36cca-24fe-4d49-b5b2-fa4f56ec61f6",
        "c47e9d21-3a5d-4b6b-88de-fac628fa03d1",
        "43c5a534-69e4-4deb-913c-a6326cfd6f2b",
        "477069bc-4979-4c94-a793-ab9e2ac5c619",
        "c0e284ac-35a3-4cec-9e1c-cc8939b0ddcb",
        "a366bbd3-e96f-4138-95ee-f0db9ab68540",
        "32891559-d6c6-4040-87d7-8fe95892ff0a",
        "d0c91ee5-db8a-433a-96a6-bce63cdbd922",
        "8f354207-0c89-488d-817a-7079221074c5",
        "9cb6d131-af94-46f6-8ae4-88667facc1db",
        "1e24cd02-477f-45ac-b5c1-63464675889b",
        "5ce54aa8-ff0d-4bdb-94bf-2415120475f7",
        "1459e43a-f96b-4b83-95c5-bc5669eb206d",
        "5d9a0ab3-e118-48c2-b5c0-a3ed3bccaac8",
        "38388067-48c1-41b5-8ac0-f19c0536e1cf",
        "aa0e13b8-9ca9-41be-b05f-8371ab301767",
        "52aed7d6-99bc-4b2d-9d66-b85558aa6ca8",
        "62b7bee0-2056-41ca-9268-c1a317d3793e",
        "c6517f4e-29b1-4e64-825a-99d442a54a80",
        "3c70f6eb-5482-4161-b548-4e63dd1ca657",
        "130f88ae-78df-478a-be34-ca9bc19bd088",
        "c7c3ddfa-7e9b-40bd-a55a-51255898d0fe",
        "dc7bc127-67ef-4a89-9b6b-acec6c737510",
        "bd133332-99ee-4d15-9084-854172d91711",
        "43ad2e7a-bbe8-428e-b18b-22e3e4f9df00",
        "bfe9848f-f9d0-488a-8ebf-7126be98be7c",
        "bd3d3e2c-7181-4c0b-aef4-65f72b52bd3c",
        "b7b7c821-6d41-40ba-86e8-7c0a0b807b73",
        "4f89e846-b090-4329-8b21-039e7a1cb477",
        "6e439110-ea70-45e2-b322-629d34ffaa0c",
        "00236b4e-e121-4859-9c4b-2b602408c387",
        "83c2da58-e8ed-4f3e-b6af-88dba8ab2483",
        "f9adb480-3cb0-481e-90a6-1278d305e320",
        "bd66b107-6b42-486b-b094-5c9983fe8a5b",
        "a02f51c8-35a5-45f7-b331-052a9af575a7",
        "e135418d-da5c-4f78-bf22-a02dc5d2b832",
        "0b76608c-4493-436e-b47d-dd5db0327afc",
        "77ab1503-f943-46e4-acd4-03552dc62f25",
        "25629c08-7707-401a-a4f4-df4a8506d3de",
        "53289ed9-feed-44df-bf00-e707c5b01ff8",
        "fc780528-c1a2-41bb-a328-e7c0f049a4af",
        "92180bf2-0db8-4043-be5b-564923f6fca7",
        "0d0d1a13-f4a2-4a36-ac7c-f246bc90d8b9",
        "b56eb0bb-4e79-4a3a-98b7-683369f0831a",
        "1661b452-b479-4ade-88a2-451f7c44ab42",
        "332878bd-83f5-44a3-9b2b-83ccc69c6542",
        "e30b9935-93ec-45ff-9fb3-9cf7518ca874",
        "b7d5f578-8cfb-43b3-9fc0-e09f0ef60ba5",
        "0fb306e0-2eca-4dd3-8d76-5c917ba6f8d5",
        "10621c42-13ec-49b5-a573-59cd11a320f7",
        "ede63a1a-57fc-49d0-a375-1e091e36c32b",
        "701c4b76-2932-49bb-a15f-aed4df27c2de",
        "df8fa56d-182a-4e41-9f66-44d27551560b",
        "7586c537-654a-485d-bbc2-8eafdfa18eb8",
        "3a011323-dabf-4f92-aab5-8d7a05a0f866",
        "1c0d4ac5-4eef-4998-bfd1-619a1515c197",
        "e9ead1c6-c82d-474f-b60b-18afb6368bb4",
        "48c798b4-ac75-47c7-abd8-307a3af2841c",
        "ca532e3f-5556-40c9-816e-bdfc41533e0c",
        "5e637d19-8897-498a-8f00-d268d5e88f95",
        "147a40bc-9f32-4be2-931c-e7c3eba411eb",
        "1784a4b3-3927-4daf-8aa3-cfa41a1e3dd5",
        "c1dfd28a-50e9-4a63-b843-860d5cef319f",
        "c6128db3-bc2f-4055-9d43-cfa7369f179b",
        "4330ae52-1bdc-4357-a627-a618cb749bf3",
        "237f2159-6e59-48c7-a0dc-a236a6cc17ac",
        "7012491f-ea41-4f54-94ac-8de27c761959",
        "c07242fe-1d9d-453a-9590-ebcb4bc7854e",
        "296dc904-a61e-4983-bfa4-8179ab6c22d5",
        "7a3bf1b8-f466-47ee-b060-da2372f2f15b",
        "8558c88c-f2ad-463e-ba08-dbf30286b6ca",
        "800aff05-8eee-4dd6-8ebb-cf08343bc276",
        "9c9f1030-f377-4da3-805c-faa85f843af7",
        "bd8a9c0c-b30a-4906-9838-b20483cac0e9",
        "87250f1c-e6bb-4ec5-94b3-625f340ab6f3",
        "e3c92800-018c-4e0c-bca5-910144cbc797",
        "d59b232c-f8e2-4bfd-ac29-c381c44b2559",
        "0c422046-e4ea-4816-9047-9abca800ff71",
        "fd0338f2-e1c8-45c9-9b9f-760ba512ab07",
        "f87148bf-9bdd-4ad6-bad9-509870b9b244",
        "6fc051bd-cf87-448c-83c0-dfcd246ed8fc",
        "73ad8872-f0e0-45a6-a38a-969a311c0949",
        "2633c27a-5ca9-46d5-8313-676cf0aa9c8a",
        "fcda363a-9902-4ed0-9186-892d11f5baae",
        "b22619a3-4c9f-43dd-98c3-257877c40ac5",
        "e6d6d2d8-df52-47dc-b61f-f03988b6bc5b",
        "711d70b8-5fdf-412a-aac6-37bb9a254759",
        "e71fd315-522b-476a-85a4-c002983a1b8f",
        "65acf4c2-e39d-4423-a867-94ee4ef52c60",
        "57c38d95-ffce-4e57-a812-89cd0a270b08",
        "3bdf34ae-45b7-41d1-80a4-3bbaac396832",
        "e0c6437d-dd5a-49c9-9a7e-a07646962294",
        "67cfd213-e090-4a4b-944c-2ab257954fc5",
        "ea3d533f-d6f4-4059-9818-456749c395c1",
        "476e48e0-3f20-445f-829d-d1266db7fdf0",
        "4025bc46-a841-45d1-81cc-28cc2d22bb06",
        "3304cb82-fee1-4bee-9aa8-e3bd7f6298e3",
        "f1a941ac-bd3b-4791-a3f7-7134d33e18da",
        "7707bd50-85fd-492e-934c-801a9bbac6c9",
        "7f039849-fd0d-43e7-998d-0e22fcf79565",
        "038f6395-573e-41ed-b2e0-2749896f5040",
        "9de6bc76-5c19-45ee-ab9d-4c76c2f29fcf",
        "c4a34eaa-2d7c-4834-848a-c0fd800c1d48",
        "29cde45b-2f1b-44e4-a4b1-5f042ee82272",
        "be5bdc80-79ba-4d35-921e-3f1253e082af",
        "1448c54d-8a0b-4cd7-9aa2-a45c0d2fd2e2",
        "fa594177-a6ac-4cb6-bc26-0242fbb7001e",
        "b4d8afb6-6e1c-4c96-bf9c-159f3e468b29",
        "c2dca180-0fc5-4ee8-8ccc-822ca6ccb04e",
        "1070e7ca-6a77-4938-bb7f-6bf3a6be0fb3",
        "5123c907-bece-43a8-84e3-a161b3051d36",
        "3ffbf637-f74e-44d9-b856-d329120f85b6",
        "2b1c2d8f-ce41-4b89-8c14-fec2cf190735",
        "1ae1443f-e468-49fb-9b87-47f088f908d9",
        "e2a0066a-4419-480e-959b-c0ce49e932ac",
        "76444653-55ab-402d-98c2-e9683eb2070c",
        "1c4fe158-f56c-4498-8019-c8e8995d1a73",
        "0e56e5a2-b99d-40b6-a244-04b48f4fa051",
        "efafaee1-9c17-4ac4-8ec4-086a3ce83960",
        "e8fc7770-3106-452e-9b3b-286af990bbfa",
        "7ddb2341-bc62-4bf0-90dd-7a4758aab25e",
        "fda0279a-da8c-441a-8648-4f7e90091632",
        "3de8864d-fd27-4f8c-a03f-e4f6ec049547",
        "764d2298-5dba-421b-8f65-cc8cd99fbaaf",
        "501cad7a-00fa-4576-8a30-b40d51e28679",
        "34d5f45b-3a86-4a3a-921b-66aa342e312c",
        "9e8b1046-9f49-471f-bff8-1a5a5b1b8834",
        "7b3be051-fc4a-4331-a846-cc387ee5711a",
        "96d6f573-6a2b-4cbe-9161-cb61d1c6834b",
        "c4f6b706-58a1-4164-83ba-9279fbd3f984",
        "0adbf3d1-cbbc-4ced-9d32-0102ddd73e2b",
        "9ac2bf9b-ffbb-405b-a335-07360a919f89",
        "da124796-358a-488d-8eda-747cc21e47a3",
        "defcdf85-f88a-4ce9-9b9d-a66e6040251c",
        "2b1eae24-2e2d-47bb-b712-649040c631db",
        "3218269f-f4d2-4cf1-88de-b02adeddc6fe",
        "b77e9945-5bed-44fc-8a58-549171f534ae",
        "83dd9c6c-be5f-4edb-8aaa-def99ce3b661",
        "b933cd2a-a2fb-4d7f-b747-cd0fd121eab1",
        "94abb818-4da7-4cce-a3da-35e519950e73",
        "10e90be0-8863-430e-948a-3d0cacfdcb83",
        "178ddeb6-2a73-4a7b-95ea-0c57ccdd1830",
        "e09c18d0-47a6-4192-a2de-18a50957b3c9",
        "1e24ead6-fce5-428f-88cb-1989374191d7",
        "37ecf12c-7612-47ae-aba8-8fa7c68ebc51",
        "278b3c53-47fc-4797-8d81-7f55d423bf1c");

    public static void main(String[] args) {
        BasicAsyncDemo demo = new BasicAsyncDemo();
        demo.start();
    }

    private void start() {
        random = new Random();

        // Get client
        DirectConnectionConfig directConnectionConfig = new DirectConnectionConfig();
        directConnectionConfig.setMaxConnectionsPerEndpoint(1);
        directConnectionConfig.setMaxRequestsPerConnection(1);
//2
        GatewayConnectionConfig gatewayConnectionConfig = new GatewayConnectionConfig();
//                                                              .setProxy(new ProxyOptions(ProxyOptions.Type.HTTP,
//                                                                                         new InetSocketAddress(
//                                                                                             "127.0.0.1",
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
        runReadItem();
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

    private void runReadItem() {
        container = client.getDatabase(DATABASE_NAME).getContainer(READ_CONTAINER_NAME);
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
                String readId = largeDocuments.get(i);
//            container.readItem("a659e646-6aca-4a37-aa30-18a1766284d6", new PartitionKey("a659e646-6aca-4a37-aa30-18a1766284d6"), ObjectNode.class)
//                .onErrorResume(throwable -> Mono.empty())
//                .publishOn(Schedulers.boundedElastic())
//                .subscribe();

                container.readItem(readId, new PartitionKey(readId), ObjectNode.class)
                    .publishOn(Schedulers.boundedElastic())
                    .doOnSuccess(itemResponse -> {
                        successfulRequests.incrementAndGet();
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
                largeDocuments.add(id);
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
