// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.http;

import io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameReader;
import io.netty.handler.codec.http2.Http2InboundFrameLogger;
import io.netty.handler.codec.http2.Http2Settings;
import reactor.netty.http.Http2SettingsSpec;

/**
 * Uses Netty's standard encoder construction and decoder safeguards with a targeted headers decoder.
 */
final class CosmosHttp2FrameCodecBuilder extends Http2FrameCodecBuilder {
    private final Http2SettingsSpec settingsSpec;

    CosmosHttp2FrameCodecBuilder(Http2SettingsSpec settingsSpec) {
        this.settingsSpec = settingsSpec;
        server(false);
        gracefulShutdownTimeoutMillis(0);
        applyResetFrameLimits(this, settingsSpec);
    }

    @Override
    protected Http2FrameCodec build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                   Http2Settings settings) {
        // Keep Netty's already-decorated encoder: constructing a bare encoder here would discard
        // its queued-control-frame and configured RST-frame flood protections.
        decoder.close();
        Http2FrameReader reader = new DefaultHttp2FrameReader(
            new CosmosHttp2HeadersDecoder(settings.maxHeaderListSize()),
            decoderEnforceMaxSmallContinuationFrames());
        if (frameLogger() != null) {
            reader = new Http2InboundFrameLogger(reader, frameLogger());
        }
        try {
            Http2ConnectionDecoder replacement = new DefaultHttp2ConnectionDecoder(
                encoder.connection(), encoder, reader, promisedRequestVerifier(),
                isAutoAckSettingsFrame(), isAutoAckPingFrame(), true, isValidateRequiredPseudoHeaders());

            DecoderCodecBuilder builder = new DecoderCodecBuilder(
                replacement, encoder, decoderEnforceMaxConsecutiveEmptyDataFrames(), settingsSpec);
            builder.initialSettings(settings)
                .gracefulShutdownTimeoutMillis(gracefulShutdownTimeoutMillis())
                .decoupleCloseAndGoAway(decoupleCloseAndGoAway())
                .flushPreface(flushPreface());
            return builder.build();
        } catch (RuntimeException | Error failure) {
            reader.close();
            throw failure;
        }
    }

    private static void applyResetFrameLimits(Http2FrameCodecBuilder builder, Http2SettingsSpec spec) {
        if (spec.maxDecodedRstFramesPerWindow() != null && spec.maxDecodedRstFramesSecondsPerWindow() != null) {
            builder.decoderEnforceMaxRstFramesPerWindow(
                spec.maxDecodedRstFramesPerWindow(), spec.maxDecodedRstFramesSecondsPerWindow());
        }
        if (spec.maxEncodedRstFramesPerWindow() != null && spec.maxEncodedRstFramesSecondsPerWindow() != null) {
            builder.encoderEnforceMaxRstFramesPerWindow(
                spec.maxEncodedRstFramesPerWindow(), spec.maxEncodedRstFramesSecondsPerWindow());
        }
    }

    private static final class DecoderCodecBuilder extends Http2FrameCodecBuilder {
        private DecoderCodecBuilder(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                    int maxConsecutiveEmptyDataFrames, Http2SettingsSpec settingsSpec) {
            decoderEnforceMaxConsecutiveEmptyDataFrames(maxConsecutiveEmptyDataFrames);
            applyResetFrameLimits(this, settingsSpec);
            codec(decoder, encoder);
        }

        @Override
        public boolean isServer() {
            return false;
        }
    }
}
