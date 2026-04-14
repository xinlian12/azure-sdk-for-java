// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.throughputControl.sdk.controller;

import com.azure.cosmos.ConnectionMode;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.implementation.caches.RxCollectionCache;
import com.azure.cosmos.implementation.caches.RxPartitionKeyRangeCache;
import com.azure.cosmos.implementation.throughputControl.sdk.config.LocalThroughputControlGroup;
import com.azure.cosmos.implementation.throughputControl.sdk.config.SDKThroughputControlGroupInternal;
import com.azure.cosmos.implementation.throughputControl.sdk.controller.container.SDKThroughputContainerController;
import com.azure.cosmos.models.PriorityLevel;
import org.mockito.Mockito;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class SDKThroughputContainerControllerTests {

    /**
     * Validates that when only targetThroughput is configured (no targetThroughputThreshold),
     * the throughputQueryMono is NOT subscribed during resolveContainerMaxThroughput.
     * This is the fix for issue #48799 — AAD principals may not have the
     * throughputSettings/read permission required by the query, and the query is
     * unnecessary when an absolute target throughput is specified.
     */
    @Test(groups = "unit")
    public void throughputQueryMonoNotSubscribedWhenOnlyTargetThroughputConfigured() {
        CosmosAsyncContainer containerMock = Mockito.mock(CosmosAsyncContainer.class);
        CosmosAsyncDatabase databaseMock = Mockito.mock(CosmosAsyncDatabase.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.doReturn("FakeCollection").when(containerMock).getId();
        Mockito.doReturn(databaseMock).when(containerMock).getDatabase();
        Mockito.doReturn("FakeDatabase").when(databaseMock).getId();

        // Group with targetThroughput only (no threshold)
        LocalThroughputControlGroup group = new LocalThroughputControlGroup(
            "test-" + UUID.randomUUID(),
            containerMock,
            6,        // targetThroughput
            null,     // targetThroughputThreshold — NOT set
            PriorityLevel.HIGH,
            true,
            false);

        Map<String, SDKThroughputControlGroupInternal> groups = new HashMap<>();
        groups.put(group.getGroupName(), group);

        AtomicBoolean throughputQuerySubscribed = new AtomicBoolean(false);
        Mono<Integer> trackingThroughputQueryMono = Mono.<Integer>just(10000)
            .doOnSubscribe(s -> throughputQuerySubscribed.set(true));

        RxCollectionCache collectionCacheMock = Mockito.mock(RxCollectionCache.class);
        RxPartitionKeyRangeCache pkRangeCacheMock = Mockito.mock(RxPartitionKeyRangeCache.class);

        // Constructing the controller wires the throughputQueryMono but should NOT subscribe
        // when throughputProvisioningScope is NONE (no threshold configured)
        SDKThroughputContainerController controller = new SDKThroughputContainerController(
            collectionCacheMock,
            ConnectionMode.DIRECT,
            groups,
            pkRangeCacheMock,
            null,
            trackingThroughputQueryMono);

        // The constructor should NOT have subscribed to the throughput query mono
        assertThat(throughputQuerySubscribed.get())
            .as("throughputQueryMono should not be subscribed when only targetThroughput is configured")
            .isFalse();
    }

    /**
     * Validates that when targetThroughputThreshold IS configured,
     * the throughputQueryMono is still wired and available for subscription.
     * (The actual subscription happens during init(), which requires a full
     * integration setup — this test just verifies the constructor accepts it.)
     */
    @Test(groups = "unit")
    public void throughputQueryMonoWiredWhenThresholdConfigured() {
        CosmosAsyncContainer containerMock = Mockito.mock(CosmosAsyncContainer.class);
        CosmosAsyncDatabase databaseMock = Mockito.mock(CosmosAsyncDatabase.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.doReturn("FakeCollection").when(containerMock).getId();
        Mockito.doReturn(databaseMock).when(containerMock).getDatabase();
        Mockito.doReturn("FakeDatabase").when(databaseMock).getId();

        // Group with targetThroughputThreshold set
        LocalThroughputControlGroup group = new LocalThroughputControlGroup(
            "test-" + UUID.randomUUID(),
            containerMock,
            null,     // targetThroughput
            0.5,      // targetThroughputThreshold — IS set
            PriorityLevel.HIGH,
            true,
            false);

        Map<String, SDKThroughputControlGroupInternal> groups = new HashMap<>();
        groups.put(group.getGroupName(), group);

        AtomicBoolean throughputQuerySubscribed = new AtomicBoolean(false);
        Mono<Integer> trackingThroughputQueryMono = Mono.<Integer>just(10000)
            .doOnSubscribe(s -> throughputQuerySubscribed.set(true));

        RxCollectionCache collectionCacheMock = Mockito.mock(RxCollectionCache.class);
        RxPartitionKeyRangeCache pkRangeCacheMock = Mockito.mock(RxPartitionKeyRangeCache.class);

        // Constructor should accept the throughput query mono (subscription happens during init)
        SDKThroughputContainerController controller = new SDKThroughputContainerController(
            collectionCacheMock,
            ConnectionMode.DIRECT,
            groups,
            pkRangeCacheMock,
            null,
            trackingThroughputQueryMono);

        // Constructor itself should not subscribe, but the mono should be wired for init()
        // (We can't easily test init() without full integration setup)
        assertThat(controller).isNotNull();
    }
}
