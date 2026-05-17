package io.zefio.gateway.netty.decoder;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.zefio.core.common.util.TimeUtils;
import io.zefio.core.GatewayPlugin;
import io.zefio.gateway.netty.dto.NettyValues;
import io.zefio.gateway.netty.dto.PollingConfig;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base decoder for managing Keep-Alive polling logic.
 * Schedules a background task to send heartbeats and monitors connection health via atomic counters.
 */
public abstract class AbstractPollingDecoder extends AbstractByteToMessageDecoder {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String pollingFormat;
    protected final long pollingDuration;
    protected final int pollingReconnectCount;

    protected final String pollingRequest;
    protected final String pollingResponse;

    protected final AtomicInteger pollingAtomic = new AtomicInteger();
    private volatile ScheduledFuture<?> timeout;

    public AbstractPollingDecoder(String flowName, GatewayPlugin flowNode, NettyValues values, PollingConfig pollingConfig, Charset requestEncoding, Charset responseEncoding) {
        super(flowName, flowNode, values, requestEncoding, responseEncoding);

        this.pollingFormat = pollingConfig.getFormat();
        this.pollingDuration = pollingConfig.getDuration();
        this.pollingReconnectCount = pollingConfig.getReconnectCount();
        this.pollingRequest = pollingConfig.getRequest();
        this.pollingResponse = pollingConfig.getResponse();
    }

    protected boolean isPollingRequest(byte[] msgBytes) {
        if (pollingRequest == null) return false;

        byte[] extracted = extractPollingMessage(msgBytes);
        if (extracted == null) return false;

        String msgStr = new String(extracted, requestEncoding).trim();
        log.debug("Polling request received: [{}]", msgStr);

        if (msgStr.equalsIgnoreCase(pollingRequest)) {
            // Reset counter upon successful polling interaction
            pollingAtomic.set(0);
            return true;
        }
        return false;
    }

    protected void sendPollingResponse(ChannelHandlerContext ctx) {
        if (pollingResponse == null) return;

        // Append timestamp if format is configured
        String resStr = !ObjectUtils.isEmpty(this.pollingFormat)
                ? this.pollingResponse + TimeUtils.timestamp(this.pollingFormat)
                : this.pollingResponse;

        // Delegate framing (Length-header or Delimiter) to implementation classes
        byte[] responseBytes = formatPollingResponse(resStr.getBytes(responseEncoding));

        ChannelFuture future = ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(responseBytes));
        future.awaitUninterruptibly();

        if (future.isSuccess()) {
            pollingAtomic.set(0);
            log.debug("Polling response sent: [{}] to remote: {} | Atomic: {}",
                    new String(responseBytes, responseEncoding), ctx.channel().remoteAddress(), pollingAtomic.get());
        }
    }

    /**
     * Slices the polling message from the raw byte stream based on specific framing rules.
     */
    protected abstract byte[] extractPollingMessage(byte[] rawBytes);

    /**
     * Wraps the heartbeat body with the appropriate framing (e.g., length headers).
     */
    protected abstract byte[] formatPollingResponse(byte[] responseBody);


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        if (timeout != null && !timeout.isDone()) timeout.cancel(false);

        // Start heartbeat monitoring task upon connection establishment
        timeout = ctx.executor().scheduleWithFixedDelay(new PollingObserverTask(ctx), 0, pollingDuration, TimeUnit.MILLISECONDS);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        destroy();
    }

    private final class PollingObserverTask implements Runnable {
        private final ChannelHandlerContext ctx;
        public PollingObserverTask(ChannelHandlerContext ctx) { this.ctx = ctx; }

        @Override
        public void run() {
            if (!ctx.channel().isOpen()) return;

            // Trigger reconnection/timeout event if heartbeat threshold is exceeded
            if (pollingAtomic.incrementAndGet() >= pollingReconnectCount) {
                destroy();
                ctx.fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);
            } else {
                sendPollingResponse(ctx);
            }
        }
    }

    protected void destroy() {
        if (timeout != null) {
            timeout.cancel(false);
            timeout = null;
        }
    }
}
