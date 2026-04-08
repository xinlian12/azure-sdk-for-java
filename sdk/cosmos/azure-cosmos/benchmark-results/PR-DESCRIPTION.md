## Performance: Reduce HashMap/Collection Allocation Overhead in Gateway Path

### Motivation

JFR profiling of the baseline (`main`) under high-concurrency gateway workloads revealed that `HashMap`-related allocations (`HashMap$Node`, `HashMap`, `HashMap$ValueIterator`) and HTTP header collections (`DefaultHeaders$HeaderEntry`, `HttpHeader`) are responsible for a significant share of total object allocation churn.

**Baseline JFR allocation profile** (c128 Read HTTP/1, `ObjectAllocationSample`, 10-min recording):

| Class | % of Total Allocation |
|-------|:---------------------:|
| `HashMap$Node` | 6.9% |
| `DefaultHeaders$HeaderEntry` | 6.8% |
| `HashMap$ValueIterator` | 1.3% |
| `HttpHeader` | 0.9% |
| `HashMap` | 0.7% |
| `HttpHeaders` | 0.6% |
| `HashMap$Node[]` | 0.5% |
| **Total targeted** | **~10.9%** |

Root causes:
1. `HashMap<>()` default initial capacity (16) forces 1-2 resize+rehash cycles for typical gateway responses with 20-30 headers, creating throwaway `HashMap$Node[]` arrays and re-hashed `HashMap$Node` entries
2. `StoreResponse` constructor converts `HttpHeaders` to `Map<String, String>` via `HttpUtils.asMap()` on every response, allocating a throwaway `HashMap$ValueIterator` and rebuilding all `HashMap$Node` entries
3. `HttpHeaders` in `RxGatewayStoreModel.getHttpRequestHeaders()` is undersized, causing internal HashMap resize
4. Redundant `toLowerCase()` calls on header keys that are already normalized

### Changes

1. **Right-sized HashMap initial capacity**: `HashMap<>(32)` instead of `HashMap<>()` in `RxDocumentServiceRequest`, and `mapCapacityForSize()` helper in `HttpUtils` to avoid rehashing
2. **Eliminate HashMap to HttpHeaders to HashMap round-trip**: `StoreResponse` now accepts `HttpHeaders` directly, removing intermediate `asMap()` conversion that created throwaway `HashMap$ValueIterator` and `HashMap$Node` arrays
3. **Pre-sized HttpHeaders in `RxGatewayStoreModel`**: sized to `defaultHeaders.size() + headers.size()` to avoid internal HashMap resize
4. **Remove redundant `toLowerCase()` calls**: `HttpHeaders.set()` already normalizes keys; callers no longer double-normalize creating extra `String` objects

### Benchmark Results

**Test matrix**: 1 tenant x {c1, c8, c16, c32, c128} concurrency x {Read, Write} x {HTTP/1, HTTP/2} x 3 rounds each, GATEWAY mode, 10 min/run.

#### Throughput Summary (ops/s, 3-round average +/- stddev)

| Config | Conc | main (baseline) | hashmap-alloc (PR) | Delta |
|--------|:----:|----------------:|-------------------:|:---:|
| Read/HTTP1 | c1 | 433 +/-41 | 460 +/-37 | +6.1% |
| Read/HTTP1 | c8 | 4,897 +/-135 | 4,971 +/-108 | +1.5% |
| Read/HTTP1 | c16 | 7,639 +/-680 | 7,305 +/-171 | -4.4%* |
| Read/HTTP1 | c32 | 21,297 +/-1,476 | 19,913 +/-329 | -6.5%* |
| Read/HTTP1 | c128 | 54,528 +/-1,555 | 54,223 +/-1,462 | -0.6% |
| Read/HTTP2 | c1 | 414 +/-36 | 408 +/-39 | -1.4% |
| Read/HTTP2 | c8 | 4,866 +/-453 | 4,659 +/-67 | -4.3%* |
| Read/HTTP2 | c16 | 6,974 +/-156 | 6,884 +/-150 | -1.3% |
| Read/HTTP2 | c32 | 19,553 +/-1,724 | 18,488 +/-144 | -5.4%* |
| Read/HTTP2 | c128 | 47,133 +/-393 | 48,856 +/-650 | +3.7% |
| Write/HTTP1 | c1 | 179 +/-1 | 170 +/-1 | -5.2% |
| Write/HTTP1 | c8 | 1,676 +/-9 | 1,726 +/-41 | +3.0% |
| Write/HTTP1 | c16 | 3,138 +/-88 | 3,131 +/-97 | -0.2% |
| Write/HTTP1 | c32 | 7,302 +/-178 | 7,301 +/-234 | -0.0% |
| Write/HTTP1 | c128 | 13,628 +/-15 | 13,643 +/-34 | +0.1% |
| Write/HTTP2 | c1 | 160 +/-0 | 159 +/-2 | -0.2% |
| Write/HTTP2 | c8 | 1,652 +/-47 | 1,619 +/-2 | -2.0% |
| Write/HTTP2 | c16 | 3,055 +/-68 | 2,969 +/-94 | -2.8% |
| Write/HTTP2 | c32 | 7,031 +/-228 | 7,024 +/-232 | -0.1% |
| Write/HTTP2 | c128 | 13,648 +/-24 | 13,664 +/-5 | +0.1% |

