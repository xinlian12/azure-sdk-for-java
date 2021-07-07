package com.azure.cosmos.implementation.directconnectivity.rntbd;

import com.azure.cosmos.implementation.HttpConstants;
import com.azure.cosmos.implementation.apachecommons.lang.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.util.concurrent.EventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class EventExecutorMonitor {
    private static final Logger logger = LoggerFactory.getLogger(EventExecutorMonitor.class);
   private static final ConcurrentHashMap<Integer, List<ChannelId>> eventExecutorChannelMonitor = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ChannelId, List<String>> eventExecutorLatency = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<ChannelId, AtomicInteger>> expire = new ConcurrentHashMap<>();
    private static final ObjectMapper objectmapper = new ObjectMapper();
    private static final int warmup = 0;
    private static final AtomicInteger totalRequests = new AtomicInteger(0);
    private static final int expected = 500;
    private static final AtomicInteger succeeded = new AtomicInteger(0);
    private static final AtomicReference<Long> totalLatency = new AtomicReference<>(0L);

    public static void registerChannel(EventExecutor executor, ChannelId channelId) {
       eventExecutorChannelMonitor.compute(System.identityHashCode(executor), (id, channelList) -> {
           if (channelList == null) {
               channelList = new ArrayList<>();
           }
           channelList.add(channelId);
           return channelList;
       });
    }

    public static void trackLatency(int statusCode, long transitTime, long decodeTime, long receiveTime, long aggregratedLatency, ChannelId channelId) {
        int requestIndex = totalRequests.incrementAndGet();
        if (requestIndex <= warmup) {
            return;
        }

        String trackingText = String.format("%s: %s|%s|%s|%s", statusCode, transitTime, decodeTime, receiveTime, aggregratedLatency);
        if (statusCode == HttpConstants.StatusCodes.OK) {
            succeeded.incrementAndGet();
            totalLatency.accumulateAndGet(aggregratedLatency, (currentLatency, newLatency) -> currentLatency + newLatency);
        }

        eventExecutorLatency.compute(channelId, (id, latencyList) -> {
            if (latencyList == null) {
                latencyList = new ArrayList<>();
            }

            latencyList.add(trackingText);

            return latencyList;
        });

        if (requestIndex == expected + warmup) {
            log();
        }
    }

    public static void trackExpired(Channel channel) {
        int requestIndex = totalRequests.incrementAndGet();
        if (requestIndex <= warmup) {
            return;
        }
        expire.compute(System.identityHashCode(channel.eventLoop()), (id, expireMap) -> {
            if (expireMap == null) {
                expireMap = new ConcurrentHashMap<>();
            }

            expireMap.compute(channel.id(), ((channelId, atomicInteger) -> {
                if (atomicInteger == null) {
                    atomicInteger = new AtomicInteger(0);
                }
                atomicInteger.incrementAndGet();
                return atomicInteger;
            }));
            return expireMap;
        });

        if (requestIndex == expected + warmup) {
            log();
        }
    }

    public static void log() {
        try {
            // adding expire map
//            for(Integer executorId : Collections.list(expire.keys())) {
//                Map<ChannelId, List<String>> channellatency = eventExecutorLatency.get(executorId);
//                ConcurrentHashMap<ChannelId, AtomicInteger> channelExpire = expire.get(executorId);
//                if (channellatency == null) {
//                    logger.warn("Can not find the mapping channel for expire requests");
//                } else{
//                    for(ChannelId channelId : Collections.list(channelExpire.keys())) {
//                        channellatency.compute(channelId, (id, latencyList) -> {
//                            if (latencyList == null) {
//                                latencyList = new ArrayList<>();
//                            }
//
//                            latencyList.add("EXPIRE|" + channelExpire.get(channelId));
//
//                            return latencyList;
//                        });
//                    }
//                }
//            }

            logger.info("There are "  + eventExecutorLatency.size() + " executor assigned with channel");

            ConcurrentHashMap<Integer, ConcurrentHashMap<ChannelId, List<String>>> aggregrated = new ConcurrentHashMap<>();
            for (Integer executorId : Collections.list(eventExecutorChannelMonitor.keys())) {
                ConcurrentHashMap<ChannelId, List<String>> latencyMap = aggregrated.compute(executorId, (id, channellatencymap) -> {
                    if (channellatencymap == null) {
                        channellatencymap = new ConcurrentHashMap<>();
                    }

                    return channellatencymap;
                });

                for(ChannelId channelId : eventExecutorChannelMonitor.get(executorId)) {
                    latencyMap.compute(channelId, (id, latencyList) -> {
                        if (latencyList == null) {
                            latencyList = new ArrayList<>();
                        }

                        latencyList.addAll(eventExecutorLatency.get(channelId));

                        return latencyList;
                    });
                }
            }
            logger.info("eventExecutorLatency:" + objectmapper.writeValueAsString(aggregrated));
            logger.info("Latency: " + totalLatency.get() / succeeded.get() + "|" + succeeded.get());


        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}
