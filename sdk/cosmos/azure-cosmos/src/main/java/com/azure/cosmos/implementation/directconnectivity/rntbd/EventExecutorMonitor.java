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
   private static final ConcurrentHashMap<Integer, AtomicInteger> eventExecutorChannelMonitor = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, String> eventExecutorThreadMonitor = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<ChannelId, List<String>>> eventExecutorLatency = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<ChannelId, AtomicInteger>> expire = new ConcurrentHashMap<>();
    private static final ObjectMapper objectmapper = new ObjectMapper();
    private static final int warmup = 0;
    private static final AtomicInteger totalRequests = new AtomicInteger(0);
    private static final int expected = 500;
    private static final AtomicInteger succeeded = new AtomicInteger(0);
    private static final AtomicReference<Long> totalLatency = new AtomicReference<>(0L);

    public static void registerChannel(EventExecutor executor, String threadName) {
       eventExecutorChannelMonitor.compute(System.identityHashCode(executor), (id, count) -> {
           if (count == null) {
               count = new AtomicInteger(0);
           }
           count.incrementAndGet();
           return count;
       });

        eventExecutorThreadMonitor.put(System.identityHashCode(executor), threadName);
    }

    public static void trackLatency(int statusCode, long latency, EventExecutor eventExecutor, ChannelId channelId) {
        int requestIndex = totalRequests.incrementAndGet();
        if (requestIndex <= warmup) {
            return;
        }

        AtomicReference<String> trackingText = new AtomicReference<>("");
        if (statusCode == HttpConstants.StatusCodes.OK) {
            succeeded.incrementAndGet();
            totalLatency.accumulateAndGet(latency, (currentLatency, newLatency) -> currentLatency + newLatency);
        }

        trackingText.set(statusCode + "|" + latency);
        eventExecutorLatency.compute(System.identityHashCode(eventExecutor), (id, latencyMap) -> {
            if (latencyMap == null) {
                latencyMap = new ConcurrentHashMap<>();
            }

            latencyMap.compute(channelId, (channelId2, latencyList) -> {
                if (latencyList == null) {
                    latencyList = new ArrayList<>();
                }

                latencyList.add(trackingText.get());
                return latencyList;
            });

            return latencyMap;
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
            Map<String, Map<ChannelId, List<String>>> latencyWithThreadName = new ConcurrentHashMap<>();
            if (eventExecutorLatency.size() != eventExecutorThreadMonitor.size()) {
                logger.warn("can not match with thread name");
                logger.info("eventExecutorChannelMonitor:" + objectmapper.writeValueAsString(eventExecutorChannelMonitor));
                return;
            }

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

            for(int executorId : Collections.list(eventExecutorLatency.keys())) {
                latencyWithThreadName.put(eventExecutorThreadMonitor.get(executorId), eventExecutorLatency.get(executorId));
            }


            logger.info("There are "  + eventExecutorLatency.size() + " executor assigned with channel");

            AtomicInteger totalChannel = new AtomicInteger(0);
            eventExecutorChannelMonitor.values()
                .forEach(channelCnt -> totalChannel.accumulateAndGet(channelCnt.get(), (old, mewValue) -> old + mewValue));

            logger.info("eventExecutorChannelMonitor:" + totalChannel.get() + "|" + objectmapper.writeValueAsString(eventExecutorChannelMonitor));
            logger.info("eventExecutorLatency:" + objectmapper.writeValueAsString(latencyWithThreadName));
            logger.info("Latency: " + totalLatency.get() / succeeded.get() + "|" + succeeded.get());


        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}
