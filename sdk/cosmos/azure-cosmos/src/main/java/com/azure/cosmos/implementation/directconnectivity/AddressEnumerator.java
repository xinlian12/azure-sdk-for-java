// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.directconnectivity;

import com.azure.cosmos.implementation.apachecommons.lang.tuple.Pair;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.azure.cosmos.implementation.guava25.base.Preconditions.checkNotNull;

public class AddressEnumerator {
    // For an address, it typically has three health status: healthy, pending, and Unhealthy
    // the risk for us to always prioritize the healthy replica may cause RU skewness
    // so we are going to always allow pending to be treated as equal as healthy
    private final Map<String, Pair<AtomicInteger, Boolean>> serverPartitionAddressCache;

    public AddressEnumerator() {
        this.serverPartitionAddressCache = new ConcurrentHashMap<>();
    }


    public List<Uri> getAddresses(
            String pkRangeId,
            List<Uri> addresses,
            List<Uri> failedEndpoints) {

        checkNotNull(addresses, "Argument 'addresses' should not be null");

        List<Uri> randomPermutation = this.getAddressesInternal(addresses);

        // Prioritize to use healthy endpoints
        // every 100, switch the health status priority
        Pair<AtomicInteger, Boolean> shouldOnlyUseHealthyReplicaPair = serverPartitionAddressCache.compute(pkRangeId, (key, value) -> {
            if (value == null) {
                value = Pair.of(new AtomicInteger(0), true);
            }

            int snapshot = value.getLeft().incrementAndGet();
            if (snapshot == 1000) {
                value = Pair.of(new AtomicInteger(0), !value.getRight());
            }

            return value;
        });

        if (shouldOnlyUseHealthyReplicaPair.getRight()) {
            randomPermutation.sort(new Comparator<Uri>() {
                @Override
                public int compare(Uri o1, Uri o2) {
                    return o1.getHealthStatus().getValue() - o2.getHealthStatus().getValue();
                }
            });
        } else {
            randomPermutation.sort(new Comparator<Uri>() {
                @Override
                public int compare(Uri o1, Uri o2) {
                    if (o1.getHealthStatus() == o2.getHealthStatus()) {
                        return 0;
                    }
                    if (o1.getHealthStatus() == Uri.HealthStatus.Healthy ||
                            o1.getHealthStatus() == Uri.HealthStatus.Unknown) {
                        return -1;
                    }

                    return 1;
                }
            });
        }

        return randomPermutation;
    }

    private List<Uri> getAddressesInternal(List<Uri> addresses) {
        checkNotNull(addresses, "Argument 'addresses' should not be null");

        if (AddressEnumeratorUsingPermutations.isSizeInPermutationLimits(addresses.size())) {
            return AddressEnumeratorUsingPermutations.getAddressesWithPredefinedPermutation(addresses);
        }

        return AddressEnumeratorFisherYateShuffle.getTransportAddressUrisWithFisherYateShuffle(addresses);
    }
}
