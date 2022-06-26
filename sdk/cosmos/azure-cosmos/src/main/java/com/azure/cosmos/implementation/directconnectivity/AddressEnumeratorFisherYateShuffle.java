// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.directconnectivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.azure.cosmos.implementation.guava25.base.Preconditions.checkNotNull;

public class AddressEnumeratorFisherYateShuffle {

    private static Random random = new Random();
    public static List<Uri> getTransportAddressUrisWithFisherYateShuffle(List<Uri> addresses)
    {
        checkNotNull(addresses, "Argument 'addresses' can not be null");

        // Fisher Yates Shuffle algorithm: https://en.wikipedia.org/wiki/Fisher%E2%80%93Yates_shuffle
        List<Uri> addressesCopy = new ArrayList<>(addresses);

        for (int i = addressesCopy.size(); i > 0; i--) {
            int randomIndex = random.nextInt(i);
            swap(addressesCopy, i - 1, randomIndex);
        }

        return addressesCopy;
    }

    private static void swap(
            List<Uri> addresses,
            int firstIndex,
            int secondIndex)
    {
        if (firstIndex == secondIndex)
        {
            return;
        }

        Uri temp = addresses.get(firstIndex);
        addresses.set(firstIndex, addresses.get(secondIndex));
        addresses.set(secondIndex, temp);
    }
}
