// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.azure.cosmos.implementation.clienttelemetry;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosDiagnostics;
import com.azure.cosmos.CosmosDiagnosticsContext;
import com.azure.cosmos.implementation.ClientSideRequestStatistics;
import com.azure.cosmos.implementation.FeedResponseDiagnostics;
import com.azure.cosmos.implementation.ImplementationBridgeHelpers;
import com.azure.cosmos.implementation.OperationType;
import com.azure.cosmos.implementation.RequestTimeline;
import com.azure.cosmos.implementation.ResourceType;
import com.azure.cosmos.implementation.Strings;
import com.azure.cosmos.implementation.directconnectivity.RntbdTransportClient;
import com.azure.cosmos.implementation.directconnectivity.StoreResponseDiagnostics;
import com.azure.cosmos.implementation.directconnectivity.StoreResultDiagnostics;
import com.azure.cosmos.implementation.directconnectivity.rntbd.RntbdDurableEndpointMetrics;
import com.azure.cosmos.implementation.directconnectivity.rntbd.RntbdEndpoint;
import com.azure.cosmos.implementation.directconnectivity.rntbd.RntbdEndpointStatistics;
import com.azure.cosmos.implementation.directconnectivity.rntbd.RntbdMetricsCompletionRecorder;
import com.azure.cosmos.implementation.directconnectivity.rntbd.RntbdRequestRecord;
import com.azure.cosmos.implementation.guava25.net.PercentEscaper;
import com.azure.cosmos.implementation.query.QueryInfo;
import com.azure.cosmos.models.CosmosMetricName;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientTelemetryMetrics {
    private static final Logger logger = LoggerFactory.getLogger(ClientTelemetryMetrics.class);
    private static final ImplementationBridgeHelpers.CosmosAsyncClientHelper.CosmosAsyncClientAccessor clientAccessor =
        ImplementationBridgeHelpers.CosmosAsyncClientHelper.getCosmosAsyncClientAccessor();
    private static final
        ImplementationBridgeHelpers.CosmosDiagnosticsHelper.CosmosDiagnosticsAccessor diagnosticsAccessor =
            ImplementationBridgeHelpers.CosmosDiagnosticsHelper.getCosmosDiagnosticsAccessor();
    private static final
    ImplementationBridgeHelpers.CosmosDiagnosticsContextHelper.CosmosDiagnosticsContextAccessor diagnosticsCtxAccessor =
        ImplementationBridgeHelpers.CosmosDiagnosticsContextHelper.getCosmosDiagnosticsContextAccessor();

    private static final PercentEscaper PERCENT_ESCAPER = new PercentEscaper("_-/.", false);

    private static CompositeMeterRegistry compositeRegistry = createFreshRegistry();
    private static final ConcurrentHashMap<MeterRegistry, AtomicLong> registryRefCount = new ConcurrentHashMap<>();
    private static CosmosMeterOptions cpuOptions;
    private static CosmosMeterOptions memoryOptions;

    private static volatile DescendantValidationResult lastDescendantValidation = new DescendantValidationResult(Instant.MIN, true);

    private static final Object lockObject = new Object();

    // Meter cache: avoids rebuilding builder chains + re-registering meters on every request.
    // Keyed by CosmosMetricName → (Tags → Meter). Cleared on registry add/remove.
    private static final ConcurrentHashMap<CosmosMetricName, MeterCache> meterCaches = new ConcurrentHashMap<>();

    private static MeterCache getMeterCache(CosmosMetricName metricName) {
        return meterCaches.computeIfAbsent(metricName, k -> new MeterCache());
    }

    private static void clearAllMeterCaches() {
        meterCaches.values().forEach(MeterCache::clear);
    }

    private static class MeterCache {
        private final ConcurrentHashMap<Tags, Counter> counters = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Tags, Timer> timers = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Tags, DistributionSummary> summaries = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Tags, DistributionSummary> summariesNoHistogram = new ConcurrentHashMap<>();

        Counter getOrCreateCounter(Tags tags, CosmosMeterOptions options,
                                   String baseUnit, String description) {
            return counters.computeIfAbsent(tags, t -> Counter
                .builder(options.getMeterName().toString())
                .baseUnit(baseUnit)
                .description(description)
                .tags(t)
                .register(compositeRegistry));
        }

        Timer getOrCreateTimer(Tags tags, CosmosMeterOptions options,
                               String description, Duration maxExpected) {
            return timers.computeIfAbsent(tags, t -> Timer
                .builder(options.getMeterName().toString())
                .description(description)
                .maximumExpectedValue(maxExpected)
                .publishPercentiles(options.getPercentiles())
                .publishPercentileHistogram(options.isHistogramPublishingEnabled())
                .tags(t)
                .register(compositeRegistry));
        }

        DistributionSummary getOrCreateSummary(Tags tags, CosmosMeterOptions options,
                                               String baseUnit, String description,
                                               double maxExpected) {
            return summaries.computeIfAbsent(tags, t -> DistributionSummary
                .builder(options.getMeterName().toString())
                .baseUnit(baseUnit)
                .description(description)
                .maximumExpectedValue(maxExpected)
                .publishPercentiles(options.getPercentiles())
                .publishPercentileHistogram(options.isHistogramPublishingEnabled())
                .tags(t)
                .register(compositeRegistry));
        }

        DistributionSummary getOrCreateSummaryNoHistogram(Tags tags, CosmosMeterOptions options,
                                                          String baseUnit, String description,
                                                          double maxExpected) {
            return summariesNoHistogram.computeIfAbsent(tags, t -> DistributionSummary
                .builder(options.getMeterName().toString())
                .baseUnit(baseUnit)
                .description(description)
                .maximumExpectedValue(maxExpected)
                .publishPercentiles()
                .publishPercentileHistogram(false)
                .tags(t)
                .register(compositeRegistry));
        }

        void clear() {
            counters.clear();
            timers.clear();
            summaries.clear();
            summariesNoHistogram.clear();
        }
    }
    private static final Tag QUERYPLAN_TAG = Tag.of(
        TagName.RequestOperationType.toString(),
        ResourceType.DocumentCollection + "/" + OperationType.QueryPlan);

    private static String convertStackTraceToString(Throwable throwable)
    {
        try (StringWriter sw = new StringWriter();
             PrintWriter pw = new PrintWriter(sw))
        {
            throwable.printStackTrace(pw);
            return sw.toString();
        }
        catch (IOException ioe)
        {
            throw new IllegalStateException(ioe);
        }
    }

    private static CompositeMeterRegistry createFreshRegistry() {
        CompositeMeterRegistry registry = new CompositeMeterRegistry();
        if (logger.isTraceEnabled()) {
            registry.config().onMeterAdded(
                (meter) -> logger.trace(
                    "Meter '{}' added. Callstack: {}",
                    meter.getId().getName(),
                    convertStackTraceToString(new IllegalStateException("Dummy")))
            );
        }

        return registry;
    }

    public static void recordSystemUsage(
        float averageSystemCpuUsage,
        float freeMemoryAvailableInMB
    ) {
        if (compositeRegistry.getRegistries().isEmpty() || cpuOptions == null || memoryOptions == null) {
            return;
        }

        if (cpuOptions.isEnabled()) {
            DistributionSummary averageSystemCpuUsageMeter = getMeterCache(CosmosMetricName.SYSTEM_CPU)
                .getOrCreateSummary(Tags.empty(), cpuOptions, "%", "Avg. System CPU load", 100d);
            averageSystemCpuUsageMeter.record(averageSystemCpuUsage);
        }

        if (memoryOptions.isEnabled()) {
            DistributionSummary freeMemoryAvailableInMBMeter = getMeterCache(CosmosMetricName.SYSTEM_MEMORY_FREE)
                .getOrCreateSummaryNoHistogram(Tags.empty(), memoryOptions, "MB", "Free memory available", Double.MAX_VALUE);
            freeMemoryAvailableInMBMeter.record(freeMemoryAvailableInMB);
        }
    }

    public static void recordOperation(
        CosmosAsyncClient client,
        CosmosDiagnosticsContext diagnosticsContext
    ) {
        recordOperation(
            client,
            diagnosticsContext,
            diagnosticsContext.getStatusCode(),
            diagnosticsContext.getSubStatusCode(),
            diagnosticsContext.getMaxItemCount(),
            diagnosticsContext.getActualItemCount(),
            diagnosticsContext.getContainerName(),
            diagnosticsContext.getDatabaseName(),
            diagnosticsContext.getOperationType(),
            diagnosticsContext.isPointOperation(),
            diagnosticsContext.getResourceType(),
            diagnosticsContext.getEffectiveConsistencyLevel(),
            diagnosticsContext.getOperationId(),
            diagnosticsContext.getTotalRequestCharge(),
            diagnosticsContext.getDuration(),
            diagnosticsCtxAccessor.getOpCountPerEvaluation(diagnosticsContext),
            diagnosticsCtxAccessor.getRetriedOpCountPerEvaluation(diagnosticsContext),
            diagnosticsCtxAccessor.getGlobalOpCount(diagnosticsContext),
            diagnosticsCtxAccessor.getTargetMaxMicroBatchSize(diagnosticsContext)
        );
    }

    private static boolean hasAnyActualMeterRegistry() {

        Instant nowSnapshot = Instant.now();
        DescendantValidationResult snapshot = lastDescendantValidation;
        if (nowSnapshot.isBefore(snapshot.getExpiration())) {
            return snapshot.getResult();
        }

        synchronized (lockObject) {
            snapshot = lastDescendantValidation;
            if (nowSnapshot.isBefore(snapshot.getExpiration())) {
                return snapshot.getResult();
            }

            DescendantValidationResult newResult = new DescendantValidationResult(
                nowSnapshot.plus(10, ChronoUnit.SECONDS),
                hasAnyActualMeterRegistryCore(compositeRegistry, 1)
            );

            lastDescendantValidation = newResult;
            return newResult.getResult();
        }
    }

    private static boolean hasAnyActualMeterRegistryCore(CompositeMeterRegistry compositeMeterRegistry, int depth) {

        if (depth > 100) {
            return true;
        }

        for (MeterRegistry registry : compositeMeterRegistry.getRegistries()) {
            if (registry instanceof CompositeMeterRegistry) {
                if (hasAnyActualMeterRegistryCore((CompositeMeterRegistry)registry, depth + 1)) {
                    return true;
                }
            } else {
                return true;
            }
        }

        return false;
    }

    private static void recordOperation(
        CosmosAsyncClient client,
        CosmosDiagnosticsContext diagnosticsContext,
        int statusCode,
        int subStatusCode,
        Integer maxItemCount,
        Integer actualItemCount,
        String containerId,
        String databaseId,
        String operationType,
        boolean isPointOperation,
        String resourceType,
        ConsistencyLevel consistencyLevel,
        String operationId,
        float requestCharge,
        Duration latency,
        Long opCountPerEvaluation,
        Long opRetriedCountPerEvaluation,
        Long globalOpCount,
        Integer targetMaxMicroBatchSize) {

        boolean isClientTelemetryMetricsEnabled = clientAccessor.shouldEnableEmptyPageDiagnostics(client);

        if (!hasAnyActualMeterRegistry() || !isClientTelemetryMetricsEnabled) {
            return;
        }

        Tag clientCorrelationTag = clientAccessor.getClientCorrelationTag(client);
        String accountTagValue = clientAccessor.getAccountTagValue(client);

        EnumSet<TagName> metricTagNames = clientAccessor.getMetricTagNames(client);
        EnumSet<MetricCategory> metricCategories = clientAccessor.getMetricCategories(client);

        Set<String> contactedRegions = Collections.emptySet();
        if (metricCategories.contains(MetricCategory.OperationDetails)) {
            contactedRegions = diagnosticsContext.getContactedRegionNames();
        }

        Tags operationTags = createOperationTags(
            metricTagNames,
            statusCode,
            subStatusCode,
            containerId,
            databaseId,
            operationType,
            resourceType,
            consistencyLevel,
            operationId,
            isPointOperation,
            contactedRegions,
            clientCorrelationTag,
            accountTagValue
        );

        OperationMetricProducer metricProducer = new OperationMetricProducer(metricCategories, metricTagNames, operationTags);
        metricProducer.recordOperation(
            client,
            requestCharge,
            latency,
            maxItemCount == null ? -1 : maxItemCount,
            actualItemCount == null ? -1: actualItemCount,
            opCountPerEvaluation == null ? 0 : opCountPerEvaluation,
            opRetriedCountPerEvaluation == null ? 0 : opRetriedCountPerEvaluation,
            globalOpCount == null ? 0 : globalOpCount,
            targetMaxMicroBatchSize == null ? 0 : targetMaxMicroBatchSize,
            diagnosticsContext,
            contactedRegions
        );
    }

    public static RntbdMetricsCompletionRecorder createRntbdMetrics(
        RntbdTransportClient client,
        RntbdEndpoint endpoint) {

        return new RntbdMetricsV2(compositeRegistry, client, endpoint);
    }

    public static synchronized void add(
        MeterRegistry registry,
        CosmosMeterOptions cpuOptions,
        CosmosMeterOptions memoryOptions) {
        if (registryRefCount
            .computeIfAbsent(registry, (meterRegistry) -> new AtomicLong(0))
            .incrementAndGet() == 1L) {
            ClientTelemetryMetrics
                .compositeRegistry
                .add(registry);

            // CPU and Memory signals are scoped system-wide - not for each client
            // technically multiple CosmosClients could have different configuration for system meter options
            // which isn't possible because it is a global system-wide metric
            // so using most intuitive compromise - last meter options wins
            ClientTelemetryMetrics.cpuOptions = cpuOptions;
            ClientTelemetryMetrics.memoryOptions = memoryOptions;

            // reset the cached flag whether any actual meter registry is available
            lastDescendantValidation = new DescendantValidationResult(Instant.MIN, true);

            clearAllMeterCaches();
        }
    }

    public static synchronized void remove(MeterRegistry registry) {
        if (registryRefCount
            .get(registry)
            .decrementAndGet() == 0L) {

            registry.clear();
            registry.close();

            ClientTelemetryMetrics
                .compositeRegistry
                .remove(registry);

            if (ClientTelemetryMetrics.compositeRegistry.getRegistries().isEmpty()) {
                ClientTelemetryMetrics.compositeRegistry = createFreshRegistry();
            }

            // reset the cached flag whether any actual meter registry is available
            lastDescendantValidation = new DescendantValidationResult(Instant.MIN, true);

            clearAllMeterCaches();
        }
    }

    public static String escape(String value) {
        return PERCENT_ESCAPER.escape(value);
    }

    private static Tags createOperationTags(
        EnumSet<TagName> metricTagNames,
        int statusCode,
        int subStatusCode,
        String containerId,
        String databaseId,
        String operationType,
        String resourceType,
        ConsistencyLevel consistencyLevel,
        String operationId,
        boolean isPointOperation,
        Set<String> contactedRegions,
        Tag clientCorrelationTag,
        String accountTagValue) {

        List<Tag> effectiveTags = new ArrayList<>();

        if (metricTagNames.contains(TagName.ClientCorrelationId)) {
            effectiveTags.add(clientCorrelationTag);
        }

        if (metricTagNames.contains(TagName.Container)) {
            String containerTagValue =
                escape(accountTagValue)
                + "/"
                + (databaseId != null ? escape(databaseId) : "NONE")
                + "/"
                + (containerId != null ? escape(containerId) : "NONE");

            effectiveTags.add(Tag.of(TagName.Container.toString(), containerTagValue));
        }

        if (metricTagNames.contains(TagName.Operation)) {
            String operationTagValue = !isPointOperation && !Strings.isNullOrWhiteSpace(operationId)
                ? resourceType + "/" + operationType + "/" + escape(operationId)
                : resourceType + "/" + operationType;

            effectiveTags.add(Tag.of(TagName.Operation.toString(), operationTagValue));
        }

        if (metricTagNames.contains(TagName.OperationStatusCode)) {
            effectiveTags.add(Tag.of(TagName.OperationStatusCode.toString(), String.valueOf(statusCode)));
        }

        if (metricTagNames.contains(TagName.OperationSubStatusCode)) {
            effectiveTags.add(Tag.of(TagName.OperationSubStatusCode.toString(), String.valueOf(subStatusCode)));
        }

        if (metricTagNames.contains(TagName.ConsistencyLevel)) {
            assert consistencyLevel != null : "ConsistencyLevel must never be null here.";
            effectiveTags.add(Tag.of(
                TagName.ConsistencyLevel.toString(),
                consistencyLevel.toString()
            ));
        }

        if (metricTagNames.contains(TagName.RegionName)) {
            effectiveTags.add(Tag.of(
                TagName.RegionName.toString(),
                contactedRegions != null && !contactedRegions.isEmpty()
                    ? String.join(", ", contactedRegions) : "NONE"
            ));
        }

        return Tags.of(effectiveTags);
    }

    private static Tags getEffectiveTags(Tags tags, CosmosMeterOptions meterOptions) {
        EnumSet<TagName> suppressedTags = meterOptions.getSuppressedTagNames();
        if (suppressedTags == null || suppressedTags.isEmpty()) {
            return tags;
        }

        HashSet<String> suppressedNames = new HashSet<>();
        for (TagName t: suppressedTags) {
            suppressedNames.add(t.name());
        }

        List<Tag> result = new ArrayList<>();
        for (Tag t: tags) {
            if (!suppressedNames.contains(t.getKey())) {
                result.add(t);
            }
        }

        return Tags.of(result);
    }

    private static class OperationMetricProducer {
        private final EnumSet<TagName> metricTagNames;
        private final EnumSet<MetricCategory> metricCategories;
        private final Tags operationTags;

        public OperationMetricProducer(EnumSet<MetricCategory> metricCategories, EnumSet<TagName> metricTagNames, Tags operationTags) {
            this.metricCategories = metricCategories;
            this.metricTagNames = metricTagNames;
            this.operationTags = operationTags;
        }

        public void recordOperation(
            CosmosAsyncClient cosmosAsyncClient,
            float requestCharge,
            Duration latency,
            int maxItemCount,
            int actualItemCount,
            long opCountPerEvaluation,
            long opRetriedCountPerEvaluation,
            long globalOpCount,
            int targetMaxMicroBatchSize,
            CosmosDiagnosticsContext diagnosticsContext,
            Set<String> contactedRegions) {

            CosmosMeterOptions callsOptions = clientAccessor.getMeterOptions(
                cosmosAsyncClient,
                CosmosMetricName.OPERATION_SUMMARY_CALLS);

            if (callsOptions.isEnabled()) {
                Tags effectiveTags = getEffectiveTags(operationTags, callsOptions);
                Counter operationsCounter = getMeterCache(CosmosMetricName.OPERATION_SUMMARY_CALLS)
                    .getOrCreateCounter(effectiveTags, callsOptions, "calls", "Operation calls");
                operationsCounter.increment();
            }

            CosmosMeterOptions requestChargeOptions = clientAccessor.getMeterOptions(
                cosmosAsyncClient,
                CosmosMetricName.OPERATION_SUMMARY_REQUEST_CHARGE);
            if (requestChargeOptions.isEnabled()) {
                Tags effectiveTags = getEffectiveTags(operationTags, requestChargeOptions);
                DistributionSummary requestChargeMeter = getMeterCache(CosmosMetricName.OPERATION_SUMMARY_REQUEST_CHARGE)
                    .getOrCreateSummary(effectiveTags, requestChargeOptions, "RU (request unit)", "Operation RU charge", 100_000d);
                requestChargeMeter.record(Math.min(requestCharge, 100_000d));
            }

            if (this.metricCategories.contains(MetricCategory.OperationDetails)) {
                CosmosMeterOptions regionsOptions = clientAccessor.getMeterOptions(
                    cosmosAsyncClient,
                    CosmosMetricName.OPERATION_DETAILS_REGIONS_CONTACTED);
                if (regionsOptions.isEnabled()) {
                    Tags effectiveTags = getEffectiveTags(operationTags, regionsOptions);
                    DistributionSummary regionsContactedMeter = getMeterCache(CosmosMetricName.OPERATION_DETAILS_REGIONS_CONTACTED)
                        .getOrCreateSummaryNoHistogram(effectiveTags, regionsOptions, "Regions contacted", "Operation - regions contacted", 100d);
                    if (contactedRegions != null && contactedRegions.size() > 0) {
                        regionsContactedMeter.record(Math.min(contactedRegions.size(), 100d));
                    }
                }

                this.recordItemCounts(cosmosAsyncClient, maxItemCount, actualItemCount);
            }

            CosmosMeterOptions latencyOptions = clientAccessor.getMeterOptions(
                cosmosAsyncClient,
                CosmosMetricName.OPERATION_SUMMARY_LATENCY);
            if (latencyOptions.isEnabled()) {
                Tags effectiveTags = getEffectiveTags(operationTags, latencyOptions);
                Timer latencyMeter = getMeterCache(CosmosMetricName.OPERATION_SUMMARY_LATENCY)
                    .getOrCreateTimer(effectiveTags, latencyOptions, "Operation latency", Duration.ofSeconds(300));
                latencyMeter.record(latency);
            }

            for (CosmosDiagnostics diagnostics: diagnosticsContext.getDiagnostics()) {
                Collection<ClientSideRequestStatistics> clientSideRequestStatistics =
                    diagnosticsAccessor.getClientSideRequestStatistics(diagnostics);

                if (clientSideRequestStatistics != null) {
                    for (ClientSideRequestStatistics requestStatistics : clientSideRequestStatistics) {

                        recordStoreResponseStatistics(
                            diagnosticsContext,
                            cosmosAsyncClient,
                            requestStatistics.getResponseStatisticsList(),
                            actualItemCount,
                            opCountPerEvaluation,
                            opRetriedCountPerEvaluation,
                            globalOpCount,
                            targetMaxMicroBatchSize);

                        recordStoreResponseStatistics(
                            diagnosticsContext,
                            cosmosAsyncClient,
                            requestStatistics.getSupplementalResponseStatisticsList(),
                            -1,
                            -1,
                            -1,
                            -1,
                            -1);

                        recordGatewayStatistics(
                            diagnosticsContext,
                            cosmosAsyncClient,
                            requestStatistics.getDuration(),
                            requestStatistics.getGatewayStatisticsList(),
                            requestStatistics.getRequestPayloadSizeInBytes(),
                            actualItemCount,
                            opCountPerEvaluation,
                            opRetriedCountPerEvaluation,
                            globalOpCount,
                            targetMaxMicroBatchSize);

                        recordAddressResolutionStatistics(
                            diagnosticsContext,
                            cosmosAsyncClient,
                            requestStatistics.getAddressResolutionStatistics());
                    }
                }

                FeedResponseDiagnostics feedDiagnostics = diagnosticsAccessor
                    .getFeedResponseDiagnostics(diagnostics);

                if (feedDiagnostics == null) {
                    continue;
                }

                QueryInfo.QueryPlanDiagnosticsContext queryPlanDiagnostics =
                    feedDiagnostics.getQueryPlanDiagnosticsContext();

                recordQueryPlanDiagnostics(diagnosticsContext, cosmosAsyncClient, queryPlanDiagnostics);
            }
        }

        private void recordQueryPlanDiagnostics(
            CosmosDiagnosticsContext ctx,
            CosmosAsyncClient cosmosAsyncClient,
            QueryInfo.QueryPlanDiagnosticsContext queryPlanDiagnostics
        ) {
            if (queryPlanDiagnostics == null || !this.metricCategories.contains(MetricCategory.RequestSummary)) {
                return;
            }

            Tags requestTags = operationTags.and(
                createQueryPlanTags(metricTagNames)
            );

            CosmosMeterOptions requestsOptions = clientAccessor.getMeterOptions(
                cosmosAsyncClient,
                CosmosMetricName.REQUEST_SUMMARY_GATEWAY_REQUESTS);
            if (requestsOptions.isEnabled() &&
                (!requestsOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                Tags effectiveTags = getEffectiveTags(requestTags, requestsOptions);
                Counter requestCounter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_GATEWAY_REQUESTS)
                    .getOrCreateCounter(effectiveTags, requestsOptions, "requests", "Gateway requests");
                requestCounter.increment();
            }

            Duration latency = queryPlanDiagnostics.getDuration();

            if (latency != null) {
                CosmosMeterOptions latencyOptions = clientAccessor.getMeterOptions(
                    cosmosAsyncClient,
                    CosmosMetricName.REQUEST_SUMMARY_GATEWAY_LATENCY);
                if (latencyOptions.isEnabled() &&
                    (!latencyOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Tags effectiveTags = getEffectiveTags(requestTags, latencyOptions);
                    Timer requestLatencyMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_GATEWAY_LATENCY)
                        .getOrCreateTimer(effectiveTags, latencyOptions, "Gateway Request latency", Duration.ofSeconds(300));
                    requestLatencyMeter.record(latency);
                }
            }

            recordRequestTimeline(
                ctx,
                cosmosAsyncClient,
                CosmosMetricName.REQUEST_DETAILS_GATEWAY_TIMELINE,
                queryPlanDiagnostics.getRequestTimeline(), requestTags);
        }

        private void recordRequestPayloadSizes(
            CosmosDiagnosticsContext ctx,
            CosmosAsyncClient client,
            int requestPayloadSizeInBytes,
            int responsePayloadSizeInBytes
        ) {
            CosmosMeterOptions reqSizeOptions = clientAccessor.getMeterOptions(
                client,
                CosmosMetricName.REQUEST_SUMMARY_SIZE_REQUEST);
            if (reqSizeOptions.isEnabled() &&
                (!reqSizeOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                Tags effectiveTags = getEffectiveTags(operationTags, reqSizeOptions);
                DistributionSummary requestPayloadSizeMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_SIZE_REQUEST)
                    .getOrCreateSummaryNoHistogram(effectiveTags, reqSizeOptions, "bytes", "Request payload size in bytes", 16d * 1024);
                requestPayloadSizeMeter.record(requestPayloadSizeInBytes);
            }

            CosmosMeterOptions rspSizeOptions = clientAccessor.getMeterOptions(
                client,
                CosmosMetricName.REQUEST_SUMMARY_SIZE_RESPONSE);
            if (rspSizeOptions.isEnabled() &&
                (!rspSizeOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                Tags effectiveTags = getEffectiveTags(operationTags, rspSizeOptions);
                DistributionSummary responsePayloadSizeMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_SIZE_RESPONSE)
                    .getOrCreateSummaryNoHistogram(effectiveTags, rspSizeOptions, "bytes", "Response payload size in bytes", 16d * 1024);
                responsePayloadSizeMeter.record(responsePayloadSizeInBytes);
            }
        }

        private void recordItemCounts(
            CosmosAsyncClient client,
            int maxItemCount,
            int actualItemCount
        ) {
            if (maxItemCount > 0 && this.metricCategories.contains(MetricCategory.OperationDetails)) {

                CosmosMeterOptions maxItemCountOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.OPERATION_DETAILS_MAX_ITEM_COUNT);
                if (maxItemCountOptions.isEnabled()) {
                    Tags effectiveTags = getEffectiveTags(operationTags, maxItemCountOptions);
                    DistributionSummary maxItemCountMeter = getMeterCache(CosmosMetricName.OPERATION_DETAILS_MAX_ITEM_COUNT)
                        .getOrCreateSummaryNoHistogram(effectiveTags, maxItemCountOptions, "item count", "Request max. item count", 100_000d);
                    maxItemCountMeter.record(Math.max(0, Math.min(maxItemCount, 100_000d)));
                }

                CosmosMeterOptions actualItemCountOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.OPERATION_DETAILS_ACTUAL_ITEM_COUNT);
                if (actualItemCountOptions.isEnabled()) {
                    Tags effectiveTags = getEffectiveTags(operationTags, actualItemCountOptions);
                    DistributionSummary actualItemCountMeter = getMeterCache(CosmosMetricName.OPERATION_DETAILS_ACTUAL_ITEM_COUNT)
                        .getOrCreateSummaryNoHistogram(effectiveTags, actualItemCountOptions, "item count", "Response actual item count", 100_000d);
                    actualItemCountMeter.record(Math.max(0, Math.min(actualItemCount, 100_000d)));
                }
            }
        }

        private Tags createRequestTags(
            EnumSet<TagName> metricTagNames,
            String pkRangeId,
            int statusCode,
            int subStatusCode,
            String resourceType,
            String operationType,
            String regionName,
            String serviceEndpoint,
            String serviceAddress
        ) {
            List<Tag> effectiveTags = new ArrayList<>();
            if (metricTagNames.contains(TagName.PartitionKeyRangeId)) {
                effectiveTags.add(Tag.of(
                    TagName.PartitionKeyRangeId.toString(),
                    Strings.isNullOrWhiteSpace(pkRangeId) ? "NONE" : escape(pkRangeId)));
            }

            if (metricTagNames.contains(TagName.RequestStatusCode)) {
                effectiveTags.add(Tag.of(
                    TagName.RequestStatusCode.toString(),
                    statusCode + "/" + subStatusCode));
            }

            if (metricTagNames.contains(TagName.RequestOperationType)) {
                effectiveTags.add(Tag.of(
                    TagName.RequestOperationType.toString(),
                    resourceType + "/" + operationType));
            }

            if (metricTagNames.contains(TagName.RegionName)) {
                effectiveTags.add(Tag.of(
                    TagName.RegionName.toString(),
                    regionName != null ? regionName : "NONE"));
            }

            if (metricTagNames.contains(TagName.ServiceEndpoint)) {
                effectiveTags.add(Tag.of(
                    TagName.ServiceEndpoint.toString(),
                    serviceEndpoint != null ? escape(serviceEndpoint) : "NONE"));
            }

            String effectiveServiceAddress = serviceAddress != null ? escape(serviceAddress) : "NONE";
            if (metricTagNames.contains(TagName.ServiceAddress)) {
                effectiveTags.add(Tag.of(
                    TagName.ServiceAddress.toString(),
                    effectiveServiceAddress));
            }

            boolean containsPartitionId = metricTagNames.contains(TagName.PartitionId);
            boolean containsReplicaId = metricTagNames.contains(TagName.ReplicaId);
            if (containsPartitionId || containsReplicaId) {

                String partitionId = "NONE";
                String replicaId = "NONE";

                String[] partitionAndReplicaId =
                    StoreResultDiagnostics.getPartitionAndReplicaId(effectiveServiceAddress);
                if (partitionAndReplicaId.length == 2) {
                    partitionId = partitionAndReplicaId[0];
                    replicaId = partitionAndReplicaId[1];
                }

                if (containsPartitionId) {
                    effectiveTags.add(Tag.of(
                        TagName.PartitionId.toString(),
                        partitionId));
                }

                if (containsReplicaId) {
                    effectiveTags.add(Tag.of(
                        TagName.ReplicaId.toString(),
                        replicaId));
                }
            }

            return Tags.of(effectiveTags);
        }

        private Tags createQueryPlanTags(
            EnumSet<TagName> metricTagNames
        ) {
            List<Tag> effectiveTags = new ArrayList<>();

            if (metricTagNames.contains(TagName.RequestOperationType)) {
                effectiveTags.add(QUERYPLAN_TAG);
            }
            if (metricTagNames.contains(TagName.RequestStatusCode)) {
                effectiveTags.add(Tag.of(TagName.RequestStatusCode.toString(),"NONE"));
            }
            if (metricTagNames.contains(TagName.PartitionKeyRangeId)) {
                effectiveTags.add(Tag.of(TagName.PartitionKeyRangeId.toString(),"NONE"));
            }

            return Tags.of(effectiveTags);
        }

        private Tags createAddressResolutionTags(
            EnumSet<TagName> metricTagNames,
            String serviceEndpoint,
            boolean isForceRefresh,
            boolean isForceCollectionRoutingMapRefresh
        ) {
            List<Tag> effectiveTags = new ArrayList<>();
            if (metricTagNames.contains(TagName.ServiceEndpoint)) {
                effectiveTags.add(Tag.of(
                    TagName.ServiceEndpoint.toString(),
                    serviceEndpoint != null ? escape(serviceEndpoint) : "NONE"));
            }

            if (metricTagNames.contains(TagName.IsForceRefresh)) {
                effectiveTags.add(Tag.of(
                    TagName.IsForceRefresh.toString(),
                    isForceRefresh ? "True" : "False"));
            }

            if (metricTagNames.contains(TagName.IsForceCollectionRoutingMapRefresh)) {
                effectiveTags.add(Tag.of(
                    TagName.IsForceCollectionRoutingMapRefresh.toString(),
                    isForceCollectionRoutingMapRefresh ? "True" : "False"));
            }

            return Tags.of(effectiveTags);
        }

        private void recordRntbdEndpointStatistics(
            CosmosAsyncClient client,
            RntbdEndpointStatistics endpointStatistics,
            Tags requestTags) {
            if (endpointStatistics == null || !this.metricCategories.contains(MetricCategory.Legacy)) {
                return;
            }

            CosmosMeterOptions acquiredOptions = clientAccessor.getMeterOptions(
                client,
                CosmosMetricName.LEGACY_DIRECT_ENDPOINT_STATISTICS_ACQUIRED);
            if (acquiredOptions.isEnabled()) {
                Tags effectiveTags = getEffectiveTags(requestTags, acquiredOptions);
                DistributionSummary acquiredChannelsMeter = getMeterCache(CosmosMetricName.LEGACY_DIRECT_ENDPOINT_STATISTICS_ACQUIRED)
                    .getOrCreateSummaryNoHistogram(effectiveTags, acquiredOptions, "#", "Endpoint statistics(acquired channels)", 100_000d);

                acquiredChannelsMeter.record(endpointStatistics.getAcquiredChannels());
            }

            CosmosMeterOptions availableOptions = clientAccessor.getMeterOptions(
                client,
                CosmosMetricName.LEGACY_DIRECT_ENDPOINT_STATISTICS_AVAILABLE);
            if (availableOptions.isEnabled()) {
                Tags effectiveTags = getEffectiveTags(requestTags, availableOptions);
                DistributionSummary availableChannelsMeter = getMeterCache(CosmosMetricName.LEGACY_DIRECT_ENDPOINT_STATISTICS_AVAILABLE)
                    .getOrCreateSummaryNoHistogram(effectiveTags, availableOptions, "#", "Endpoint statistics(available channels)", 100_000d);
                availableChannelsMeter.record(endpointStatistics.getAvailableChannels());
            }

            CosmosMeterOptions inflightOptions = clientAccessor.getMeterOptions(
                client,
                CosmosMetricName.LEGACY_DIRECT_ENDPOINT_STATISTICS_INFLIGHT);
            if (inflightOptions.isEnabled()) {
                Tags effectiveTags = getEffectiveTags(requestTags, inflightOptions);
                DistributionSummary inflightRequestsMeter = getMeterCache(CosmosMetricName.LEGACY_DIRECT_ENDPOINT_STATISTICS_INFLIGHT)
                    .getOrCreateSummary(effectiveTags, inflightOptions, "#", "Endpoint statistics(inflight requests)", 1_000_000d);
                inflightRequestsMeter.record(endpointStatistics.getInflightRequests());
            }
        }

        private void recordRequestTimeline(
            CosmosDiagnosticsContext ctx,
            CosmosAsyncClient client,
            CosmosMetricName name,
            RequestTimeline requestTimeline,
            Tags requestTags) {

            if (requestTimeline == null || !this.metricCategories.contains(MetricCategory.RequestDetails)) {
                return;
            }

            CosmosMeterOptions timelineOptions = clientAccessor.getMeterOptions(
                client,
                name);
            if (!timelineOptions.isEnabled() ||
                (timelineOptions.isDiagnosticThresholdsFilteringEnabled() && !ctx.isThresholdViolated())) {
                return;
            }
            for (RequestTimeline.Event event : requestTimeline) {
                Duration duration = event.getDuration();
                if (duration == null || duration == Duration.ZERO) {
                    continue;
                }

                Timer eventMeter = Timer
                    .builder(timelineOptions.getMeterName().toString() + "." + escape(event.getName()))
                    .description("Request timeline (" + event.getName() + ")")
                    .maximumExpectedValue(Duration.ofSeconds(300))
                    .publishPercentiles(timelineOptions.getPercentiles())
                    .publishPercentileHistogram(timelineOptions.isHistogramPublishingEnabled())
                    .tags(getEffectiveTags(requestTags, timelineOptions))
                    .register(compositeRegistry);
                eventMeter.record(duration);
            }
        }

        private void recordStoreResponseStatistics(
            CosmosDiagnosticsContext ctx,
            CosmosAsyncClient client,
            Collection<ClientSideRequestStatistics.StoreResponseStatistics> storeResponseStatistics,
            int actualItemCount,
            long opCountPerEvaluation,
            long opRetriedCountPerEvaluation,
            long globalOpCount,
            int targetMaxMicroBatchSize) {

            if (!this.metricCategories.contains(MetricCategory.RequestSummary)) {
                return;
            }

            for (ClientSideRequestStatistics.StoreResponseStatistics responseStatistics: storeResponseStatistics) {
                StoreResultDiagnostics storeResultDiagnostics = responseStatistics.getStoreResult();
                StoreResponseDiagnostics storeResponseDiagnostics =
                    storeResultDiagnostics.getStoreResponseDiagnostics();

                Tags requestTags = operationTags.and(
                    createRequestTags(
                        metricTagNames,
                        storeResponseDiagnostics.getPartitionKeyRangeId(),
                        storeResponseDiagnostics.getStatusCode(),
                        storeResponseDiagnostics.getSubStatusCode(),
                        responseStatistics.getRequestResourceType().toString(),
                        responseStatistics.getRequestOperationType().toString(),
                        responseStatistics.getRegionName(),
                        storeResultDiagnostics.getStorePhysicalAddressEscapedAuthority(),
                        storeResultDiagnostics.getStorePhysicalAddressEscapedPath())
                );

                Double backendLatency = storeResultDiagnostics.getBackendLatencyInMs();

                if (backendLatency != null) {

                    CosmosMeterOptions beLatencyOptions = clientAccessor.getMeterOptions(
                        client,
                        CosmosMetricName.REQUEST_SUMMARY_DIRECT_BACKEND_LATENCY);
                    if (beLatencyOptions.isEnabled() &&
                        (!beLatencyOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                        Tags effectiveTags = getEffectiveTags(requestTags, beLatencyOptions);
                        DistributionSummary backendRequestLatencyMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_DIRECT_BACKEND_LATENCY)
                            .getOrCreateSummary(effectiveTags, beLatencyOptions, "ms", "Backend service latency", 6_000d);
                        backendRequestLatencyMeter.record(storeResultDiagnostics.getBackendLatencyInMs());
                    }
                }

                CosmosMeterOptions ruOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.REQUEST_SUMMARY_DIRECT_REQUEST_CHARGE);
                if (ruOptions.isEnabled() &&
                    (!ruOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    double requestCharge = storeResponseDiagnostics.getRequestCharge();
                    Tags effectiveTags = getEffectiveTags(requestTags, ruOptions);
                    DistributionSummary requestChargeMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_DIRECT_REQUEST_CHARGE)
                        .getOrCreateSummary(effectiveTags, ruOptions, "RU (request unit)", "RNTBD Request RU charge", 100_000d);
                    requestChargeMeter.record(Math.min(requestCharge, 100_000d));
                }

                CosmosMeterOptions latencyOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.REQUEST_SUMMARY_DIRECT_LATENCY);
                if (latencyOptions.isEnabled() &&
                    (!latencyOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Duration latency = responseStatistics.getDuration();
                    if (latency != null) {
                        Tags effectiveTags = getEffectiveTags(requestTags, latencyOptions);
                        Timer requestLatencyMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_DIRECT_LATENCY)
                            .getOrCreateTimer(effectiveTags, latencyOptions, "RNTBD Request latency", Duration.ofSeconds(6));
                        requestLatencyMeter.record(latency);
                    }
                }

                CosmosMeterOptions reqOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.REQUEST_SUMMARY_DIRECT_REQUESTS);
                if (reqOptions.isEnabled() &&
                    (!reqOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Tags effectiveTags = getEffectiveTags(requestTags, reqOptions);
                    Counter requestCounter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_DIRECT_REQUESTS)
                        .getOrCreateCounter(effectiveTags, reqOptions, "requests", "RNTBD requests");
                    requestCounter.increment();
                }

                CosmosMeterOptions actualItemCountOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.REQUEST_SUMMARY_DIRECT_ACTUAL_ITEM_COUNT);

                if (actualItemCountOptions.isEnabled()
                    && (!actualItemCountOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Tags effectiveTags = getEffectiveTags(requestTags, actualItemCountOptions);
                    DistributionSummary actualItemCountMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_DIRECT_ACTUAL_ITEM_COUNT)
                        .getOrCreateSummaryNoHistogram(effectiveTags, actualItemCountOptions, "item count", "Rntbd response actual item count", 100_000d);
                    actualItemCountMeter.record(Math.max(0, Math.min(actualItemCount, 100_000d)));
                }

                CosmosMeterOptions opCountPerEvaluationOptions = clientAccessor.getMeterOptions(
                  client,
                  CosmosMetricName.REQUEST_SUMMARY_DIRECT_BULK_OP_COUNT_PER_EVALUATION
                );

                if (opCountPerEvaluationOptions.isEnabled()
                    && (!opCountPerEvaluationOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Tags effectiveTags = getEffectiveTags(requestTags, opCountPerEvaluationOptions);
                    DistributionSummary opCountPerEvaluationMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_DIRECT_BULK_OP_COUNT_PER_EVALUATION)
                        .getOrCreateSummaryNoHistogram(effectiveTags, opCountPerEvaluationOptions, "item count", "Operation count per evaluation", Double.MAX_VALUE);
                    opCountPerEvaluationMeter.record(Math.max(0, Math.min(opCountPerEvaluation, Double.MAX_VALUE)));
                }

                CosmosMeterOptions opRetriedCountPerEvaluationOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.REQUEST_SUMMARY_DIRECT_BULK_OP_RETRIED_COUNT_PER_EVALUATION
                );

                if (opRetriedCountPerEvaluationOptions.isEnabled()
                    && (!opRetriedCountPerEvaluationOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Tags effectiveTags = getEffectiveTags(requestTags, opRetriedCountPerEvaluationOptions);
                    DistributionSummary opRetriedCountPerEvaluationMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_DIRECT_BULK_OP_RETRIED_COUNT_PER_EVALUATION)
                        .getOrCreateSummaryNoHistogram(effectiveTags, opRetriedCountPerEvaluationOptions, "item count", "Operation retried count per evaluation", Double.MAX_VALUE);
                    opRetriedCountPerEvaluationMeter.record(Math.max(0, Math.min(opRetriedCountPerEvaluation, Double.MAX_VALUE)));
                }

                CosmosMeterOptions globalOpCountOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.REQUEST_SUMMARY_DIRECT_BULK_GLOBAL_OP_COUNT
                );

                if (globalOpCountOptions.isEnabled()
                    && (!globalOpCountOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Tags effectiveTags = getEffectiveTags(requestTags, globalOpCountOptions);
                    DistributionSummary globalOpCountMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_DIRECT_BULK_GLOBAL_OP_COUNT)
                        .getOrCreateSummaryNoHistogram(effectiveTags, globalOpCountOptions, "item count", "Global operation count", Double.MAX_VALUE);
                    globalOpCountMeter.record(Math.max(0, Math.min(globalOpCount, Double.MAX_VALUE)));
                }


                CosmosMeterOptions targetMaxMicroBatchSizeOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.REQUEST_SUMMARY_DIRECT_BULK_TARGET_MAX_MICRO_BATCH_SIZE
                );

                if (targetMaxMicroBatchSizeOptions.isEnabled()
                    && (!targetMaxMicroBatchSizeOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Tags effectiveTags = getEffectiveTags(requestTags, targetMaxMicroBatchSizeOptions);
                    DistributionSummary targetMaxMicroBatchSizeMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_DIRECT_BULK_TARGET_MAX_MICRO_BATCH_SIZE)
                        .getOrCreateSummaryNoHistogram(effectiveTags, targetMaxMicroBatchSizeOptions, "item count", "Target max micro batch size", 101d);
                    targetMaxMicroBatchSizeMeter.record(Math.max(0, Math.min(targetMaxMicroBatchSize, 101d)));
                }

                if (this.metricCategories.contains(MetricCategory.RequestDetails)) {
                    recordRequestTimeline(
                        ctx,
                        client,
                        CosmosMetricName.REQUEST_DETAILS_DIRECT_TIMELINE,
                        storeResponseDiagnostics.getRequestTimeline(), requestTags);
                }

                recordRequestPayloadSizes(
                    ctx,
                    client,
                    storeResponseDiagnostics.getRequestPayloadLength(),
                    storeResponseDiagnostics.getResponsePayloadLength()
                );

                recordRntbdEndpointStatistics(
                    client,
                    storeResponseDiagnostics.getRntbdEndpointStatistics(),
                    requestTags);
            }
        }

        private void recordGatewayStatistics(
            CosmosDiagnosticsContext ctx,
            CosmosAsyncClient client,
            Duration latency,
            List<ClientSideRequestStatistics.GatewayStatistics> gatewayStatisticsList,
            int requestPayloadSizeInBytes,
            int actualItemCount,
            long opCountPerEvaluation,
            long opRetriedCountPerEvaluation,
            long globalOpCount,
            int targetMaxMicroBatchSize) {

            if (gatewayStatisticsList == null
                || gatewayStatisticsList.size() == 0
                || !this.metricCategories.contains(MetricCategory.RequestSummary)) {
                return;
            }

            EnumSet<TagName> metricTagNamesForGateway = metricTagNames.clone();
            metricTagNamesForGateway.remove(TagName.RegionName);
            metricTagNamesForGateway.remove(TagName.ServiceAddress);
            metricTagNamesForGateway.remove(TagName.ServiceEndpoint);
            metricTagNamesForGateway.remove(TagName.PartitionId);
            metricTagNamesForGateway.remove(TagName.ReplicaId);

            for (ClientSideRequestStatistics.GatewayStatistics gatewayStats : gatewayStatisticsList) {
                Tags requestTags = operationTags.and(
                    createRequestTags(
                        metricTagNamesForGateway,
                        gatewayStats.getPartitionKeyRangeId(),
                        gatewayStats.getStatusCode(),
                        gatewayStats.getSubStatusCode(),
                        gatewayStats.getResourceType().toString(),
                        gatewayStats.getOperationType().toString(),
                        null,
                        null,
                        null)
                );

                recordRequestPayloadSizes(
                    ctx,
                    client,
                    requestPayloadSizeInBytes,
                    gatewayStats.getResponsePayloadSizeInBytes()
                );

                CosmosMeterOptions reqOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.REQUEST_SUMMARY_GATEWAY_REQUESTS);
                if (reqOptions.isEnabled() &&
                    (!reqOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Tags effectiveTags = getEffectiveTags(requestTags, reqOptions);
                    Counter requestCounter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_GATEWAY_REQUESTS)
                        .getOrCreateCounter(effectiveTags, reqOptions, "requests", "Gateway requests");
                    requestCounter.increment();
                }

                CosmosMeterOptions ruOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.REQUEST_SUMMARY_GATEWAY_REQUEST_CHARGE);
                if (ruOptions.isEnabled() &&
                    (!ruOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    double requestCharge = gatewayStats.getRequestCharge();
                    Tags effectiveTags = getEffectiveTags(requestTags, ruOptions);
                    DistributionSummary requestChargeMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_GATEWAY_REQUEST_CHARGE)
                        .getOrCreateSummary(effectiveTags, ruOptions, "RU (request unit)", "Gateway Request RU charge", 100_000d);
                    requestChargeMeter.record(Math.min(requestCharge, 100_000d));
                }

                if (latency != null) {
                    CosmosMeterOptions latencyOptions = clientAccessor.getMeterOptions(
                        client,
                        CosmosMetricName.REQUEST_SUMMARY_GATEWAY_LATENCY);
                    if (latencyOptions.isEnabled() &&
                        (!latencyOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                        Tags effectiveTags = getEffectiveTags(requestTags, latencyOptions);
                        Timer requestLatencyMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_GATEWAY_LATENCY)
                            .getOrCreateTimer(effectiveTags, latencyOptions, "Gateway Request latency", Duration.ofSeconds(300));
                        requestLatencyMeter.record(latency);
                    }
                }

                CosmosMeterOptions actualItemCountOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.REQUEST_SUMMARY_GATEWAY_ACTUAL_ITEM_COUNT);

                if (actualItemCountOptions.isEnabled()
                    && (!actualItemCountOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Tags effectiveTags = getEffectiveTags(requestTags, actualItemCountOptions);
                    DistributionSummary actualItemCountMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_GATEWAY_ACTUAL_ITEM_COUNT)
                        .getOrCreateSummaryNoHistogram(effectiveTags, actualItemCountOptions, "item count", "Gateway response actual item count", 100_000d);
                    actualItemCountMeter.record(Math.max(0, Math.min(actualItemCount, 100_000d)));
                }

                CosmosMeterOptions opCountPerEvaluationOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.REQUEST_SUMMARY_GATEWAY_BULK_OP_COUNT_PER_EVALUATION
                );

                if (opCountPerEvaluationOptions.isEnabled()
                    && (!opCountPerEvaluationOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Tags effectiveTags = getEffectiveTags(requestTags, opCountPerEvaluationOptions);
                    DistributionSummary opCountPerEvaluationMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_GATEWAY_BULK_OP_COUNT_PER_EVALUATION)
                        .getOrCreateSummaryNoHistogram(effectiveTags, opCountPerEvaluationOptions, "item count", "Operation count per evaluation", Double.MAX_VALUE);
                    opCountPerEvaluationMeter.record(Math.max(0, Math.min(opCountPerEvaluation, Double.MAX_VALUE)));
                }

                CosmosMeterOptions opRetriedCountPerEvaluationOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.REQUEST_SUMMARY_GATEWAY_BULK_OP_RETRIED_COUNT_PER_EVALUATION
                );

                if (opRetriedCountPerEvaluationOptions.isEnabled()
                    && (!opRetriedCountPerEvaluationOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Tags effectiveTags = getEffectiveTags(requestTags, opRetriedCountPerEvaluationOptions);
                    DistributionSummary opRetriedCountPerEvaluationMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_GATEWAY_BULK_OP_RETRIED_COUNT_PER_EVALUATION)
                        .getOrCreateSummaryNoHistogram(effectiveTags, opRetriedCountPerEvaluationOptions, "item count", "Operation retried count per evaluation", Double.MAX_VALUE);
                    opRetriedCountPerEvaluationMeter.record(Math.max(0, Math.min(opRetriedCountPerEvaluation, Double.MAX_VALUE)));
                }

                CosmosMeterOptions globalOpCountOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.REQUEST_SUMMARY_GATEWAY_BULK_GLOBAL_OP_COUNT
                );

                if (globalOpCountOptions.isEnabled()
                    && (!globalOpCountOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Tags effectiveTags = getEffectiveTags(requestTags, globalOpCountOptions);
                    DistributionSummary globalOpCountMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_GATEWAY_BULK_GLOBAL_OP_COUNT)
                        .getOrCreateSummaryNoHistogram(effectiveTags, globalOpCountOptions, "item count", "Global operation count", Double.MAX_VALUE);
                    globalOpCountMeter.record(Math.max(0, Math.min(globalOpCount, Double.MAX_VALUE)));
                }

                CosmosMeterOptions targetMaxMicroBatchSizeOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.REQUEST_SUMMARY_GATEWAY_BULK_TARGET_MAX_MICRO_BATCH_SIZE
                );

                if (targetMaxMicroBatchSizeOptions.isEnabled()
                    && (!targetMaxMicroBatchSizeOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Tags effectiveTags = getEffectiveTags(requestTags, targetMaxMicroBatchSizeOptions);
                    DistributionSummary targetMaxMicroBatchSizeMeter = getMeterCache(CosmosMetricName.REQUEST_SUMMARY_GATEWAY_BULK_TARGET_MAX_MICRO_BATCH_SIZE)
                        .getOrCreateSummaryNoHistogram(effectiveTags, targetMaxMicroBatchSizeOptions, "item count", "Target max micro batch size", 101d);
                    targetMaxMicroBatchSizeMeter.record(Math.max(0, Math.min(targetMaxMicroBatchSize, 101d)));
                }

                recordRequestTimeline(
                    ctx,
                    client,
                    CosmosMetricName.REQUEST_DETAILS_GATEWAY_TIMELINE,
                    gatewayStats.getRequestTimeline(), requestTags);
            }
        }

        private void recordAddressResolutionStatistics(
            CosmosDiagnosticsContext ctx,
            CosmosAsyncClient client,
            Map<String, ClientSideRequestStatistics.AddressResolutionStatistics> addressResolutionStatisticsMap) {

            if (addressResolutionStatisticsMap == null
                || addressResolutionStatisticsMap.size() == 0
                || !this.metricCategories.contains(MetricCategory.AddressResolutions) ) {

                return;
            }

            for (ClientSideRequestStatistics.AddressResolutionStatistics addressResolutionStatistics
                : addressResolutionStatisticsMap.values()) {

                if (addressResolutionStatistics.isInflightRequest() ||
                    addressResolutionStatistics.getEndTimeUTC() == null) {

                    // skipping inflight or failed address resolution statistics
                    // capturing error count etc. won't make sense here - request diagnostic
                    // logs are the right way to debug those - not metrics
                    continue;
                }

                Tags addressResolutionTags = operationTags.and(
                    createAddressResolutionTags(
                        metricTagNames,
                        addressResolutionStatistics.getTargetEndpoint(),
                        addressResolutionStatistics.isForceRefresh(),
                        addressResolutionStatistics.isForceCollectionRoutingMapRefresh()
                    )
                );

                Duration latency = Duration.between(
                    addressResolutionStatistics.getStartTimeUTC(),
                    addressResolutionStatistics.getEndTimeUTC());

                CosmosMeterOptions latencyOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.DIRECT_ADDRESS_RESOLUTION_LATENCY);
                if (latencyOptions.isEnabled() &&
                    (!latencyOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Tags effectiveTags = getEffectiveTags(addressResolutionTags, latencyOptions);
                    Timer addressResolutionLatencyMeter = getMeterCache(CosmosMetricName.DIRECT_ADDRESS_RESOLUTION_LATENCY)
                        .getOrCreateTimer(effectiveTags, latencyOptions, "Address resolution latency", Duration.ofSeconds(6));
                    addressResolutionLatencyMeter.record(latency);
                }

                CosmosMeterOptions reqOptions = clientAccessor.getMeterOptions(
                    client,
                    CosmosMetricName.DIRECT_ADDRESS_RESOLUTION_REQUESTS);
                if (reqOptions.isEnabled() &&
                    (!reqOptions.isDiagnosticThresholdsFilteringEnabled() || ctx.isThresholdViolated())) {
                    Tags effectiveTags = getEffectiveTags(addressResolutionTags, reqOptions);
                    Counter requestCounter = getMeterCache(CosmosMetricName.DIRECT_ADDRESS_RESOLUTION_REQUESTS)
                        .getOrCreateCounter(effectiveTags, reqOptions, "requests", "Address resolution requests");
                    requestCounter.increment();
                }
            }
        }
    }

    private static class RntbdMetricsV2 implements RntbdMetricsCompletionRecorder {
        private final RntbdTransportClient client;
        private final Tags tags;
        private final MeterRegistry registry;

        private final Timer requestLatencyTimer;
        private final Timer requestLatencyFailedTimer;
        private final Timer requestLatencySuccessTimer;
        private final DistributionSummary requestSizeSummary;
        private final DistributionSummary responseSizeSummary;

        private RntbdMetricsV2(MeterRegistry registry, RntbdTransportClient client, RntbdEndpoint endpoint) {
            this.tags = Tags.of(endpoint.clientMetricTag());
            this.client = client;
            this.registry = registry;

            Timer tmpRequestLatency = null;
            Timer tmpRequestLatencyFailed = null;
            Timer tmpRequestLatencySuccess = null;
            DistributionSummary tmpRequestSize = null;
            DistributionSummary tmpResponseSize = null;

            if (this.client.getMetricCategories().contains(MetricCategory.DirectRequests)) {

                CosmosMeterOptions options = client
                    .getMeterOptions(CosmosMetricName.DIRECT_REQUEST_CONCURRENT_COUNT);
                if (options.isEnabled()) {
                    Gauge.builder(options.getMeterName().toString(), endpoint, RntbdEndpoint::concurrentRequests)
                         .description("RNTBD concurrent requests (executing or queued request count)")
                         .tags(getEffectiveTags(tags, options))
                         .register(registry);
                }

                options = client
                    .getMeterOptions(CosmosMetricName.DIRECT_REQUEST_QUEUED_COUNT);
                if (options.isEnabled()) {
                    Gauge.builder(options.getMeterName().toString(), endpoint, RntbdEndpoint::requestQueueLength)
                         .description("RNTBD queued request count")
                         .tags(getEffectiveTags(tags, options))
                         .register(registry);
                }

                options = client
                    .getMeterOptions(CosmosMetricName.DIRECT_REQUEST_LATENCY);
                if (options.isEnabled()) {
                    tmpRequestLatency = Timer
                        .builder(options.getMeterName().toString())
                        .description("RNTBD request latency")
                        .maximumExpectedValue(Duration.ofSeconds(300))
                        .publishPercentiles(options.getPercentiles())
                        .publishPercentileHistogram(options.isHistogramPublishingEnabled())
                        .tags(getEffectiveTags(this.tags, options))
                        .register(this.registry);
                }

                options = client
                    .getMeterOptions(CosmosMetricName.DIRECT_REQUEST_LATENCY_FAILED);
                if (options.isEnabled()) {
                    tmpRequestLatencyFailed = Timer
                        .builder(options.getMeterName().toString())
                        .description("RNTBD failed request latency")
                        .maximumExpectedValue(Duration.ofSeconds(300))
                        .publishPercentiles(options.getPercentiles())
                        .publishPercentileHistogram(options.isHistogramPublishingEnabled())
                        .tags(getEffectiveTags(tags, options))
                        .register(registry);
                }

                options = client
                    .getMeterOptions(CosmosMetricName.DIRECT_REQUEST_LATENCY_SUCCESS);
                if (options.isEnabled()) {
                    tmpRequestLatencySuccess = Timer
                        .builder(options.getMeterName().toString())
                        .description("RNTBD successful request latency")
                        .maximumExpectedValue(Duration.ofSeconds(300))
                        .publishPercentiles(options.getPercentiles())
                        .publishPercentileHistogram(options.isHistogramPublishingEnabled())
                        .tags(getEffectiveTags(tags, options))
                        .register(registry);
                }

                options = client
                    .getMeterOptions(CosmosMetricName.DIRECT_REQUEST_SIZE_REQUEST);
                if (options.isEnabled()) {
                    tmpRequestSize = DistributionSummary.builder(options.getMeterName().toString())
                                         .description("RNTBD request size (bytes)")
                                         .baseUnit("bytes")
                                         .tags(getEffectiveTags(tags, options))
                                         .maximumExpectedValue(16_000_000d)
                                         .publishPercentileHistogram(false)
                                         .publishPercentiles()
                                         .register(registry);
                }

                options = client
                    .getMeterOptions(CosmosMetricName.DIRECT_REQUEST_SIZE_RESPONSE);
                if (options.isEnabled()) {
                    tmpResponseSize = DistributionSummary.builder(options.getMeterName().toString())
                                          .description("RNTBD response size (bytes)")
                                          .baseUnit("bytes")
                                          .tags(getEffectiveTags(tags, options))
                                          .maximumExpectedValue(16_000_000d)
                                          .publishPercentileHistogram(false)
                                          .publishPercentiles()
                                          .register(registry);
                }
            }

            this.requestLatencyTimer = tmpRequestLatency;
            this.requestLatencyFailedTimer = tmpRequestLatencyFailed;
            this.requestLatencySuccessTimer = tmpRequestLatencySuccess;
            this.requestSizeSummary = tmpRequestSize;
            this.responseSizeSummary = tmpResponseSize;

            if (this.client.getMetricCategories().contains(MetricCategory.DirectEndpoints)) {
                CosmosMeterOptions options = client
                    .getMeterOptions(CosmosMetricName.DIRECT_ENDPOINTS_COUNT);
                if (options.isEnabled()) {
                    Gauge.builder(options.getMeterName().toString(), client, RntbdTransportClient::endpointCount)
                         .description("RNTBD endpoint count")
                         .register(registry);
                }

                options = client
                    .getMeterOptions(CosmosMetricName.DIRECT_ENDPOINTS_EVICTED);
                if (options.isEnabled()) {
                    FunctionCounter.builder(
                        options.getMeterName().toString(),
                        client,
                        RntbdTransportClient::endpointEvictionCount)
                                   .description("RNTBD endpoint eviction count")
                                   .register(registry);
                }
            }

            if (this.client.getMetricCategories().contains(MetricCategory.DirectChannels)) {
                CosmosMeterOptions options = client
                    .getMeterOptions(CosmosMetricName.DIRECT_CHANNELS_ACQUIRED_COUNT);
                if (options.isEnabled()) {
                    FunctionCounter.builder(
                        options.getMeterName().toString(),
                        endpoint.durableEndpointMetrics(),
                        RntbdDurableEndpointMetrics::totalChannelsAcquiredMetric)
                                   .description("RNTBD acquired channel count")
                                   .tags(getEffectiveTags(tags, options))
                                   .register(registry);
                }

                options = client
                    .getMeterOptions(CosmosMetricName.DIRECT_CHANNELS_CLOSED_COUNT);
                if (options.isEnabled()) {
                    FunctionCounter.builder(
                        options.getMeterName().toString(),
                        endpoint.durableEndpointMetrics(),
                        RntbdDurableEndpointMetrics::totalChannelsClosedMetric)
                                   .description("RNTBD closed channel count")
                                   .tags(getEffectiveTags(tags, options))
                                   .register(registry);
                }

                options = client
                    .getMeterOptions(CosmosMetricName.DIRECT_CHANNELS_AVAILABLE_COUNT);
                if (options.isEnabled()) {
                    Gauge.builder(
                        options.getMeterName().toString(),
                        endpoint.durableEndpointMetrics(),
                        RntbdDurableEndpointMetrics::channelsAvailableMetric)
                         .description("RNTBD available channel count")
                         .tags(getEffectiveTags(tags, options))
                         .register(registry);
                }
            }
        }

        public void markComplete(RntbdRequestRecord requestRecord) {
            if (this.client.getMetricCategories().contains(MetricCategory.DirectRequests)) {

                requestRecord.stop(
                    this.requestLatencyTimer,
                    requestRecord.isCompletedExceptionally() ? this.requestLatencyFailedTimer : this.requestLatencySuccessTimer);

                if (this.requestSizeSummary != null) {
                    this.requestSizeSummary.record(requestRecord.requestLength());
                }

                if (this.responseSizeSummary != null) {
                    this.responseSizeSummary.record(requestRecord.responseLength());
                }
            } else {
                requestRecord.stop();
            }
        }
    }

    static class DescendantValidationResult {
        private final Instant expiration;
        private final boolean result;

        public DescendantValidationResult(Instant expiration, boolean result) {
            this.expiration = expiration;
            this.result = result;
        }

        public Instant getExpiration() {
            return this.expiration;
        }

        public boolean getResult() {
            return this.result;
        }
    }
}
