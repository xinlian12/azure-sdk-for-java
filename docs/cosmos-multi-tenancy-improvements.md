# Cosmos DB Java SDK Multi-Tenancy Improvements

> Status as of September 11, 2026. In this document, *multi-tenancy* means a single JVM serving many logical tenants by creating clients for many Cosmos DB accounts or credentials. It is primarily a resource-scaling problem, not an authentication-flow problem.

## Why multi-tenant workloads are challenging

The Cosmos DB Java SDK was historically optimized around a small number of long-lived `CosmosClient` instances. At hundreds or thousands of clients, resources that are inexpensive per client become significant at the process level:

- **Linear resource growth:** per-client background schedulers, endpoint-management work, routing metadata, and network state increase thread and heap usage.
- **Repeated metadata and allocation cost:** clients may retain duplicate partition-routing data, while high-throughput request and response paths amplify small object-allocation inefficiencies.
- **Connection-pool fragmentation:** transport pools are associated with an endpoint identity (not simply an IP address). Different account hostnames therefore use different pools even when traffic reaches related infrastructure.
- **Private endpoint topology:** the current one-account-to-one-private-endpoint model gives each account a distinct endpoint. A JVM accessing many accounts cannot reuse connections across those endpoints.
- **Lifecycle and isolation requirements:** any process-wide sharing must release resources when clients close, avoid leaking tenant state, preserve authentication and diagnostics boundaries, and isolate failures.

## SDK improvements delivered

All five unique related PRs have been merged. Together they make large client populations cheaper without changing the public API.

| PR | Change | Multi-tenant benefit |
|---|---|---|
| [#48455](https://github.com/Azure/azure-sdk-for-java/pull/48455) | Replaced an HTTP/2 response-header scan with a direct lookup for the server-version header. | Reduces per-response CPU work in Gateway HTTP/2 workloads; multi-account benchmarks showed improved throughput and latency in most tested configurations. |
| [#48662](https://github.com/Azure/azure-sdk-for-java/pull/48662) | Reduced gateway-path collection allocations by pre-sizing maps and headers, removing an intermediate header-to-map conversion, and avoiding redundant normalization. | Lowers allocation and GC pressure while preserving throughput and latency behavior. |
| [#49062](https://github.com/Azure/azure-sdk-for-java/pull/49062) | Replaced per-client endpoint-refresh and partition-availability schedulers with shared bounded-elastic schedulers; retained per-client task cancellation and cleanup. | Removes the previous `2N` dedicated-background-thread pattern and caps thread growth as client count increases. |
| [#49513](https://github.com/Azure/azure-sdk-for-java/pull/49513) | Strips unused service fields from each internal `PartitionKeyRange` after deserialization. | Reduces retained heap per physical partition, which compounds across many partitions and clients. |
| [#49560](https://github.com/Azure/azure-sdk-for-java/pull/49560) | Shares partition-key-range cache storage across clients configured with the same endpoint URI, using a process-wide reference-counted registry. | Avoids duplicate routing maps and repeated `/pkranges` calls for same-endpoint clients. Entries are isolated by endpoint, released on close, evicted after the final reference, and protected by a leak-cleanup fallback. |

These changes address the SDK-controlled portion of the problem: thread scaling, duplicated routing metadata, request-path overhead, retained heap, and cleanup. The shared routing cache is intentionally narrower than connection sharing: clients with different endpoint URIs still have separate caches and network stacks.

## Pending: cross-account connection management

The largest remaining improvement is to reuse or multiplex transport connections across accounts. This cannot be implemented safely by the SDK alone because accounts currently resolve to distinct hostnames/private endpoints, while SDK pools are scoped by endpoint identity.

A service-side design is needed to define:

1. **Shared endpoint topology:** how multiple accounts can use a common hostname and port, including private endpoint support.
2. **Account routing contract:** how each request identifies its target account when the hostname no longer provides that identity, potentially through a service-defined header or protocol field.
3. **Security and isolation:** how authentication, authorization, throttling, diagnostics, and failure handling remain account-scoped over shared connections.
4. **Connection lifecycle:** multiplexing limits, pool ownership, idle eviction, broken-channel cleanup, failover behavior, and compatibility across Gateway versions and connection modes.
5. **SDK integration contract:** the stable endpoint/account identifiers and service capabilities the SDK can use to key shared pools and determine when sharing is permitted.

Until that service contract is finalized, the SDK should continue optimizing per-client cost and same-endpoint sharing, but should not attempt cross-account connection pooling. Once the service design is available, the next SDK work item is a reference-counted shared transport-pool layer with explicit account isolation, deterministic cleanup, diagnostics, opt-out/rollout controls, and multi-account scale validation.
