package io.zefio.gateway.websocket.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ResponseListener;
import io.zefio.gateway.netty.NettyRequestReplyCallback;
import io.zefio.gateway.netty.dto.HandlerDefinition;
import io.zefio.gateway.netty.dto.ServerHandlerContext;
import io.zefio.gateway.netty.handler.AbstractIngressHandler;
import io.zefio.gateway.netty.util.NettyUtils;

/**
 * Terminal Ingress handler for WebSocket server endpoints.
 * Extracts data from WebSocket frames and provides a callback listener for asynchronous responses.
 */
public class WebSocketServerHandler extends AbstractIngressHandler<Object> {

    public WebSocketServerHandler(ServerHandlerContext<Payload> context, HandlerDefinition handlerDef) {
        super(context);
    }

    /**
     * Invoked by AbstractIngressHandler.channelRead0.
     * Handles specific WebSocket frame types and extracts raw content.
     */
    @Override
    protected byte[] handleDataExtraction(ChannelHandlerContext ctx, Object frame) throws FlowException {
        if (!(frame instanceof WebSocketFrame)) {
            throw new FlowException(FlowResultStatus.BAD_REQUEST, "Non-WebSocket message type received: " + frame.getClass().getName());
        }

        WebSocketFrame wsFrame = (WebSocketFrame) frame;
        ByteBuf content = wsFrame.content();

        // Memory Guard: Validate content length
        if (content.readableBytes() > maxContentLength) {
            handleExceededLength(ctx, content.readableBytes());
        }

        if (wsFrame instanceof BinaryWebSocketFrame || wsFrame instanceof TextWebSocketFrame) {
            byte[] datas = new byte[content.readableBytes()];
            content.readBytes(datas);
            return datas;
        } else if (wsFrame instanceof CloseWebSocketFrame) {
            NettyUtils.runWithMdc(ctx.channel(), () -> log.info("WebSocket CloseFrame received. Closing session."));
            txnManager.close(ctx.channel());
            return null;
        }

        throw new FlowException(FlowResultStatus.BAD_REQUEST, "Unsupported WebSocket frame type.");
    }

    /**
     * Creates a listener to transform engine payloads back into WebSocket BinaryFrames.
     */
    @Override
    protected ResponseListener createResponseListener(Payload requestPayload, ChannelHandlerContext ctx) {
        return new NettyRequestReplyCallback(ingress.getMetricsAggregator(), ingress.getEventBuilder(), responseEncoding, ctx) {
            @Override
            public Payload response(Payload payload) {
                ByteBuf byteBuf = Unpooled.wrappedBuffer(payload.getBody());
                BinaryWebSocketFrame frame = new BinaryWebSocketFrame(byteBuf);

                // WebSocket connections are persistent by default (keepAlive = true)
                lastCompleteAndSend(payload, ctx, true, frame);
                return payload;
            }
        };
    }

    /**
     * Transforms engine errors into TextWebSocketFrames for edge error reporting.
     */
    @Override
    protected void onSendEdgeError(ChannelHandlerContext ctx, byte[] errorBytes, FlowException flowEx) {
        String errorMsg = new String(errorBytes, responseEncoding != null ? responseEncoding : requestEncoding);

        // Send error message and close the connection immediately
        ctx.writeAndFlush(new TextWebSocketFrame(errorMsg))
                .addListener(io.netty.channel.ChannelFutureListener.CLOSE);
    }
}
