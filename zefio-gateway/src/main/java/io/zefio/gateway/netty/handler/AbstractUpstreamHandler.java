package io.zefio.gateway.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.schema.dto.UpstreamValues;
import io.zefio.core.Upstream;
import io.zefio.gateway.netty.dto.ClientHandlerContext;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Base handler for Upstream communication.
 * Manages the transition of MDC contexts between the Netty EventLoop and transaction-specific promises.
 *
 * @param <T> The physical message type (e.g., ByteBuf or FullHttpResponse)
 * @param <Payload> The internal Payload/Transaction type
 */
public abstract class AbstractUpstreamHandler<T, Payload> extends AbstractEndpointHandler<T, Payload> {

    protected final Upstream upstream;
    protected final ClientHandlerContext<Payload> context;

    public AbstractUpstreamHandler(ClientHandlerContext<Payload> context) {
        super(context.getFlowName(),
                context.getUpstream(),
                context.getTxnManager(),
                ((UpstreamValues)context.getValues()).getRequestEncoding(),
                ((UpstreamValues)context.getValues()).getResponseEncoding(),
                context.getNettyValues().getMaxContentLength());

        this.upstream = context.getUpstream();
        this.context = context;
    }

    /**
     * Orchestrates the MDC lifecycle for Upstream reads.
     * Backs up the current thread's MDC, allows the implementation to process the response
     * (where the TxnManager will likely inject transaction-specific MDC),
     * and restores the original state in the finally block.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, T msg) throws Exception {
        // 1. Back up the existing MDC context of the current Netty EventLoop thread.
        Map<String, String> originalMdcContext = MDC.getCopyOfContextMap();

        // Clear the MDC for safety before entering the specific transaction logic.
        // It is assumed the TxnManager will restore the relevant transaction context.
        MDC.clear();

        try {
            // 2. Invoke handleRead0.
            // Inside this method, the TxnManager.complete() logic typically triggers,
            // which restores the transaction-specific MDC from the Promise to the current thread.
            this.handleRead0(ctx, msg);
        } catch (Exception e) {
            // Propagate exceptions to the pipeline's exceptionCaught mechanism
            ctx.fireExceptionCaught(e);
        } finally {
            // 3. MDC Cleanup: After handleRead0 execution is complete,
            // restore the original EventLoop MDC to prevent context bleeding.
            if (originalMdcContext != null) {
                MDC.setContextMap(originalMdcContext);
            } else {
                MDC.clear();
            }
        }
    }

    /**
     * Concrete implementation for processing the received Upstream message.
     * Implementation should typically involve notifying the ITxnManager.
     */
    protected abstract void handleRead0(ChannelHandlerContext ctx, T msg) throws Exception;
}
