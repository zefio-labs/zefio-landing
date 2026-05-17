package io.zefio.gateway.netty.chunked;

import io.netty.channel.ChannelHandlerContext;
import io.zefio.core.payload.Payload;

/**
 * Strategy interface for encoding and sending chunked responses over a Netty channel.
 */
public interface ChunkedResponseEncoderStrategy {
    /**
     * Fragments and sends the payload body as multiple chunks.
     */
    void sendChunkedResponse(Payload payload, ChannelHandlerContext ctx);
}
