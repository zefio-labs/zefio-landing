package io.zefio.gateway.netty;

import io.netty.channel.ChannelHandlerContext;
import io.zefio.core.RequestReplyCallback;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.telemetry.module.ModuleMetricsAggregator;

import java.nio.charset.Charset;

/**
 * Bridge between the Flow engine's response and Netty's I/O thread.
 * Ensures the final write-back happens on the correct EventLoop thread.
 */
public abstract class NettyRequestReplyCallback extends RequestReplyCallback {
    protected ChannelHandlerContext ctx;

    public NettyRequestReplyCallback(ModuleMetricsAggregator metricsAggregator, PayloadBuilder ingressBuilder, Charset responseEncoding, ChannelHandlerContext ctx) {
        super(metricsAggregator, ingressBuilder, responseEncoding);
        this.ctx = ctx;
    }

    /**
     * Threading Mechanism:
     * 1. Call Site: The Flow Executor thread (where MDC context is active).
     * 2. Target Site: The Netty EventLoop thread (where the socket write must occur).
     *
     * By using ctx.executor().execute(), we ensure thread safety for Netty's
     * pipeline while the MdcWrapper (managed by the core engine) preserves
     * the transaction context during the hand-off.
     */

    @Override
    public void success(Payload payload) {
        // Switch context to the specific Netty EventLoop thread assigned to this channel
        ctx.executor().execute(() -> {
            super.success(payload); // Executes the final response logic (e.g., formatting and sending)
        });
    }

    @Override
    public void error(Payload payload) {
        // Ensure error responses (e.g., 500 Internal Error) are also sent via the EventLoop
        ctx.executor().execute(() -> {
            super.error(payload);
        });
    }
}
