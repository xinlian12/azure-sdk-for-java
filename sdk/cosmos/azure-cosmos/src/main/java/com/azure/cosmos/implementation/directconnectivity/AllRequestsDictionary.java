package com.azure.cosmos.implementation.directconnectivity;

import io.netty.channel.ChannelId;
import io.netty.util.AttributeKey;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AllRequestsDictionary {
    private static final Map<ChannelId, AttributeKey<List<Long>>> allRequests = new ConcurrentHashMap<>();
    private static final AtomicBoolean trackingFlag = new AtomicBoolean(false);
    public static final AttributeKey<Boolean> shouldLog = AttributeKey.newInstance("ShouldLog");

    public static AttributeKey<List<Long>> getAttributeKey(ChannelId channelId) {
        return allRequests.compute(channelId, (id, requests) -> {
            if (requests == null) {
                requests = AttributeKey.newInstance("AllRequests_" + channelId);
            }

            return requests;
        });
    }

    public static boolean shouldLog() {
        return trackingFlag.compareAndSet(false, true);
    }

}
