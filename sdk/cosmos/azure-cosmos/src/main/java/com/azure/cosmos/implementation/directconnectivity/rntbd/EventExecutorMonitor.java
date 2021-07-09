package com.azure.cosmos.implementation.directconnectivity.rntbd;

import com.azure.cosmos.implementation.HttpConstants;
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

    private static final AtomicLong totalCreated = new AtomicLong(0);
    private static final AtomicLong totalQueued = new AtomicLong(0);
    private static final AtomicLong totalChannelAcquisitionStarted = new AtomicLong(0);
    private static final AtomicLong totalPipelined = new AtomicLong(0);
    private static final AtomicLong totalTransitTime = new AtomicLong(0);
    private static final AtomicLong totalReceived = new AtomicLong(0);
    private static final AtomicLong totalDecode = new AtomicLong(0);
    private static final AtomicLong totalCompleted = new AtomicLong(0);
    private static final AtomicInteger totalRecords = new AtomicInteger(0);

    public static void registerChannel(EventExecutor executor, ChannelId channelId) {
       eventExecutorChannelMonitor.compute(System.identityHashCode(executor), (id, channelList) -> {
           if (channelList == null) {
               channelList = new ArrayList<>();
           }
           channelList.add(channelId);
           return channelList;
       });
    }

    public static void trackLatency(
        int statusCode,
        long transitTime,
        long decodeTime,
        long receiveTime,
        long aggregratedLatency,
        ChannelId channelId,
        int pendingRequests,
        long parsingTime,
        long timePipelined,
        long timeOnContext) {
        int requestIndex = totalRequests.incrementAndGet();
        if (requestIndex <= warmup) {
            return;
        }

        String trackingText = String.format("%s: context:%s|pipelined:%s|Transit:%s|Decode:%s|Receive:%s|parsing:%s|total:%s|pending:%s", statusCode, timeOnContext, timePipelined, transitTime, decodeTime, receiveTime, parsingTime, aggregratedLatency,pendingRequests);
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
            logRecords();
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
            logRecords();
        }
    }

    public static void trackRecord(RntbdRequestRecord record) {
        long created = record.timeQueued().toEpochMilli() - record.timeCreated().toEpochMilli();
        long queued = record.timeChannelAcquisitionStarted().toEpochMilli() - record.timeQueued().toEpochMilli();
        long channelAcquisition = record.timePipelined().toEpochMilli() - record.timeChannelAcquisitionStarted().toEpochMilli();
        long pipelined = record.timeSent().toEpochMilli() - record.timePipelined().toEpochMilli();
        long transit = record.timeDecodeStarted().toEpochMilli() - record.timeSent().toEpochMilli();
        long decode = record.timeReceived().toEpochMilli() - record.timeDecodeStarted().toEpochMilli();
        long receive = record.timeCompleted().toEpochMilli() - record.timeReceived().toEpochMilli();

        totalRecords.incrementAndGet();
        totalCreated.accumulateAndGet(created, (newValue, oldValue) -> newValue + oldValue);
        totalQueued.accumulateAndGet(queued, (newValue, oldValue) -> newValue + oldValue);
        totalChannelAcquisitionStarted.accumulateAndGet(channelAcquisition, (newValue, oldValue) -> newValue + oldValue);
        totalPipelined.accumulateAndGet(pipelined, (newValue, oldValue) -> newValue + oldValue);
        totalTransitTime.accumulateAndGet(transit, (newValue, oldValue) -> newValue + oldValue);
        totalDecode.accumulateAndGet(decode, (newValue, oldValue) -> newValue + oldValue);
        totalReceived.accumulateAndGet(receive, (newValue, oldValue) -> newValue + oldValue);
    }

    private static void logRecords() {
        logger.info(
            "Avg latency for {} requests: [created: {}, queued, {}, channelAcquisition: {}, pipelined: {}, transitTime: {}, decode: {}, received: {}]",
            totalRecords.get(),
            totalCreated.get()/totalRecords.get(),
            totalQueued.get()/totalRecords.get(),
            totalChannelAcquisitionStarted.get()/totalRecords.get(),
            totalPipelined.get()/totalRecords.get(),
            totalTransitTime.get()/totalRecords.get(),
            totalDecode.get()/totalRecords.get(),
            totalReceived.get()/totalRecords.get());
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

            logger.info("There are "  + eventExecutorChannelMonitor.size() + " executor assigned with channel");

            ConcurrentHashMap<Integer, ConcurrentHashMap<ChannelId, List<String>>> aggregrated = new ConcurrentHashMap<>();
            for (Integer executorId : Collections.list(eventExecutorChannelMonitor.keys())) {
                ConcurrentHashMap<ChannelId, List<String>> latencyMap = aggregrated.compute(executorId, (id, channellatencymap) -> {
                    if (channellatencymap == null) {
                        channellatencymap = new ConcurrentHashMap<>();
                    }

                    return channellatencymap;
                });

                for(ChannelId channelId : eventExecutorChannelMonitor.get(executorId)) {
                    if (eventExecutorLatency.containsKey(channelId)) {
                        latencyMap.compute(channelId, (id, latencyList) -> {
                            if (latencyList == null) {
                                latencyList = new ArrayList<>();
                            }

                            latencyList.addAll(eventExecutorLatency.get(channelId));

                            return latencyList;
                        });
                    }
                }
            }
            logger.info("eventExecutorLatency:" + objectmapper.writeValueAsString(aggregrated));
            logger.info("Latency: " + totalLatency.get() / succeeded.get() + "|" + succeeded.get());


        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}
