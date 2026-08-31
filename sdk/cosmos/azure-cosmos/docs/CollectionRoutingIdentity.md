# Collection Routing Identity

This document summarizes how the Azure Cosmos DB Java SDK identifies a
collection for public operations and internal routing-metadata requests.

In Gateway mode, the SDK sends an HTTP URI. In Direct mode, data requests do
not have an HTTP URI; the SDK sends an RNTBD frame containing resource-name and
resource-ID tokens.

## Public Operations

| Resource / `OperationType` | Path identity | Gateway URI | Direct-mode path / routing identity | Collection-related headers or tokens |
|---|---|---|---|---|
| Container `Create` | Database name; no existing collection identity | `/dbs/{dbName}/colls` | Still uses Gateway | Collection name is in the request body; no collection RID header |
| Container `Read`, `Delete` | Collection name | `/dbs/{dbName}/colls/{collName}` | Still uses Gateway | No automatic collection-name or collection-RID header |
| Container `Replace` | `_self` link, normally RID | `/dbs/{dbRid}/colls/{collRid}` | Still uses Gateway | No automatic collection-name or collection-RID header |
| Container `ReadFeed`, `Query` | Database name; collection feed | `/dbs/{dbName}/colls` | Still uses Gateway | No specific collection identity |
| Item `Create`, `Upsert`, `Batch` | Collection name | `/dbs/{dbName}/colls/{collName}/docs` | RNTBD `DatabaseName={dbName}` and `CollectionName={collName}` | Direct: `CollectionRid={collRid}` and usually `PartitionKeyRangeId={collRid},{rangeId}`. Gateway: `x-ms-cosmos-intended-collection-rid: {collRid}` |
| Item `Read`, `Replace`, `Patch`, `Delete` | Collection and item names | `/dbs/{dbName}/colls/{collName}/docs/{itemId}` | RNTBD database, collection, and document-name tokens | Same as the preceding row |
| Item `ReadFeed`, including change feed | Collection name | `/dbs/{dbName}/colls/{collName}/docs` | RNTBD collection name, targeted to a resolved range | Direct: collection RID plus composite partition-key-range ID. Gateway: intended collection RID; targeted feeds can also carry a composite partition-key-range ID |
| Item `Query`, including read-many | Collection name | `/dbs/{dbName}/colls/{collName}/docs` | RNTBD collection name, normally one request per resolved range | Direct: collection RID plus composite partition-key-range ID. Gateway: intended collection RID; targeted query pages can carry a composite partition-key-range ID |
| Partition-key delete `Delete` | Collection name | `/dbs/{dbName}/colls/{collName}/operations/partitionkeydelete` | Still uses Gateway | No automatic intended-collection-RID header because the resource type is `PartitionKey` |
| Stored procedure `ExecuteJavaScript` | Collection and stored-procedure names | `/dbs/{dbName}/colls/{collName}/sprocs/{sprocName}` | RNTBD database, collection, and stored-procedure-name tokens | Direct: collection RID plus composite partition-key-range ID. Gateway: no automatic intended-RID header because the resource type is `StoredProcedure` |
| Stored procedure, trigger, or UDF `Create`, `Read`, `Delete`, `ReadFeed`, `Query` | Collection and script names | `/dbs/{dbName}/colls/{collName}/{sprocs\|triggers\|udfs}[/{name}]` | Still uses Gateway | No automatic collection-name or collection-RID header |
| Stored procedure, trigger, or UDF `Replace` | `_self` link, normally RID | `/dbs/{dbRid}/colls/{collRid}/{sprocs\|triggers\|udfs}/{resourceRid}` | Still uses Gateway | No automatic collection-name or collection-RID header |
| Conflict `Read`, `Delete`, `ReadFeed`, `Query` | Collection and conflict names | `/dbs/{dbName}/colls/{collName}/conflicts[/{conflictName}]` | RNTBD database, collection, and conflict-name tokens | Direct: collection RID plus composite partition-key-range ID. Gateway: no automatic intended-RID header because the resource type is `Conflict` |
| Public partition-key-range `ReadFeed` | Collection name | `/dbs/{dbName}/colls/{collName}/pkranges` | Still uses Gateway | No collection RID header; public identity is in the URI |

## Internal Metadata Operations

