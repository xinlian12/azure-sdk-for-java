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

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class RntbdResponseDecoder extends ByteToMessageDecoder {

    private long packetLength;
    private State state;
    private RntbdResponse response;

    public RntbdResponseDecoder() {
        // inital state
        this.packetLength = -1;
        this.state = State.NONE;
        response = null;
    }

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

        if (this.packetLength < 0) {
            this.packetLength = RntbdResponseStatus.LENGTH;
        }

        for(;;) {
            if (in.readableBytes() < this.packetLength) {
                return;
            }

            if (this.state == State.NONE) {
                this.packetLength = RntbdFramer.getHeadLength(in);
                checkPoint(State.HEAD);
            } else if (this.state == State.HEAD) {
                // Currently the decode head contains decode body,
                // should try to split into head and body
                this.decodeHead(context, in, out);
            } else if (this.state == State.BODY) {
                Pair<RntbdResponse, Long> responseResult = RntbdResponse.decode(in);
                if (responseResult.getLeft() == null) {
                    throw new CorruptedFrameException("Can not parse payload");
                } else {
                    outputRntbdResponse(in, out, responseResult.getLeft());
                }
            }
        }
    }

    private void decodeHead(final ChannelHandlerContext context, final ByteBuf in, final List<Object> out) {
        Pair<RntbdResponse, Long> responseResult = RntbdResponse.decode(in);
        if (responseResult.getLeft() != null) {
            outputRntbdResponse(in, out, responseResult.getLeft());
        } else {
            this.packetLength += responseResult.getRight();
            checkPoint(State.BODY);
        }
    }

    private void outputRntbdResponse(final ByteBuf in, final List<Object> out, RntbdResponse response) {
        in.discardReadBytes();
        out.add(response.retain());

        this.packetLength = -1;
        this.state = State.NONE;
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
