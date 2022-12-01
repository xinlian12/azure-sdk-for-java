## Table of Contents
[Cosmos feed processor in Azure Cosmos DB](https://learn.microsoft.com/en-us/azure/cosmos-db/nosql/change-feed-processor?tabs=dotnet)

- [ChangeFeedProcessor internal implementation]()
- [Public API]()
- [Migration]()
- [Other Changes]()

## ChangeFeedProcessor internal implementation
Based on the lease version SDK use internally, we have `pkversion` and `epkversion`.
- pkversion lease container
  ```
  {
    "id":"TESTDatabaseAccount.documents.azure.com_RxJava.SDKTest.SharedDatabase_20221115T094259_AQi_e45790f7-e0cc-4697-8bc1-d8a95e792a9c.info"
  }
  ```
  ```
  {
    "id":"TESTDatabaseAccount.documents.azure.com_RxJava.SDKTest.SharedDatabase_20221115T094259_AQi_e45790f7-e0cc-4697-8bc1-d8a95e792a9c..0",
    "LeaseToken":"0",
    "ContinuationToken":"\"11\"",
    "timestamp":"2022-11-15T17:43:34.634427Z",
    "Owner":"ZKNjph"
  }
  ```

- epkversion lease container
  ```
  {
    "id":"TESTDatabaseAccount.documents.azure.com_bf8uAA==_bf8uALgoqxk=.info"
  }
  ```
  ```
  {
    "id":"TESTDatabaseAccount.documents.azure.com_RxJava.azure.com_bf8uAA==_bf8uALgoqxk=..-FF",
    "LeaseToken":"-FF",
    "ContinuationToken":"eyJWIjoxLCJSaWQiOiIvZGJzL1J4SmF2YS5TREtUZXN0LlNoYXJlZERhdGFiYXNlXzIwMjIxMTE1VDA5NTI1NV9LWFAvY29sbHMvOGM2NTNmNzUtOTc1OC00NmExLWJlMzItMjFjMDQwZDIzNDVhIiwiTW9kZSI6IklOQ1JFTUVOVEFMIiwiU3RhcnRGcm9tIjp7IlR5cGUiOiJCRUdJTk5JTkcifSwiQ29udGludWF0aW9uIjp7IlYiOjEsIlJpZCI6Ii9kYnMvUnhKYXZhLlNES1Rlc3QuU2hhcmVkRGF0YWJhc2VfMjAyMjExMTVUMDk1MjU1X0tYUC9jb2xscy84YzY1M2Y3NS05NzU4LTQ2YTEtYmUzMi0yMWMwNDBkMjM0NWEiLCJDb250aW51YXRpb24iOlt7InRva2VuIjoiXCIxMVwiIiwicmFuZ2UiOnsibWluIjoiIiwibWF4IjoiRkYifX1dLCJSYW5nZSI6eyJtaW4iOiIiLCJtYXgiOiJGRiIsImlzTWluSW5jbHVzaXZlIjp0cnVlLCJpc01heEluY2x1c2l2ZSI6ZmFsc2V9fX0=",
    "timestamp":"2022-11-15T17:43:34.634427Z",
    "Owner":"ZKNjph",
    "version":1,
    "feedRange":{
      "Range":{
        "min":"",
        "max":"FF",
        "isMinInclusive":true,
        "isMaxInclusive":false
      }
    }
  }
  ```
`"V":1,"Rid":"/dbs/RxJava.SDKTest.SharedDatabase_20221115T095255_KXP/colls/8c653f75-9758-46a1-be32-21c040d2345a","Mode":"INCREMENTAL","StartFrom":{"Type":"BEGINNING"},"Continuation":{"V":1,"Rid":"/dbs/RxJava.SDKTest.SharedDatabase_20221115T095255_KXP/colls/8c653f75-9758-46a1-be32-21c040d2345a","Continuation":[{"token":"\"11\"","range":{"min":"","max":"FF"}}],"Range":{"min":"","max":"FF","isMinInclusive":true,"isMaxInclusive":false}}}`

Major differences between different lease version includes

| Lease version                          | id                                        | LeaseToken           | ContinuationToken   | track feedRange   |
  | :------------------------------------- | :---------------------------------------- | :------------------- | :------------------ | :---------------- |
| PARTITION_KEY_BASED_LEASE  (0)         | databaseId & containerId                  | partitionKeyRangeId  | lsn                 | No                |
| EPK_RANGE_BASED_LEASE  (1)             | databaseResourceId & containerResourceId  | EpkRange             | ChangeFeedState     | Yes               |

Major differences between different change feed internal implementation version includes


| ChangeFeed processor internal version  | Lease version                             | ChangeFeedMode                 |
  | :------------------------------------- | :---------------------------------------- | :----------------------------- |
| pkversion                              | PARTITION_KEY_BASED_LEASE                 | Incremental                    |
| epkversion                             | EPK_RANGE_BASED_LEASE                     | Incremental & FullFidelity     |

## Public API
- `handleChanges(Consumer<List<JsonNode>> consumer)` : targeted for `incremental` change feed mode, use `pkversion` changeFeedProcessor internally
- `handleAllVersionsAndDeletesChanges(Consumer<List<ChangeFeedProcessorItem>> consumer)` : targeted for `full fidelity` change feed mode, use `epkversion` changeFeedProcessor internally

For merge support, we need to use `EPK_RANGE_BASED_LEASE`
- `handleLatestVersionChanges(Consumer<List<JsonNode>> consumer)` : targeted for `incremental` change feed mode, use `epkversion` changeFeedProcessor internally
- Another proposal: `handleLatestVersionChanges(Consumer<List<ChangeFeedProcessorItem>> consumer)`
  [ChangeFeedWireFormatDesign](https://microsoft-my.sharepoint.com/:w:/p/mkolt/EYuEz-aOPydMkbWKCO0ThJUBf_3BeewRp5XV91_Swz7ZaA?wdOrigin=TEAMS-ELECTRON.p2p.bim&wdExp=TEAMS-CONTROL&wdhostclicktime=1668711240969&web=1)
>       Should we use the new change feed wire format version
  It will be good to match with the change feed full fidelity public API. 

```
      {
          "id" : "23b9a579-2bd8-4cc4-9441-f457084adb44",
           "mypk" : "23b9a579-2bd8-4cc4-9441-f457084adb44",
          "sgmts" : [ [ 6519456, 1471916863 ], [ 2498434, 1455671440 ] ],
           "_rid" : "+8ZSALEqaM8BAAAAAAAAAA==",
           "_self" : "dbs/+8ZSAA==/colls/+8ZSALEqaM8=/docs/+8ZSALEqaM8BAAAAAAAAAA==/",
           "_etag" : "\"6f02ab2f-0000-0700-0000-637684110000\"",
           "_attachments" : "attachments/",
           "_ts" : 1668711441,
           "_lsn" : 2
    }
   {
          "current" : {
            "id" : "b71bf39a-4f76-4626-bac4-9b3e55a483f2",
            "mypk" : "b71bf39a-4f76-4626-bac4-9b3e55a483f2",
            "sgmts" : [ [ 6519456, 1471916863 ], [ 2498434, 1455671440 ] ],
            "_rid" : "I0pAAJK-nJYBAAAAAAAAAA==",
            "_self" : "dbs/I0pAAA==/colls/I0pAAJK-nJY=/docs/I0pAAJK-nJYBAAAAAAAAAA==/",
            "_etag" : "\"8900a8c7-0000-0700-0000-637684ac0000\"",
            "_attachments" : "attachments/",
            "_ts" : 1668711596
         },
        "metadata" : {
          "lsn" : 2,
          "crts" : 1668711596
        }
   }
```

>     Whether we should introduce more dependency on Jackson library

>     Whether we should keep the format consistent between fullFidelity public API contract
Yes

>     How do we coordinate with queryChangeFeed public API
For queryChangeFeed, we are still going to keep use the old wire format. In the future, we will perpuse a strong contract as well, like returning ChangeFeedProcessItem.