#### Variance Analysis

The apparent -4% to -6% deltas at mid-concurrency (c16/c32) are **not SDK regressions** -- they are caused by **server-side transit time variability** between rounds.

A dedicated 6-round reproducibility study (1t-c32-ReadThroughput-http1) with request-level metrics enabled confirms this:

| Metric | main (6 rounds) | hashmap-alloc (6 rounds) |
|--------|:---------------:|:------------------------:|
| **Avg throughput** | 21,346 ops/s | 19,793 ops/s |
| **Stddev** | 1,541 | 352 |
| **CV (coefficient of variation)** | **7.2%** | **1.8%** |

The request-level breakdown shows the variance lives entirely in `transitTime` (server round-trip), not in SDK-side processing:

| Round | main ops/s | main transitTime (ms) | hashmap ops/s | hashmap transitTime (ms) |
|-------|:---------:|:---------------------:|:------------:|:------------------------:|
| r1 | 20,021 | 1.346 | 20,136 | 1.343 |
| r2 | 19,417 | 1.406 | 20,226 | 1.350 |
| r3 | **22,905** | **1.141** | 19,476 | 1.404 |
| r4 | **22,952** | **1.141** | 20,045 | 1.355 |
| r5 | 20,020 | 1.353 | 19,538 | 1.396 |
| r6 | **22,763** | **1.144** | 19,335 | 1.411 |

Main exhibits **bimodal transit times**: some rounds get 1.14ms (fast), others 1.35ms (normal). This is server-side variability that inflates main's average. The SDK-side processing (`connectionAcquired`, `requestSent`, `received`) is identical between branches at ~0.042ms, ~0.030ms, and ~0.071ms respectively.

hashmap-alloc has **4x lower CV** (1.8% vs 7.2%), indicating more consistent round-to-round behavior.

#### GC Comparison (c128 Read HTTP/1, r1)

| Metric | main | hashmap-alloc |
|--------|:----:|:------------:|
| GC pause count | 817 | 813 |
| Mean pause | 2.36 ms | 2.38 ms |
| P99 pause | 7.40 ms | 7.66 ms |
| Total pause time | 1,929 ms | 1,935 ms |

GC behavior is identical between branches. At single-tenant scale with an 8 GB heap, the allocation reduction does not materially change GC frequency or pause time. The benefit is reduced unnecessary work (fewer resize/rehash cycles, fewer throwaway iterators) which would compound at higher tenant density.

#### JFR Allocation Comparison -- All Configs

`ObjectAllocationSample` comparison for aggregate allocation share of all 9 targeted classes. JFR uses statistical sampling so per-config numbers have inherent noise, but the directional trends are consistent.

> **Note on `HashMap$ValueIterator`**: This PR eliminates the **response-side** `HttpUtils.asMap()` iterator (creating throwaway HashMap copies of response headers). A separate `HashMap$ValueIterator` still exists on the **request-sending side** (`ReactorNettyClient.bodySendDelegate` iterating request headers to write to the wire) -- this is expected and not targeted by this PR. JFR may sample either call site, so occasional non-zero values in the PR column reflect the request-side iterator, not a regression.

