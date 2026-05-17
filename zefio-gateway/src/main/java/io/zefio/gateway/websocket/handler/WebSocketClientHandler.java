package io.zefio.gateway.websocket.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ZefioMessage;
import io.zefio.gateway.netty.dto.ClientHandlerContext;
import io.zefio.gateway.netty.handler.AbstractUpstreamHandler;
import io.zefio.gateway.netty.util.NettyUtils;

/**
 * Terminal Upstream handler for WebSocket clients.
 * Orchestrates the HTTP-to-WebSocket handshake and handles subsequent frame processing.
 */
public class WebSocketClientHandler extends AbstractUpstreamHandler<Object, Payload> {

    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;

    public WebSocketClientHandler(ClientHandlerContext<Payload> context, WebSocketClientHandshaker handshaker) {
        super(context);
        this.handshaker = handshaker;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // Initiate the handshake when the channel becomes active
        handshaker.handshake(ctx.channel());
    }

    @Override
    protected void handleRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();

        // 1. Handshake Orchestration
        if (!handshaker.isHandshakeComplete()) {
            if (msg instanceof FullHttpResponse) {
                try {
                    handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                    NettyUtils.runWithMdc(ch, () -> log.info("WebSocket Handshake finalized successfully."));
                    handshakeFuture.setSuccess();
                } catch (WebSocketHandshakeException e) {
                    handshakeFuture.setFailure(e);
                }
            } else {
                handshakeFuture.setFailure(new WebSocketHandshakeException("Unexpected message received during handshake: " + msg));
            }
            return;
        }

        // 2. WebSocket Frame Processing
        if (msg instanceof WebSocketFrame) {
            WebSocketFrame frame = (WebSocketFrame) msg;
            ByteBuf content = frame.content();

            // Memory Guard: Enforce frame length limits
            if (content.readableBytes() > maxContentLength) {
                NettyUtils.runWithMdc(ch, () -> log.error("Frame exceeds maxContentLength: {} bytes.", content.readableBytes()));
                ctx.close();
                return;
            }

            if (frame instanceof BinaryWebSocketFrame || frame instanceof TextWebSocketFrame) {
                byte[] datas = new byte[content.readableBytes()];
                content.readBytes(datas);

                // Promote raw bytes to standardized Payload object for the core engine
                Payload responsePayload = new ZefioMessage(datas, responseEncoding);

                // Prepare for Lazy Parsing by injecting metadata context
                responsePayload.setTelegramName(this.context.getTelegramName());

                if (upstream.isTwoWay()) {
                    // Notify transaction manager of completion
                    this.txnManager.complete(ch, responsePayload);
                }

                NettyUtils.runWithMdc(ch, () -> log.info("WebSocket Upstream received frame. Size: [{}]", datas.length));

            } else if (frame instanceof PongWebSocketFrame) {
                // Heartbeat management
            } else if (frame instanceof CloseWebSocketFrame) {
                ch.close();
            }
        }
    }
}
