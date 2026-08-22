// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.http;

import com.azure.cosmos.implementation.HttpConstants;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2SettingsAckFrame;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Http2ResponseHeaderCleanerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(Http2ResponseHeaderCleanerHandler.class);
    private static final AsciiString SERVER_VERSION_KEY = AsciiString.of(HttpConstants.HttpHeaders.SERVER_VERSION);

    // HPACK analysis: log summary every N responses per connection
    private static final int HPACK_LOG_INTERVAL = 1000;
    // Cap distinct value tracking per header name to bound memory
    private static final int MAX_DISTINCT_VALUES_PER_HEADER = 50;
    // HPACK dynamic table entry overhead per RFC 7541 Section 4.1
    private static final int HPACK_ENTRY_OVERHEAD_BYTES = 32;

    // Server SETTINGS (populated when SETTINGS frame arrives)
    private Long serverHeaderTableSize;
    private Long serverMaxConcurrentStreams;
    private Long serverMaxHeaderListSize;
    private Integer serverInitialWindowSize;
    private Integer serverMaxFrameSize;

    // Per-connection header statistics.
    // Accessed from a single Netty event-loop thread per connection, so no synchronization is needed.
    private long responseCount;
    private long totalHeaderCount;
    private long totalHeaderBytes;
    private final Map<String, HeaderNameStats> headerAnalysis = new HashMap<>();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http2HeadersFrame) {
            Http2HeadersFrame headersFrame = (Http2HeadersFrame) msg;
            Http2Headers headers = headersFrame.headers();

            // Direct O(1) hash lookup instead of O(n) forEach iteration over all headers
            CharSequence serverVersion = headers.get(SERVER_VERSION_KEY);
            if (serverVersion != null && serverVersion.length() > 0
                && (serverVersion.charAt(0) == ' ' || serverVersion.charAt(serverVersion.length() - 1) == ' ')) {
                logger.trace("There is extra whitespace for key {} with value {}", SERVER_VERSION_KEY, serverVersion);
                headers.set(SERVER_VERSION_KEY, serverVersion.toString().trim());
            }

            // HPACK analysis: collect header statistics when debug logging is enabled
            if (logger.isInfoEnabled()) {
                collectHeaderStats(headers, ctx);
            }

            super.channelRead(ctx, msg);
        } else if (msg instanceof Http2SettingsAckFrame) {
            ReferenceCountUtil.release(msg);
        } else if (msg instanceof Http2SettingsFrame) {
            Http2SettingsFrame settingsFrame = (Http2SettingsFrame) msg;
            logServerSettings(settingsFrame, ctx);
            super.channelRead(ctx, msg);
        } else {
            // Pass the message to the next handler in the pipeline
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Log final HPACK analysis when the connection closes
        if (logger.isInfoEnabled() && responseCount > 0) {
            logHpackAnalysis(ctx, "connection_close");
        }
        super.channelInactive(ctx);
    }

    private void logServerSettings(Http2SettingsFrame settingsFrame, ChannelHandlerContext ctx) {
        serverHeaderTableSize = settingsFrame.settings().headerTableSize();
        serverMaxConcurrentStreams = settingsFrame.settings().maxConcurrentStreams();
        serverMaxHeaderListSize = settingsFrame.settings().maxHeaderListSize();
        serverInitialWindowSize = settingsFrame.settings().initialWindowSize();
        serverMaxFrameSize = settingsFrame.settings().maxFrameSize();

        logger.info(
            "HTTP/2 SETTINGS received [channel={}]: headerTableSize={}, maxConcurrentStreams={}, "
                + "maxHeaderListSize={}, initialWindowSize={}, maxFrameSize={}",
            ctx.channel().id(),
            serverHeaderTableSize,
            serverMaxConcurrentStreams,
            serverMaxHeaderListSize,
            serverInitialWindowSize,
            serverMaxFrameSize);
    }

    private void collectHeaderStats(Http2Headers headers, ChannelHandlerContext ctx) {
        responseCount++;
        int count = 0;

        for (Map.Entry<CharSequence, CharSequence> entry : headers) {
            count++;
            String name = entry.getKey().toString();
            CharSequence value = entry.getValue();
            int nameLen = name.length();
            int valueLen = value.length();

            // HPACK entry size per RFC 7541 Section 4.1: name octets + value octets + 32
            totalHeaderBytes += nameLen + valueLen + HPACK_ENTRY_OVERHEAD_BYTES;

            headerAnalysis.computeIfAbsent(name, k -> new HeaderNameStats()).record(value, nameLen);
        }

        totalHeaderCount += count;

        if (responseCount % HPACK_LOG_INTERVAL == 0) {
            logHpackAnalysis(ctx, "periodic");
        }
    }

    private void logHpackAnalysis(ChannelHandlerContext ctx, String trigger) {
        if (headerAnalysis.isEmpty()) {
            return;
        }

        double avgHeadersPerResponse = responseCount > 0 ? (double) totalHeaderCount / responseCount : 0;

        StringBuilder sb = new StringBuilder(2048);
        sb.append("HPACK table analysis [trigger=").append(trigger)
            .append(", channel=").append(ctx.channel().id())
            .append(", responses=").append(responseCount)
            .append(", avgHeadersPerResponse=").append(String.format("%.1f", avgHeadersPerResponse))
            .append(", totalHeaderBytes=").append(totalHeaderBytes)
            .append(", serverHeaderTableSize=").append(serverHeaderTableSize)
            .append("]\n");

        // INDEX candidates: stable name+value pairs that benefit from dynamic table indexing
        sb.append("  INDEX candidates (stable name+value pairs, reuseRatio >= 2.0):\n");
        headerAnalysis.entrySet().stream()
            .filter(e -> e.getValue().getReuseRatio() >= 2.0 && !e.getValue().distinctValuesCapped)
            .sorted(Comparator.comparingDouble(
                (Map.Entry<String, HeaderNameStats> e) -> e.getValue().getReuseRatio()).reversed())
            .forEach(e -> appendHeaderLine(sb, e.getKey(), e.getValue()));

        // NEVER_INDEX candidates: high-cardinality or low-reuse headers that churn the table
        sb.append("  NEVER_INDEX candidates (high churn or high cardinality):\n");
        headerAnalysis.entrySet().stream()
            .filter(e -> e.getValue().getReuseRatio() < 2.0 || e.getValue().distinctValuesCapped)
            .sorted(Comparator.comparingLong(
                (Map.Entry<String, HeaderNameStats> e) -> e.getValue().occurrences).reversed())
            .forEach(e -> appendHeaderLine(sb, e.getKey(), e.getValue()));

        // Table sizing guidance: estimate ideal table size for indexable headers
        long idealTableSize = 0;
        long estimatedSavings = 0;
        for (Map.Entry<String, HeaderNameStats> e : headerAnalysis.entrySet()) {
            HeaderNameStats stats = e.getValue();
            if (stats.getReuseRatio() >= 2.0 && !stats.distinctValuesCapped) {
                int nameLen = e.getKey().length();
                double avgValSize = stats.avgValueSize();
                // Each distinct value occupies one table slot: nameLen + avgValueSize + 32
                idealTableSize += (long) stats.distinctValueCount()
                    * (nameLen + avgValSize + HPACK_ENTRY_OVERHEAD_BYTES);
                // Savings: subsequent references use ~1-2 bytes instead of full literal encoding
                estimatedSavings += (stats.occurrences - stats.distinctValueCount())
                    * (long) (nameLen + avgValSize);
            }
        }

        sb.append("  Estimated ideal dynamic table size for indexable headers: ")
            .append(idealTableSize).append(" bytes\n");
        sb.append("  Estimated compression savings from dynamic table: ~")
            .append(estimatedSavings / 1024).append("KB over ").append(responseCount).append(" responses");

        logger.info(sb.toString());
    }

    private void appendHeaderLine(StringBuilder sb, String name, HeaderNameStats stats) {
        sb.append("    ").append(name)
            .append(" | occurrences=").append(stats.occurrences)
            .append(", distinctValues=").append(stats.distinctValueCount())
            .append(stats.distinctValuesCapped ? "+" : "")
            .append(", reuseRatio=").append(String.format("%.2f", stats.getReuseRatio()))
            .append(", avgValueSize=").append(String.format("%.0fB", stats.avgValueSize()))
            .append(", tableEntrySize=")
            .append(name.length() + (long) stats.avgValueSize() + HPACK_ENTRY_OVERHEAD_BYTES).append("B")
            .append("\n");
    }

    /**
     * Tracks statistics for a single response header name within a connection's lifetime.
     * Accessed from a single Netty event-loop thread, so no synchronization is needed.
     */
    static class HeaderNameStats {
        long occurrences;
        long totalValueBytes;
        int nameLength;
        final Set<String> distinctValues = new HashSet<>();
        boolean distinctValuesCapped;

        void record(CharSequence value, int nameLen) {
            occurrences++;
            totalValueBytes += value.length();
            nameLength = nameLen;

            if (!distinctValuesCapped) {
                if (distinctValues.size() < MAX_DISTINCT_VALUES_PER_HEADER) {
                    distinctValues.add(value.toString());
                } else {
                    distinctValuesCapped = true;
                }
            }
        }

        int distinctValueCount() {
            return distinctValues.size();
        }

        double avgValueSize() {
            return occurrences > 0 ? (double) totalValueBytes / occurrences : 0;
        }

        /**
         * Returns occurrences / distinctValues.
         * High ratio (>= 2.0) means values repeat across responses — good HPACK dynamic table candidate.
         * Ratio near 1.0 means values are unique per response — poor candidate, causes table churn.
         */
        double getReuseRatio() {
            int distinct = distinctValueCount();
            return distinct > 0 ? (double) occurrences / distinct : 0;
        }
    }
}