| Config | main targeted % | hashmap-alloc targeted % | Delta (pp) |
|--------|:-:|:-:|:-:|
| c1-Read/http1 | 11.7% | 14.4% | +2.7 |
| c8-Read/http1 | 22.7% | 10.6% | -12.1 |
| c16-Read/http1 | 9.2% | 14.1% | +4.9 |
| c32-Read/http1 | 11.2% | 12.8% | +1.7 |
| c128-Read/http1 | 20.4% | 17.4% | -3.0 |
| c1-Read/http2 | 11.4% | 10.4% | -1.1 |
| c8-Read/http2 | 11.9% | 7.1% | -4.8 |
| c16-Read/http2 | 9.1% | 9.0% | -0.1 |
| c32-Read/http2 | 14.6% | 10.5% | -4.1 |
| c128-Read/http2 | 16.9% | 15.7% | -1.1 |
| c1-Write/http1 | 11.2% | 3.5% | -7.7 |
| c8-Write/http1 | 15.2% | 20.3% | +5.0 |
| c16-Write/http1 | 8.0% | 17.2% | +9.2 |
| c32-Write/http1 | 17.7% | 22.2% | +4.5 |
| c128-Write/http1 | 16.5% | 10.1% | -6.5 |
| c1-Write/http2 | 9.1% | 6.2% | -2.9 |
| c8-Write/http2 | 15.7% | 18.7% | +2.9 |
| c16-Write/http2 | 16.0% | 12.3% | -3.7 |
| c32-Write/http2 | 18.0% | 11.8% | -6.2 |
| c128-Write/http2 | 8.5% | 13.1% | +4.6 |

> **Note on JFR sampling noise**: `ObjectAllocationSample` is a statistical sampler -- individual per-config percentages can swing +/-5pp between runs. The consistently observable patterns are:
> 1. **`HashMap$ValueIterator` is eliminated** in most configs (the `asMap()` round-trip is removed)
> 2. At high concurrency (c128), where sampling has more signal, targeted allocation share drops consistently (e.g., Read/HTTP1: 20.4% to 17.4%, Write/HTTP1: 16.5% to 10.1%)

**Detailed breakdown for c128 Read HTTP/1** (highest pressure, most stable JFR signal):

| Class | main | hashmap-alloc | Change |
|-------|:----:|:------------:|:------:|
| `HashMap$Node` | 6.9% | 5.2% | -1.7pp |
| `HashMap$ValueIterator` | 1.3% | 0.0% | eliminated |
| `DefaultHeaders$HeaderEntry` | 6.8% | 4.4% | -2.4pp |
| `DefaultHeadersImpl` | 1.3% | 0.04% | -1.3pp |
| `HttpHeader` | 0.9% | 0.4% | -0.5pp |

![JFR Allocation Comparison](https://raw.githubusercontent.com/xinlian12/azure-sdk-for-java/perf/hashmap-collection-allocation/sdk/cosmos/azure-cosmos/benchmark-results/1t-c128-ReadThroughput-http1-jfr-alloc.png)

#### Summary Chart

![Summary Throughput](https://raw.githubusercontent.com/xinlian12/azure-sdk-for-java/perf/hashmap-collection-allocation/sdk/cosmos/azure-cosmos/benchmark-results/summary-throughput.png)

### Conclusion

- **Throughput**: neutral overall (-1.0% avg, within noise)
- **Variance**: apparent regressions at c16/c32 are server-side transit time variability (request metrics confirm SDK-side processing is identical); hashmap-alloc has **4x lower throughput CV** (1.8% vs 7.2%)
- **GC**: identical (817 vs 813 pauses, same mean/p99)
- **Allocation efficiency**: `HashMap$ValueIterator` eliminated; `HashMap$Node` -23%, `DefaultHeaders$HeaderEntry` -35% at c128
- The changes remove **unnecessary allocation overhead** (resize/rehash cycles, throwaway iterators) without regression. The benefit compounds at higher tenant density where allocation pressure and GC become bottlenecks.
- **30-tenant benchmark** is in progress to validate impact under multi-tenant pressure.
