package io.zefio.gateway.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.timeout.ReadTimeoutException;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.base.MDCKey;
import io.zefio.core.GatewayPlugin;
import io.zefio.gateway.netty.NettyMdcConstants;
import io.zefio.gateway.netty.transaction.ITxnManager;
import io.zefio.gateway.netty.util.NettyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Terminal endpoint handler for managing lifecycle, metrics, and exception orchestration.
 */
public abstract class AbstractEndpointHandler<T, Z> extends SimpleChannelInboundHandler<T> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final GatewayPlugin endpoint;
    protected final String flowName;
    protected ITxnManager<Z> txnManager;
    protected Charset requestEncoding;
    protected Charset responseEncoding;
    protected final long maxContentLength;

    public AbstractEndpointHandler(String flowName, GatewayPlugin endpoint, ITxnManager<Z> txnManager,
                                   Charset requestEncoding, Charset responseEncoding, long maxContentLength) {
        this.flowName = flowName;
        this.endpoint = endpoint;
        this.txnManager = txnManager;
        this.requestEncoding = requestEncoding;
        this.responseEncoding = responseEncoding;
        this.maxContentLength = maxContentLength;
    }

    /** Validates received data length against max content limits. */
    protected void handleExceededLength(ChannelHandlerContext ctx, int length) throws FlowException {
        NettyUtils.runWithMdc(ctx.channel(), () -> {
            log.error("Content length {} exceeds limit {}.", length, maxContentLength);
        });
        throw new FlowException(FlowResultStatus.INGRESS_EDGE_REJECT_PAYLOAD_TOO_LARGE, "Payload too large: " + length);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelReadComplete();
    }

    /**
     * Unified exception handler for the network stack.
     * Uses MDC for context tracking and suppresses common network "noise".
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Throwable root = cause.getCause() != null ? cause.getCause() : cause;

        NettyUtils.runWithMdc(ctx.channel(), () -> {
            if (root instanceof DecoderException) {
                log.error("Decoder length error. Closing session. {}: {}", ctx.channel(), root.getMessage());
                ctx.close();
            }
            else if (root instanceof ReadTimeoutException) {
                // Silent handling for expected idle timeouts/zombie connections
                log.debug("Idle connection closed via ReadTimeout. Channel: {}", ctx.channel());
                ctx.close();
            }
            else if (root instanceof IOException) {
                // Suppress standard reset/broken pipe logs as they are routine network events
                String msg = root.getMessage();
                if (msg != null && (msg.contains("Connection reset") || msg.contains("Broken pipe"))) {
                    log.debug("Remote peer abruptly closed connection ({}). Channel: {}", msg, ctx.channel());
                } else {
                    log.warn("IOException occurred for channel {}: {}", ctx.channel(), msg);
                }
                ctx.close();
            }
            else {
                // Log critical/unhandled system errors with full context
                log.error("Unhandled network error for channel {}: {}", ctx.channel(), root.getMessage());
                ctx.close();
            }
        });

        if (this.txnManager != null) {
            this.txnManager.close(ctx.channel());
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.endpoint.getMetricsAggregator().incrementChannelActiveCount();

        String channelId = ctx.channel().id().asShortText();

        // Initialize session-level context for subsequent decoders/handlers
        Map<String, String> contextMap = new HashMap<>();
        contextMap.put(MDCKey.CID.getKey(), channelId);
        contextMap.put(MDCKey.FLOW.getKey(), flowName);

        ctx.channel().attr(NettyMdcConstants.MDC_CONTEXT_KEY).set(contextMap);

        NettyUtils.runWithMdc(ctx.channel(), () -> {
            log.info("Successfully connected channel: {}", ctx.channel());
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);

        NettyUtils.runWithMdc(ctx.channel(), () -> {
            log.info("Disconnected channel: {}", ctx.channel());
            if (this.txnManager != null) {
                this.txnManager.close(ctx.channel());
            }
            this.endpoint.getMetricsAggregator().incrementChannelInActiveCount();
        });
    }
}
