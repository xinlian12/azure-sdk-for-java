// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.http;

import com.azure.cosmos.Http2ConnectionConfig;
import com.azure.cosmos.implementation.Configs;
import com.azure.cosmos.implementation.HttpConstants;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2ConnectionAdapter;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamFrame;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.netty.NettyPipeline;
import reactor.netty.http.HttpProtocol;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

public class CosmosHttp2TransportTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String SERVICE_VERSION = HttpConstants.HttpHeaders.SERVER_VERSION;

    @DataProvider(name = "poolModes")
    public Object[][] poolModes() {
        return new Object[][] { { true }, { false } };
    }

    @Test(groups = "unit", dataProvider = "poolModes")
    public void normalizesHeadersOnActualCosmosResponsePath(boolean pooled) throws Exception {
        try (LocalServer server = new LocalServer(HttpProtocol.H2, SERVICE_VERSION, " \tversion\t ", false)) {
            ReactorNettyClient client = createClient(server, pooled);
            try {
                for (int i = 0; i < 3; i++) {
                    HttpResponse response = request(client, server);
                    assertThat(response.headerValue(SERVICE_VERSION)).isEqualTo("version");
                    assertThat(response.headers().value(SERVICE_VERSION)).isEqualTo("version");
                    assertThat(response.bodyAsString().block(TIMEOUT)).isEqualTo("ok");
                    assertThat(response.request().reactorNettyRequestRecord().isHttp2()).isTrue();
                }
            } finally {
                client.shutdown();
            }
        }
    }

    @Test(groups = "unit")
    public void stockReactorNettyRejectsSameResponse() throws Exception {
        try (LocalServer server = new LocalServer(HttpProtocol.H2, SERVICE_VERSION, " version ", false)) {
            assertThatThrownBy(() -> reactor.netty.http.client.HttpClient.newConnection()
                .secure(spec -> spec.sslContext(server.clientContext))
                .protocol(HttpProtocol.H2, HttpProtocol.HTTP11)
                .get().uri(server.uri().toString())
                .responseSingle((response, body) -> body.asString()).block(TIMEOUT))
                .hasStackTraceContaining("prohibited character");
        }
    }

    @Test(groups = "unit")
    public void pooledStreamsReuseConnectionAndRetainSettings() throws Exception {
        try (LocalServer server = new LocalServer(HttpProtocol.H2, SERVICE_VERSION, " version ", false)) {
            ReactorNettyClient client = createClient(server, true);
            try {
                Channel initialized = warmup(client, server);
                assertThat(initialized.pipeline().get(CosmosHttp2ProtocolInitializer.HANDLER_NAME)).isNull();
                assertThat(initialized.pipeline().get(NettyPipeline.HttpTrafficHandler)).isNull();
                List<String> parents = Flux.range(0, 8).flatMap(index -> client.send(new HttpRequest(
                    HttpMethod.GET, server.uri(), server.port(), new HttpHeaders()))
                    .flatMap(response -> {
                        Channel parent = parent(response);
                        Http2FrameCodec codec = parent.pipeline().get(Http2FrameCodec.class);
                        assertThat(codec.decoder().localSettings().initialWindowSize()).isEqualTo(1024 * 1024);
                        assertThat(codec.decoder().localSettings().maxFrameSize())
                            .isEqualTo(Configs.getHttp2MaxFrameSizeInBytes());
                        assertThat(response.headerValue(SERVICE_VERSION)).isEqualTo("version");
                        return response.bodyAsString().map(body -> {
                            assertThat(body).isEqualTo("ok");
                            return parent.id().asLongText();
                        });
                    }), 8).collectList().block(TIMEOUT);
                assertThat(parents).hasSize(8);
                assertThat(parents.stream().distinct().count()).isEqualTo(1);
            } finally {
                client.shutdown();
            }
        }
    }

    @Test(groups = "unit", dataProvider = "poolModes")
    public void preservesHttp11Fallback(boolean pooled) throws Exception {
        try (LocalServer server = new LocalServer(HttpProtocol.HTTP11, SERVICE_VERSION, "version", false)) {
            ReactorNettyClient client = createClient(server, pooled);
            try {
                HttpResponse response = request(client, server);
                assertThat(response.headerValue(SERVICE_VERSION)).isEqualTo("version");
                assertThat(response.bodyAsString().block(TIMEOUT)).isEqualTo("ok");
                assertThat(response.request().reactorNettyRequestRecord().isHttp2()).isFalse();
            } finally {
                client.shutdown();
            }
        }
    }

    @Test(groups = "unit")
    public void preservesCleartextHttp11() throws Exception {
        try (LocalServer server = new LocalServer(HttpProtocol.HTTP11, SERVICE_VERSION, "version", false, false)) {
            ReactorNettyClient client = createClient(server, true);
            try {
                HttpResponse response = request(client, server);
                assertThat(response.headerValue(SERVICE_VERSION)).isEqualTo("version");
                assertThat(response.bodyAsString().block(TIMEOUT)).isEqualTo("ok");
                assertThat(response.request().reactorNettyRequestRecord().isHttp2()).isFalse();
            } finally {
                client.shutdown();
            }
        }
    }

    @Test(groups = "unit")
    public void reconnectsAfterGoAway() throws Exception {
        try (LocalServer server = new LocalServer(HttpProtocol.H2, SERVICE_VERSION, " version ", false)) {
            ReactorNettyClient client = createClient(server, true);
            try {
                Channel firstParent = warmup(client, server);
                CountDownLatch goAway = new CountDownLatch(1);
                firstParent.eventLoop().submit(() -> firstParent.pipeline().get(Http2FrameCodec.class)
                    .connection().addListener(new Http2ConnectionAdapter() {
                        @Override
                        public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf data) {
                            goAway.countDown();
                        }
                    })).sync();
                for (Channel channel : server.channels) {
                    if (channel.pipeline().get(Http2FrameCodec.class) != null) {
                        channel.writeAndFlush(new DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR)).sync();
                    }
                }
                assertThat(goAway.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
                HttpResponse second = request(client, server);
                assertThat(second.headerValue(SERVICE_VERSION)).isEqualTo("version");
                assertThat(second.request().reactorNettyRequestRecord().getParentChannelId())
                    .isNotEqualTo(firstParent.id().asShortText());
                assertThat(second.bodyAsString().block(TIMEOUT)).isEqualTo("ok");
            } finally {
                client.shutdown();
            }
        }
    }

    @Test(groups = "unit")
    public void propagatesMidResponseCloseAndRecoversPool() throws Exception {
        try (LocalServer server = new LocalServer(HttpProtocol.H2, SERVICE_VERSION, " version ", false)) {
            ReactorNettyClient client = createClient(server, true);
            try {
                assertThatThrownBy(() -> client.send(new HttpRequest(HttpMethod.GET,
                    server.uri().resolve("/close"), server.port(), new HttpHeaders()))
                    .flatMap(HttpResponse::bodyAsString).block(TIMEOUT))
                    .hasStackTraceContaining("PrematureCloseException");
                HttpResponse response = request(client, server);
                assertThat(response.headerValue(SERVICE_VERSION)).isEqualTo("version");
                assertThat(response.bodyAsString().block(TIMEOUT)).isEqualTo("ok");
            } finally {
                client.shutdown();
            }
        }
    }

    @Test(groups = "unit")
    public void cancellationReleasesStreamWithoutClosingHealthyConnection() throws Exception {
        try (LocalServer server = new LocalServer(HttpProtocol.H2, SERVICE_VERSION, " version ", false)) {
            ReactorNettyClient client = createClient(server, true);
            try {
                Channel firstParent = warmup(client, server);
                assertThatThrownBy(() -> client.send(new HttpRequest(HttpMethod.GET,
                    server.uri().resolve("/never"), server.port(), new HttpHeaders()))
                    .timeout(Duration.ofMillis(200)).block(TIMEOUT))
                    .hasRootCauseInstanceOf(TimeoutException.class);
                HttpResponse second = request(client, server);
                assertThat(second.request().reactorNettyRequestRecord().getParentChannelId())
                    .isEqualTo(firstParent.id().asShortText());
                assertThat(second.headerValue(SERVICE_VERSION)).isEqualTo("version");
                assertThat(second.bodyAsString().block(TIMEOUT)).isEqualTo("ok");
            } finally {
                client.shutdown();
            }
        }
    }

    @DataProvider(name = "invalidResponses")
    public Object[][] invalidResponses() {
        return new Object[][] {
            { "x-other", " version " },
            { SERVICE_VERSION, " \rversion " },
            { SERVICE_VERSION, " version\n " },
            { "X-Uppercase", "version" },
            { "connection", "keep-alive" }
        };
    }

    @Test(groups = "unit", dataProvider = "invalidResponses")
    public void rejectsOtherMalformedResponseHeaders(String name, String value) throws Exception {
        try (LocalServer server = new LocalServer(HttpProtocol.H2, name, value, false)) {
            ReactorNettyClient client = createClient(server, true);
            try {
                Throwable failure = catchThrowable(() -> request(client, server));
                assertThat(failure).isNotNull();
                Throwable cause = failure;
                while (!(cause instanceof Http2Exception) && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                assertThat(cause).isInstanceOf(Http2Exception.class);
                assertThat(((Http2Exception) cause).error()).isEqualTo(Http2Error.PROTOCOL_ERROR);
            } finally {
                client.shutdown();
            }
        }
    }

    @Test(groups = "unit", dataProvider = "poolModes")
    public void failsPromptlyWhenPeerClosesBeforeSettings(boolean pooled) throws Exception {
        try (LocalServer server = new LocalServer(HttpProtocol.H2, SERVICE_VERSION, "version", true)) {
            ReactorNettyClient client = createClient(server, pooled);
            try {
                assertThatThrownBy(() -> request(client, server))
                    .hasStackTraceContaining("HTTP/2 connection closed before receiving initial SETTINGS");
            } finally {
                client.shutdown();
            }
        }
    }

    private static HttpResponse request(ReactorNettyClient client, LocalServer server) {
        return client.send(new HttpRequest(HttpMethod.GET, server.uri(), server.port(), new HttpHeaders()))
            .block(TIMEOUT);
    }

    private static Channel warmup(ReactorNettyClient client, LocalServer server) {
        AtomicReference<Channel> parent = new AtomicReference<>();
        String body = client.send(new HttpRequest(HttpMethod.GET, server.uri(), server.port(), new HttpHeaders()))
            .flatMap(response -> {
                parent.set(parent(response));
                return response.bodyAsString();
            }).block(TIMEOUT);
        assertThat(body).isEqualTo("ok");
        return parent.get();
    }

    private static Channel parent(HttpResponse response) {
        Channel channel = response.internConnection().channel();
        return channel.parent() == null ? channel : channel.parent();
    }

    private static ReactorNettyClient createClient(LocalServer server, boolean pooled) {
        Configs configs = new Configs() {
            @Override
            public SslContext getSslContext(boolean serverCertValidationDisabled, boolean http2Enabled) {
                return server.clientContext;
            }
        };
        HttpClientConfig config = new HttpClientConfig(configs)
            .withHttp2ConnectionConfig(new Http2ConnectionConfig().setEnabled(true)
                .setMinConnectionPoolSize(1).setMaxConnectionPoolSize(2))
            .withPoolSize(2)
            .withConnectionAcquireTimeout(Duration.ofSeconds(5));
        if (!pooled) {
            return ReactorNettyClient.create(config);
        }
        return (ReactorNettyClient) HttpClient.createFixed(config);
    }

    private static final class LocalServer implements AutoCloseable {
        private final NioEventLoopGroup eventLoops = new NioEventLoopGroup(1);
        private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        private final SslContext clientContext;
        private final Channel serverChannel;
        private final boolean tls;

        private LocalServer(HttpProtocol protocol, String headerName, String headerValue, boolean closeBeforeSettings)
            throws Exception {
            this(protocol, headerName, headerValue, closeBeforeSettings, true);
        }

        private LocalServer(HttpProtocol protocol, String headerName, String headerValue, boolean closeBeforeSettings,
                            boolean tls)
            throws Exception {
            this.tls = tls;
            KeyStore store = KeyStore.getInstance("JKS");
            try (InputStream input = getClass().getResourceAsStream("/server.jks")) {
                store.load(input, "rntbdTest".toCharArray());
            }
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, "rntbdTest".toCharArray());
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(store);
            ApplicationProtocolConfig clientProtocols = protocols(
                ApplicationProtocolNames.HTTP_2, ApplicationProtocolNames.HTTP_1_1);
            clientContext = SslContextBuilder.forClient().sslProvider(SslProvider.JDK)
                .trustManager(trust).applicationProtocolConfig(clientProtocols).build();
            SslContext serverContext = SslContextBuilder.forServer(keys).sslProvider(SslProvider.JDK)
                .applicationProtocolConfig(protocols(protocol == HttpProtocol.H2
                    ? ApplicationProtocolNames.HTTP_2 : ApplicationProtocolNames.HTTP_1_1)).build();
            serverChannel = new ServerBootstrap().group(eventLoops).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channels.add(channel);
                        if (tls) {
                            channel.pipeline().addLast(serverContext.newHandler(channel.alloc()));
                        }
                        if (closeBeforeSettings) {
                            channel.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                    ctx.close();
                                }
                            });
                        } else if (protocol == HttpProtocol.H2) {
                            channel.pipeline().addLast(Http2FrameCodecBuilder.forServer().build(),
                                new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
                                    @Override
                                    protected void initChannel(Channel stream) {
                                        stream.pipeline().addLast(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                                            @Override
                                            protected void channelRead0(ChannelHandlerContext ctx,
                                                                        Http2StreamFrame frame) {
                                                if (frame instanceof Http2HeadersFrame) {
                                                    CharSequence path = ((Http2HeadersFrame) frame).headers().path();
                                                    if ("/never".contentEquals(path)) {
                                                        return;
                                                    }
                                                    if ("/close".contentEquals(path)) {
                                                        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(
                                                            new DefaultHttp2Headers().status("200"), false))
                                                            .addListener(future -> ctx.channel().parent().close());
                                                        return;
                                                    }
                                                    ctx.write(new DefaultHttp2HeadersFrame(
                                                        new DefaultHttp2Headers(false).status("200")
                                                            .add(headerName, headerValue), false));
                                                    ctx.writeAndFlush(new DefaultHttp2DataFrame(
                                                        Unpooled.copiedBuffer("ok", CharsetUtil.UTF_8), true));
                                                }
                                            }
                                        });
                                    }
                                }));
                        } else {
                            channel.pipeline().addLast(new HttpServerCodec(), new HttpObjectAggregator(1024),
                                new SimpleChannelInboundHandler<FullHttpRequest>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
                                        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                                            HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                                            Unpooled.copiedBuffer("ok", CharsetUtil.UTF_8));
                                        response.headers().set(headerName, headerValue).set("content-length", 2);
                                        ctx.writeAndFlush(response);
                                    }
                                });
                        }
                    }
                }).bind("localhost", 0).sync().channel();
            channels.add(serverChannel);
        }

        private static ApplicationProtocolConfig protocols(String... protocols) {
            return new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT, protocols);
        }

        private int port() {
            return ((InetSocketAddress) serverChannel.localAddress()).getPort();
        }

        private URI uri() {
            return URI.create((tls ? "https" : "http") + "://localhost:" + port() + "/");
        }

        @Override
        public void close() {
            channels.close().awaitUninterruptibly();
            eventLoops.shutdownGracefully(0, 5, TimeUnit.SECONDS).awaitUninterruptibly();
        }
    }
}
