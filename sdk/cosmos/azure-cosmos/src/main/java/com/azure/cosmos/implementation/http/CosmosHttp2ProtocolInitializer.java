// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.http;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2SettingsAckFrame;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import reactor.netty.Connection;
import reactor.netty.ConnectionObserver;
import reactor.netty.NettyPipeline;
import reactor.netty.channel.ChannelOperations;
import reactor.netty.http.Http2SettingsSpec;

import java.io.IOException;

/**
 * Installs the Cosmos headers decoder during TLS protocol selection, before an HTTP/2 codec has
 * sent its connection preface. HTTP/1.1 remains on Reactor Netty's standard initialization path.
 * This integration relies on Reactor Netty's HTTP/2 pipeline names and CONNECTED/CONFIGURED
 * lifecycle contract; the local TLS transport tests must be run when upgrading Reactor Netty.
 */
final class CosmosHttp2ProtocolInitializer extends ChannelInboundHandlerAdapter {
    static final String HANDLER_NAME = "cosmosHttp2ProtocolInitializer";
    private final ConnectionObserver observer;
    private final Http2SettingsSpec settingsSpec;

    private CosmosHttp2ProtocolInitializer(ConnectionObserver observer, Http2SettingsSpec settingsSpec) {
        this.observer = observer;
        this.settingsSpec = settingsSpec;
    }

    static void install(Channel channel, ConnectionObserver observer, Http2SettingsSpec settingsSpec) {
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(NettyPipeline.H2OrHttp11Codec) == null) {
            if (pipeline.get(NettyPipeline.HttpCodec) instanceof HttpClientCodec) {
                return;
            }
            throw new IllegalStateException("Missing Reactor Netty HTTP/2 protocol negotiation handler");
        }
        pipeline.addBefore(NettyPipeline.H2OrHttp11Codec, HANDLER_NAME,
            new CosmosHttp2ProtocolInitializer(observer, settingsSpec));
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
        if (sslHandler == null) {
            throw new IllegalStateException("Missing TLS handler during HTTP/2 protocol negotiation");
        }
        if (ApplicationProtocolNames.HTTP_2.equals(sslHandler.applicationProtocol())) {
            ChannelPipeline pipeline = ctx.pipeline();
            pipeline.remove(NettyPipeline.H2OrHttp11Codec);
            CosmosHttp2FrameCodecBuilder builder = new CosmosHttp2FrameCodecBuilder(settingsSpec);
            builder.initialSettings(initialSettings(settingsSpec));
            if (pipeline.get(NettyPipeline.LoggingHandler) != null) {
                builder.frameLogger(new Http2FrameLogger(LogLevel.DEBUG, "reactor.netty.http.client.h2"));
            }
            pipeline.addBefore(NettyPipeline.ReactiveBridge, NettyPipeline.H2Flush,
                    new FlushConsolidationHandler(1024, true))
                .addBefore(NettyPipeline.ReactiveBridge, NettyPipeline.HttpCodec, builder.build())
                .addBefore(NettyPipeline.ReactiveBridge, "cosmosHttp2SettingsAck", SettingsAckHandler.INSTANCE)
                .addBefore(NettyPipeline.ReactiveBridge, NettyPipeline.H2MultiplexHandler,
                    new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            // Server-initiated streams are not supported by the Cosmos HTTP client.
                            channel.close();
                        }
                    }))
                .addBefore(NettyPipeline.ReactiveBridge, NettyPipeline.HttpTrafficHandler,
                    new ConnectionInitializationHandler(observer));
        }
        ctx.fireChannelActive();
        ctx.pipeline().remove(this);
    }

    static Http2Settings initialSettings(Http2SettingsSpec spec) {
        Http2Settings settings = Http2Settings.defaultSettings().maxHeaderListSize(spec.maxHeaderListSize());
        if (spec.headerTableSize() != null) {
            settings.headerTableSize(spec.headerTableSize());
        }
        if (spec.initialWindowSize() != null) {
            settings.initialWindowSize(spec.initialWindowSize());
        }
        if (spec.maxConcurrentStreams() != null) {
            settings.maxConcurrentStreams(spec.maxConcurrentStreams());
        }
        if (spec.maxFrameSize() != null) {
            settings.maxFrameSize(spec.maxFrameSize());
        }
        if (spec.pushEnabled() != null) {
            settings.pushEnabled(spec.pushEnabled());
        }
        return settings;
    }

    private static final class ConnectionInitializationHandler extends ChannelInboundHandlerAdapter {
        private final ConnectionObserver observer;
        private boolean configured;

        private ConnectionInitializationHandler(ConnectionObserver observer) {
            this.observer = observer;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            observer.onStateChange(connection(ctx.channel()), ConnectionObserver.State.CONNECTED);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Http2SettingsFrame) {
                if (!configured) {
                    configured = true;
                    observer.onStateChange(connection(ctx.channel()), ConnectionObserver.State.CONFIGURED);
                    ctx.pipeline().remove(NettyPipeline.ReactiveBridge);
                    ctx.pipeline().remove(this);
                }
                ReferenceCountUtil.release(msg);
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (!configured) {
                ctx.fireExceptionCaught(new IOException("HTTP/2 connection closed before receiving initial SETTINGS"));
            }
            ctx.fireChannelInactive();
        }

        private static Connection connection(Channel channel) {
            ChannelOperations<?, ?> operations = ChannelOperations.get(channel);
            return operations == null ? Connection.from(channel) : operations;
        }
    }

    @ChannelHandler.Sharable
    private static final class SettingsAckHandler extends ChannelInboundHandlerAdapter {
        private static final SettingsAckHandler INSTANCE = new SettingsAckHandler();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Http2SettingsAckFrame) {
                ReferenceCountUtil.release(msg);
            } else {
                ctx.fireChannelRead(msg);
            }
        }
    }
}
