package io.zefio.gateway.tcp.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ResponseListener;
import io.zefio.gateway.netty.NettyRequestReplyCallback;
import io.zefio.gateway.netty.chunked.ChunkedResponseEncoderFactory;
import io.zefio.gateway.netty.chunked.ChunkedResponseEncoderStrategy;
import io.zefio.gateway.netty.dto.HandlerDefinition;
import io.zefio.gateway.netty.dto.ServerHandlerContext;
import io.zefio.gateway.netty.handler.AbstractIngressHandler;

/**
 * Terminal Ingress handler for TCP Server communication.
 * Manages data extraction and orchestrates chunked response transmission for large payloads.
 */
public class TcpServerHandler extends AbstractIngressHandler<ByteBuf> {

    protected final boolean keepAlive;

    /** Offset used to identify if a message should be treated as a "long message" (chunked) */
    protected int longMessageOffset;

    /** Strategy for encoding and sending chunked responses */
    protected ChunkedResponseEncoderStrategy chunkHandler;

    public TcpServerHandler(ServerHandlerContext<Payload> context, HandlerDefinition handlerDef) {
        super(context);
        this.keepAlive = context.getNettyValues().getKeepAlive();

        // Initialize the chunked response splitter if configured
        if (handlerDef.getSplitter() != null && handlerDef.getSplitter().isBigMessage()) {
            this.longMessageOffset = handlerDef.getSplitter().getLongMessageOffset();
            this.chunkHandler = ChunkedResponseEncoderFactory.create(
                    handlerDef,
                    this.ingress,
                    this.responseEncoding,
                    this.keepAlive,
                    this
            );
        }
    }

    /**
     * Called by AbstractIngressHandler.channelRead0.
     * MDC setup and Flow orchestration are handled by the parent class.
     */
    @Override
    protected byte[] handleDataExtraction(ChannelHandlerContext ctx, ByteBuf msg) throws FlowException {
        // Security: Validate data length before extraction
        if (msg.readableBytes() > maxContentLength) {
            handleExceededLength(ctx, msg.readableBytes());
        }

        byte[] datas = new byte[msg.readableBytes()];
        msg.readBytes(datas);
        return datas;
    }

    /**
     * Creates a ResponseListener used for Two-way communication.
     */
    @Override
    protected ResponseListener createResponseListener(Payload requestPayload, ChannelHandlerContext ctx) {
        return new NettyRequestReplyCallback(ingress.getMetricsAggregator(), ingress.getEventBuilder(), responseEncoding, ctx) {
            @Override
            public Payload response(Payload payload) {
                try {
                    if (isLongMessage(payload.getBody())) {
                        // Handle large response payloads via chunking strategy
                        chunkHandler.sendChunkedResponse(payload, ctx);
                    } else {
                        // Standard payloads are passed to the final DefaultByteArrayEncoder
                        lastCompleteAndSend(payload, ctx, keepAlive, payload);
                    }
                } catch (Exception e) {
                    log.error("Error occurred while sending TCP response", e);
                    payload.setThrowable(e);
                }
                return payload;
            }
        };
    }

    /**
     * Determines if the response body exceeds the long message threshold.
     */
    private boolean isLongMessage(byte[] data) {
        return longMessageOffset > 0 && data.length > longMessageOffset;
    }

    /**
     * Writes the error payload provided by the parent to the channel and closes the connection.
     */
    @Override
    protected void onSendEdgeError(ChannelHandlerContext ctx, byte[] errorBytes, FlowException flowEx) {
        ctx.writeAndFlush(Unpooled.wrappedBuffer(errorBytes))
                .addListener(io.netty.channel.ChannelFutureListener.CLOSE);
    }
}
