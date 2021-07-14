package com.azure.cosmos.implementation.directconnectivity.rntbd;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.util.List;

public class RntbdSslHandler extends SslHandler {

    private static final Logger logger = LoggerFactory.getLogger(RntbdSslHandler.class);

    public RntbdSslHandler(SSLEngine engine) {
        super(engine);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws SSLException {
        //logger.info("RntbdSSlHandler DECODE: {} | {}", ctx.channel().id(), in.readableBytes());
        super.decode(ctx, in, out);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        if (msg instanceof ByteBuf) {
//            logger.info("channelRead: {} | {} | {}", ctx.channel().id(), ((ByteBuf) msg).readableBytes(), ((ByteBuf) msg).capacity());
//        }

        super.channelRead(ctx, msg);
    }

}
