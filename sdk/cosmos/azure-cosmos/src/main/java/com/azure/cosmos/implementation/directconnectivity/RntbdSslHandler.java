package com.azure.cosmos.implementation.directconnectivity;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;

public class RntbdSslHandler extends SslHandler {
    private final static Logger logger = LoggerFactory.getLogger(RntbdSslHandler.class);

    public RntbdSslHandler(SSLEngine engine) {
        super(engine);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        if (channel.attr(AllRequestsDictionary.shouldLog).get()) {
            logger.info("SSlhandler READCOMPLETE:" + channel.id() + "|" + channel.unsafe().recvBufAllocHandle().lastBytesRead());
        }        super.channelReadComplete(ctx);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        if (channel.attr(AllRequestsDictionary.shouldLog).get()) {
            logger.info("SslHandler FLUSH:" + channel.id() + "|" + channel.unsafe().recvBufAllocHandle().lastBytesRead());
        }

        super.flush(ctx);
    }
}
