package io.zefio.gateway.websocket.encoder;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.zefio.core.payload.Payload;

import java.util.List;

/**
 * Upstream encoder that transforms internal Payload objects into Netty WebSocket frames.
 * Operates at the edge of the pipeline just before physical transmission.
 */
public class PayloadToWebSocketFrameEncoder extends MessageToMessageEncoder<Payload> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Payload payload, List<Object> out) {
        // Extracts the raw byte array from the Payload body and wraps it
        // into a Netty BinaryWebSocketFrame for wire transmission.
        out.add(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(payload.getBody())));
    }
}
