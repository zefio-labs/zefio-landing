package io.zefio.gateway.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.base.MDCKey;
import io.zefio.core.common.util.ApplicationAttributes;
import io.zefio.core.common.util.FlowErrorUtils;
import io.zefio.core.payload.Payload;
import io.zefio.core.schema.dto.TwowayIngressValues;
import io.zefio.core.Ingress;
import io.zefio.core.IngressHandler;
import io.zefio.core.FireAndForgetCallback;
import io.zefio.core.payload.ResponseListener;
import io.zefio.core.payload.util.IngressErrorUtils;
import io.zefio.gateway.netty.IngressSender;
import io.zefio.gateway.netty.dto.ServerHandlerContext;
import io.zefio.gateway.netty.util.NettyUtils;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.zefio.gateway.netty.NettyMdcConstants.MDC_CONTEXT_KEY;

/**
 * Base handler for Ingress traffic.
 * Handles the transformation of Netty messages into internal Payloads and manages the transaction lifecycle.
 */
public abstract class AbstractIngressHandler<T> extends AbstractEndpointHandler<T, Payload> implements IngressSender {
    protected final Ingress ingress;
    protected final IngressHandler ingressHandler;
    protected final ResponseListener onewayListener;

    public AbstractIngressHandler(ServerHandlerContext<Payload> context) {
        super(context.getFlowName(), context.getIngress(), context.getTxnManager(),
                ((TwowayIngressValues)context.getValues()).getRequestEncoding(),
                ((TwowayIngressValues)context.getValues()).getResponseEncoding(),
                context.getNettyValues().getMaxContentLength());
        this.ingress = context.getIngress();
        this.ingressHandler = context.getIngressHandler();
        this.onewayListener = new FireAndForgetCallback(context.getIngress().getMetricsAggregator());
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, T msg) throws Exception {
        Payload payload = null;
        try {
            // 1. Delegate data extraction to implementation (e.g., TCP, WebSocket)
            byte[] rawData = handleDataExtraction(ctx, msg);

            if (rawData == null) {
                log.debug("Data extraction returned null. Skipping processing for channel: {}", ctx.channel().id());
                return;
            }

            // 2. Build the Payload (Event)
            payload = this.ingress.getEventBuilder().withBody(rawData, requestEncoding);

            // 3. Check for stat suppression flags
            Boolean suppress = ctx.channel().attr(ApplicationAttributes.SUPPRESS_STAT_LOG).get();
            if (Boolean.TRUE.equals(suppress)) payload.setSuppressStatLog(true);

            // 4. Initialize and store MDC context for traceability
            setMdcContext(ctx, payload);

            log.info("Received from channel[{}] id[{}] length[{}]", ctx.channel(), ctx.channel().hashCode(), rawData.length);

            // 5. Delegate to IngressHandler to start the internal Flow
            if (this.ingress.isTwoWay()) {
                payload.setCallback(createResponseListener(payload, ctx));
                sendToIngress(payload, ctx);
            } else {
                payload.setCallback(onewayListener);
                ingressHandler.onPayload(payload);
            }

        } catch (Exception e) {
            // Edge Rejection: Handle parsing errors before the request enters the pipeline
            log.error("Edge Parsing Error: {}", e.getMessage());
            sendEdgeErrorResponse(ctx, e);
        } finally {
            MDC.clear();
        }
    }

    protected abstract byte[] handleDataExtraction(ChannelHandlerContext ctx, T msg) throws FlowException;

    protected abstract ResponseListener createResponseListener(Payload payload, ChannelHandlerContext ctx);

    @Override
    public void sendChunk(Payload event, ChannelHandlerContext ctx, Object payload) {
        ctx.channel().writeAndFlush(payload).addListener(f -> NettyUtils.runWithMdc(ctx.channel(), () -> {
            if (!f.isSuccess()) {
                log.warn("Chunk send failed", f.cause());
                event.setThrowable(f.cause());
            }
            logPayload("chunk", payload);
        }));
    }

    @Override
    public void lastCompleteAndSend(Payload event, ChannelHandlerContext ctx, boolean keepAlive, Object payload) {
        // 1. Complete the transaction
        try {
            txnManager.complete(ctx.channel(), event);
        } catch (Exception e) {
            log.warn("Transaction completion error for channel[{}]: {}", ctx.channel().id(), e.getMessage());
            if (!event.hasException()) {
                event.setThrowable(e);
            }
        }

        // 2. Transmit the final response
        ctx.channel().write(payload);

        ChannelFuture future = keepAlive
                ? ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
                : ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);

        future.addListener((ChannelFutureListener) f -> NettyUtils.runWithMdc(ctx.channel(), () -> {
            logPayload("response", payload);
            if (!f.isSuccess()) {
                log.warn("Response not sent (Session likely disconnected)");
                if (!event.hasException()) {
                    event.setThrowable(f.cause());
                }
            }
        }));
    }

    protected void sendToIngress(Payload payload, ChannelHandlerContext ctx) {
        NettyUtils.runWithMdc(ctx.channel(), () -> {
            CompletableFuture<Payload> future = this.txnManager.send(ctx.channel(), payload);

            if (future.isDone() && future.isCompletedExceptionally()) {
                future.whenComplete((result, throwable) -> {
                    payload.setThrowable(throwable);
                    ingressHandler.onPayload(payload);
                });
            } else {
                ingressHandler.onPayload(payload);
            }
        });
    }

    private void logPayload(String type, Object payload) {
        byte[] logBytes = null;
        if (payload instanceof byte[]) logBytes = (byte[]) payload;
        else if (payload instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) payload;
            logBytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), logBytes);
        } else if (payload instanceof Payload) {
            logBytes = ((Payload) payload).getBody();
        }

        if (logBytes != null) {
            log.info("{} send length[{}] encoding[{}]\n[{}]",
                    type, logBytes.length, responseEncoding, new String(logBytes, responseEncoding));
        }
    }

    protected void setMdcContext(ChannelHandlerContext ctx, Payload payload) {
        String channelId = ctx.channel().id().asShortText();

        MDC.put(MDCKey.CID.getKey(), channelId);
        MDC.put(MDCKey.TID.getKey(), payload.getTrxID());
        MDC.put(MDCKey.FLOW.getKey(), flowName);

        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        if (mdcContext != null) {
            ctx.channel().attr(MDC_CONTEXT_KEY).set(mdcContext);
            payload.setMdcContext(mdcContext);
        }
    }

    protected void sendEdgeErrorResponse(ChannelHandlerContext ctx, Throwable cause) {
        FlowException flowEx = FlowErrorUtils.convert(cause);
        log.error("Edge Reject: {}", flowEx.getMessage(), flowEx);

        // Build error payload using format-specific builder (Fixed, JSON, etc.)
        byte[] errorBytes = IngressErrorUtils.buildEdgeErrorPayload(
                this.ingress.getEventBuilder(),
                flowEx,
                responseEncoding
        );

        onSendEdgeError(ctx, errorBytes, flowEx);
    }
    protected abstract void onSendEdgeError(ChannelHandlerContext ctx, byte[] errorBytes, FlowException flowEx);
}
