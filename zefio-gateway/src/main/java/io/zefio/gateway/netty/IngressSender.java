package io.zefio.gateway.netty;

import io.netty.channel.ChannelHandlerContext;
import io.zefio.core.payload.Payload;

/**
 * Interface for transmitting responses back to the Ingress client.
 * Supports both incremental chunked data and final response completion.
 */
public interface IngressSender {
    /**
     * Sends a single chunk of data to the client.
     */
    void sendChunk(Payload event, ChannelHandlerContext ctx, Object payload);

    /**
     * Completes the transaction and sends the final message.
     * Handles the 'Connection' header logic (keep-alive vs close).
     */
    void lastCompleteAndSend(Payload event, ChannelHandlerContext ctx, boolean keepAlive, Object payload);
}