| Internal operation / `OperationType` | Path identity | Gateway URI | Direct-mode path / behavior | Collection-related headers or tokens |
|---|---|---|---|---|
| Container lookup `DocumentCollection/Read` | Name on initial resolution; RID when resolving a cached RID | `/dbs/{dbName}/colls/{collName}` or `/dbs/{dbRid}/colls/{collRid}` | Direct clients also perform this through Gateway | No automatic collection RID header; RID or name is in the URI |
| Partition-key-range lookup or refresh `PartitionKeyRange/ReadFeed` | Collection RID | `/dbs/{dbRid}/colls/{collRid}/pkranges` | Direct clients also perform this through Gateway | No collection RID header; RID is in the URI. Refresh uses incremental-feed continuation headers |
| Address lookup or refresh | Collection RID | `/addresses?$resolveFor=/dbs/{dbRid}/colls/{collRid}/docs&$partitionKeyRangeIds={rangeIds}` | Direct only; returns physical replica addresses | No collection RID header. RID is in `$resolveFor`; refresh may add `x-ms-force-refresh` and `x-ms-collectionroutingmap-refresh` |
| Query plan `Document/QueryPlan` | Collection name | `/dbs/{dbName}/colls/{collName}` | A Direct client routes this through Gateway V1 or Gateway V2 | `x-ms-cosmos-intended-collection-rid: {collRid}` |
| Consistency barrier `DocumentCollection/Head` | Same as the original request: name or RID | Not used in normal Gateway mode | Direct-only RNTBD request to resolved replicas | Copies `CollectionName` when name-based, `CollectionRid`, and `PartitionKeyRangeId={collRid},{rangeId}` |

## Header Summary

| Identity | Gateway V1 | Direct RNTBD | Gateway V2 / thin client |
|---|---|---|---|
| Collection name | Only in the URI | `CollectionName` RNTBD token | `CollectionName` RNTBD token inside the proxy payload |
| Resolved collection RID | `x-ms-cosmos-intended-collection-rid` automatically for `Document` requests only | `CollectionRid`, sourced from `x-ms-documentdb-collection-rid` | `CollectionRid`, sourced from `x-ms-documentdb-collection-rid` |
| Partition or range identity | Optional `x-ms-documentdb-partitionkeyrangeid`; targeted query or feed values can include the collection RID | `PartitionKeyRangeId`, normally `{collectionRid},{rangeId}` | Same RNTBD token |
| Address-refresh identity | RID in `$resolveFor`, not a header | Not applicable to the backend data request | Not applicable |

A normal name-based item operation is intentionally dual-identified:

- Gateway V1 uses the collection name in the URI and sends
  `x-ms-cosmos-intended-collection-rid`.
- Direct mode sends a `CollectionName` RNTBD token, a `CollectionRid` token,
  and usually the `{collectionRid},{rangeId}` routing identity.
- Partition-key-range and address metadata requests primarily carry the
  collection RID in the URI or query string rather than in a collection
  header.

## Implementation References

- Request path parsing:
  `src/main/java/com/azure/cosmos/implementation/RxDocumentServiceRequest.java`
- Gateway URI generation and intended collection RID:
  `src/main/java/com/azure/cosmos/implementation/RxGatewayStoreModel.java`
- Direct collection resolution:
  `src/main/java/com/azure/cosmos/implementation/directconnectivity/AddressResolver.java`
- RNTBD name and RID tokens:
  `src/main/java/com/azure/cosmos/implementation/directconnectivity/rntbd/RntbdRequestHeaders.java`
- Partition-key-range metadata:
  `src/main/java/com/azure/cosmos/implementation/caches/RxPartitionKeyRangeCache.java`
- Address metadata:
  `src/main/java/com/azure/cosmos/implementation/directconnectivity/GatewayAddressCache.java`
- Query-plan metadata:
  `src/main/java/com/azure/cosmos/implementation/query/QueryPlanRetriever.java`
- Direct consistency barriers:
  `src/main/java/com/azure/cosmos/implementation/directconnectivity/BarrierRequestHelper.java`

For general Direct and Gateway routing architecture, see
[SQL SDK connectivity modes](https://learn.microsoft.com/azure/cosmos-db/sdk-connection-modes#routing).
