// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.implementation.directconnectivity.rntbd;

import com.azure.cosmos.implementation.apachecommons.lang.tuple.Pair;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

public final class RntbdResponseDecoder extends ByteToMessageDecoder {

    private static final Logger logger = LoggerFactory.getLogger(RntbdResponseDecoder.class);
    private long packetLength = 0;
    private State state = State.NONE;
    private RntbdResponse rntbdResponse = null;
    private Instant decodeStartTime;
    private Instant decodeEndTime;

    /**
     * Deserialize from an input {@link ByteBuf} to an {@link RntbdResponse} instance.
     * <p>
     * This method is called till it reads no bytes from the {@link ByteBuf} or there is no more data to be read.
     *
     * @param context the {@link ChannelHandlerContext} to which this {@link RntbdResponseDecoder} belongs.
     * @param in the {@link ByteBuf} to which data to be decoded is read.
     * @param out the {@link List} to which decoded messages are added.
     */
    @Override
    protected void decode(final ChannelHandlerContext context, final ByteBuf in, final List<Object> out) {
       // logger.info("DECODE: {} | {}", context.channel().id(), in.readableBytes());
        long packetLength = this.packetLength;
        if (packetLength == 0) {
            packetLength = RntbdResponseStatus.LENGTH;
            decodeStartTime = Instant.now();
        }

        if (in.readableBytes() < packetLength) {
            return;
        }

        if (this.state == State.NONE) {
            this.packetLength = RntbdFramer.getHeadLength(in);
            checkPoint(State.HEAD);

            // if got enough length for the head
            if (in.readableBytes() >= this.packetLength) {
                decodeHead(context, in, out);
            }
        } else if (this.state == State.HEAD) {
            decodeHead(context, in, out);
        } else if (this.state == State.BODY) {
            Pair<RntbdResponse, Long> responseResult = RntbdResponse.decode(in);
            if (responseResult.getLeft() == null) {
                // something is wrong
                throw new CorruptedFrameException("Cannot parse payload");
            } else {
                outputRntbdResponse(context, in, out, responseResult.getLeft());
            }
        }

//        logger.info("DECODE: {} | {}", context.channel().id(), this.packetLength);
    }

    private void decodeHead(final ChannelHandlerContext context, final ByteBuf in, final List<Object> out) {
        // decode head and get the body length
        Pair<RntbdResponse, Long> responseResult = RntbdResponse.decode(in);
        if (responseResult.getLeft() != null) {
            outputRntbdResponse(context, in, out, responseResult.getLeft());
        } else {
            // get body length
            this.packetLength += responseResult.getRight();
            checkPoint(State.BODY);
        }
    }

    private void outputRntbdResponse(final ChannelHandlerContext context, final ByteBuf in, final List<Object> out, RntbdResponse response) {
        logger.debug("DECODE COMPLETE: {} | {}", context.channel().id(), response.getTransportRequestId());

        decodeEndTime = Instant.now();
        response.setDecodeStartTime(this.decodeStartTime);
        response.setDecodeEndTime(this.decodeEndTime);

        in.discardReadBytes();
        out.add(response.retain());

        this.packetLength = 0;
        this.rntbdResponse = null;
        this.state = State.NONE;
        this.decodeStartTime = null;
        this.decodeEndTime = null;
    }

    private void checkPoint(State state) {
        this.state = state;
    }

    private enum State {
        NONE,
        HEAD,
        BODY
    }
}
