// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.http;

import com.azure.cosmos.implementation.HttpConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
import io.netty.handler.codec.http2.DefaultHttp2HeadersEncoder;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.ReferenceCountUtil;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import reactor.netty.http.Http2SettingsSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CosmosHttp2HeadersDecoderTest {
    private static final String SERVICE_VERSION = HttpConstants.HttpHeaders.SERVER_VERSION;

    @DataProvider(name = "serviceVersions")
    public Object[][] serviceVersions() {
        return new Object[][] {
            { "version", "version" },
            { " version ", "version" },
            { "\tversion\t", "version" },
            { " \tversion with spaces\t ", "version with spaces" },
            { "", "" },
            { " \t ", "" }
        };
    }

    @Test(groups = "unit", dataProvider = "serviceVersions")
    public void normalizesOnlyBoundaryOptionalWhitespace(String value, String expected) throws Exception {
        ByteBuf block = encode(new DefaultHttp2HeadersEncoder(), 1, SERVICE_VERSION, value);
        try {
            Http2Headers headers = new CosmosHttp2HeadersDecoder(8192).decodeHeaders(1, block);
            assertThat(headers.get(SERVICE_VERSION).toString()).isEqualTo(expected);
        } finally {
            block.release();
        }
    }

    @Test(groups = "unit")
    public void stockDecoderRejectsPaddedServiceVersion() throws Exception {
        ByteBuf block = encode(new DefaultHttp2HeadersEncoder(), 1, SERVICE_VERSION, " version ");
        try {
            assertThatThrownBy(() -> new DefaultHttp2HeadersDecoder(true, true).decodeHeaders(1, block))
                .isInstanceOf(Http2Exception.class);
        } finally {
            block.release();
        }
    }

    @DataProvider(name = "invalidHeaders")
    public Object[][] invalidHeaders() {
        return new Object[][] {
            { "x-other", " version" },
            { "x-other", "\tversion" },
            { SERVICE_VERSION, " \rversion " },
            { SERVICE_VERSION, " version\n " },
            { SERVICE_VERSION, "\u0000version" },
            { "X-Uppercase", "version" },
            { "connection", "keep-alive" }
        };
    }

    @Test(groups = "unit", dataProvider = "invalidHeaders")
    public void retainsStrictValidation(String name, String value) throws Exception {
        ByteBuf block = encode(new DefaultHttp2HeadersEncoder(), 1, name, value);
        try {
            assertThatThrownBy(() -> new CosmosHttp2HeadersDecoder(8192).decodeHeaders(1, block))
                .isInstanceOf(Http2Exception.class);
        } finally {
            block.release();
        }
    }

    @Test(groups = "unit")
    public void preservesHpackDynamicTableAcrossStreams() throws Exception {
        DefaultHttp2HeadersEncoder encoder = new DefaultHttp2HeadersEncoder();
        CosmosHttp2HeadersDecoder decoder = new CosmosHttp2HeadersDecoder(8192);
        for (int streamId : new int[] { 1, 3, 5 }) {
            ByteBuf block = encode(encoder, streamId, SERVICE_VERSION, " \tversion\t ");
            try {
                if (streamId != 1) {
                    assertThat(block.readableBytes()).isEqualTo(2);
                }
                assertThat(decoder.decodeHeaders(streamId, block).get(SERVICE_VERSION).toString())
                    .isEqualTo("version");
            } finally {
                block.release();
            }
        }
    }

    @Test(groups = "unit")
    public void doesNotNormalizeOtherHeaders() throws Exception {
        ByteBuf block = encode(new DefaultHttp2HeadersEncoder(), 1, "x-other", "version with spaces ");
        try {
            Http2Headers headers = new CosmosHttp2HeadersDecoder(8192).decodeHeaders(1, block);
            assertThat(headers.get("x-other").toString()).isEqualTo("version with spaces ");
        } finally {
            block.release();
        }
    }

    @Test(groups = "unit")
    public void enforcesHeaderListLimitBeforeNormalization() throws Exception {
        ByteBuf block = encode(new DefaultHttp2HeadersEncoder(), 1, SERVICE_VERSION,
            new String(new char[256]).replace('\u0000', ' ') + "version");
        try {
            assertThatThrownBy(() -> new CosmosHttp2HeadersDecoder(128).decodeHeaders(1, block))
                .isInstanceOf(Http2Exception.class);
        } finally {
            block.release();
        }
    }

    @Test(groups = "unit")
    public void retainsQueuedControlFrameProtection() {
        Http2SettingsSpec spec = Http2SettingsSpec.builder().build();
        Http2FrameCodec codec = new CosmosHttp2FrameCodecBuilder(spec)
            .initialSettings(CosmosHttp2ProtocolInitializer.initialSettings(spec)).build();
        try {
            assertThat(codec.encoder().getClass().getSimpleName()).isEqualTo("Http2ControlFrameLimitEncoder");
        } finally {
            codec.decoder().close();
            codec.encoder().close();
        }
    }

    @Test(groups = "unit")
    public void rejectsConsecutiveEmptyDataFrames() throws Exception {
        Http2SettingsSpec spec = Http2SettingsSpec.builder().build();
        Http2FrameCodec codec = new CosmosHttp2FrameCodecBuilder(spec)
            .initialSettings(CosmosHttp2ProtocolInitializer.initialSettings(spec)).build();
        AtomicReference<Throwable> error = new AtomicReference<>();
        EmbeddedChannel channel = channel(codec, error);
        ByteBuf frames = Unpooled.buffer();
        try {
            codec.connection().local().createStream(1, false);
            settingsFrame(frames);
            ByteBuf headers = encode(new DefaultHttp2HeadersEncoder(), 1, SERVICE_VERSION, " version ");
            try {
                frameHeader(frames, headers.readableBytes(), 1, 4, 1);
                frames.writeBytes(headers);
            } finally {
                headers.release();
            }
            for (int i = 0; i < 10; i++) {
                frameHeader(frames, 0, 0, 0, 1);
            }
            channel.writeInbound(frames);
            assertThat(error.get()).isInstanceOf(Http2Exception.class)
                .hasMessageContaining("empty data frames");
        } finally {
            if (frames.refCnt() > 0) {
                frames.release();
            }
            channel.finishAndReleaseAll();
        }
    }

    @Test(groups = "unit")
    public void rejectsConfiguredResetFrameFlood() throws Exception {
        Http2SettingsSpec spec = Http2SettingsSpec.builder()
            .maxDecodedRstFramesPerWindow(2, 60).build();
        Http2FrameCodec codec = new CosmosHttp2FrameCodecBuilder(spec)
            .initialSettings(CosmosHttp2ProtocolInitializer.initialSettings(spec)).build();
        AtomicReference<Throwable> error = new AtomicReference<>();
        EmbeddedChannel channel = channel(codec, error);
        codec.decoder().frameListener(new Http2FrameAdapter());
        ByteBuf frames = Unpooled.buffer();
        try {
            settingsFrame(frames);
            for (int streamId : new int[] { 1, 3, 5 }) {
                codec.connection().local().createStream(streamId, false);
                frameHeader(frames, 4, 3, 0, streamId);
                frames.writeInt(8);
            }
            channel.writeInbound(frames);
            assertThat(error.get()).isInstanceOf(Http2Exception.class)
                .hasMessageContaining("RST");
        } finally {
            if (frames.refCnt() > 0) {
                frames.release();
            }
            channel.finishAndReleaseAll();
        }
    }

    @Test(groups = "unit")
    public void rejectsQueuedControlFrameFlood() {
        Http2SettingsSpec spec = Http2SettingsSpec.builder().build();
        Http2FrameCodec codec = new CosmosHttp2FrameCodecBuilder(spec)
            .initialSettings(CosmosHttp2ProtocolInitializer.initialSettings(spec))
            .encoderEnforceMaxQueuedControlFrames(2).build();
        AtomicReference<Throwable> error = new AtomicReference<>();
        List<Object> pendingMessages = new ArrayList<>();
        List<ChannelPromise> pendingPromises = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                pendingMessages.add(msg);
                pendingPromises.add(promise);
            }
        }, codec, errorHandler(error));
        ByteBuf frames = Unpooled.buffer();
        try {
            for (int i = 0; i < 2; i++) {
                settingsFrame(frames);
            }
            channel.writeInbound(frames);
            assertThat(channel.isOpen()).isTrue();
            ByteBuf thirdFrame = Unpooled.buffer();
            settingsFrame(thirdFrame);
            channel.writeInbound(thirdFrame);
            assertThat(channel.isOpen()).isFalse();
            assertThat(pendingMessages.stream().filter(ByteBuf.class::isInstance).map(ByteBuf.class::cast)
                .anyMatch(buffer -> buffer.readableBytes() >= 17
                    && buffer.getUnsignedByte(buffer.readerIndex() + 3) == 7
                    && buffer.getUnsignedInt(buffer.readerIndex() + 13) == Http2Error.ENHANCE_YOUR_CALM.code()))
                .isTrue();
        } finally {
            if (frames.refCnt() > 0) {
                frames.release();
            }
            channel.finishAndReleaseAll();
            pendingMessages.forEach(ReferenceCountUtil::release);
            pendingPromises.forEach(ChannelPromise::trySuccess);
        }
    }

    @Test(groups = "unit")
    public void rejectsSmallContinuationFrameFlood() throws Exception {
        Http2SettingsSpec spec = Http2SettingsSpec.builder().build();
        Http2FrameCodec codec = new CosmosHttp2FrameCodecBuilder(spec)
            .initialSettings(CosmosHttp2ProtocolInitializer.initialSettings(spec))
            .decoderEnforceMaxSmallContinuationFrames(2).build();
        AtomicReference<Throwable> error = new AtomicReference<>();
        EmbeddedChannel channel = channel(codec, error);
        ByteBuf frames = Unpooled.buffer();
        ByteBuf headers = encode(new DefaultHttp2HeadersEncoder(), 1, SERVICE_VERSION, " version ");
        try {
            codec.connection().local().createStream(1, false);
            settingsFrame(frames);
            frameHeader(frames, 1, 1, 0, 1);
            frames.writeByte(headers.readByte());
            for (int i = 0; i < 3; i++) {
                frameHeader(frames, 1, 9, 0, 1);
                frames.writeByte(headers.readByte());
            }
            channel.writeInbound(frames);
            assertThat(error.get()).isInstanceOf(Http2Exception.class)
                .hasMessageContaining("continuations frames 2 exceeds maximum: 2");
        } finally {
            headers.release();
            if (frames.refCnt() > 0) {
                frames.release();
            }
            channel.finishAndReleaseAll();
        }
    }

    private static EmbeddedChannel channel(Http2FrameCodec codec, AtomicReference<Throwable> error) {
        return new EmbeddedChannel(codec, errorHandler(error));
    }

    private static SimpleChannelInboundHandler<Http2Frame> errorHandler(AtomicReference<Throwable> error) {
        return new SimpleChannelInboundHandler<Http2Frame>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Http2Frame msg) {
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                error.compareAndSet(null, cause);
            }
        };
    }

    private static void settingsFrame(ByteBuf output) {
        frameHeader(output, 0, 4, 0, 0);
    }

    private static void frameHeader(ByteBuf output, int size, int type, int flags, int streamId) {
        output.writeMedium(size).writeByte(type).writeByte(flags).writeInt(streamId);
    }

    private static ByteBuf encode(DefaultHttp2HeadersEncoder encoder, int streamId, String name, String value)
        throws Http2Exception {
        ByteBuf block = Unpooled.buffer();
        try {
            encoder.encodeHeaders(streamId, new DefaultHttp2Headers(false).status("200").add(name, value), block);
            return block;
        } catch (Http2Exception | RuntimeException failure) {
            block.release();
            throw failure;
        }
    }
}
