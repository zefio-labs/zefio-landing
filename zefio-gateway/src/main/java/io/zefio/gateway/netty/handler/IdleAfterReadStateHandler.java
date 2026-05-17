package io.zefio.gateway.netty.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Logic Flow:
 * 1) Client connects.
 * 2) Data received -> channelRead() -> Timer resets.
 * 3) No data received for the specified duration.
 * 4) Scheduled ReaderTimeoutTask executes.
 * 5) Trigger READER_IDLE event; downstream handlers can catch this to close the session.
 */
public class IdleAfterReadStateHandler extends ChannelDuplexHandler {

    private static final Logger log = LoggerFactory.getLogger(IdleAfterReadStateHandler.class);

    private final int readTimeout;
    private final TimeUnit timeUnit;

    private volatile ScheduledFuture<?> timeoutFuture;
    private volatile long lastReadTimestamp;

    public IdleAfterReadStateHandler(int readTimeout, TimeUnit timeUnit) {
        if (readTimeout <= 0) throw new IllegalArgumentException("Timeout must be > 0");
        this.readTimeout = readTimeout;
        this.timeUnit = timeUnit;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // Record timestamp and reset the timeout future upon every data read
        lastReadTimestamp = System.currentTimeMillis();
        resetTimeout(ctx);
        ctx.fireChannelRead(msg);
    }

    private void resetTimeout(ChannelHandlerContext ctx) {
        if (timeoutFuture != null && !timeoutFuture.isDone()) {
            timeoutFuture.cancel(false);
        }

        timeoutFuture = ctx.executor().schedule(new ReaderTimeoutTask(ctx), readTimeout, timeUnit);
    }

    private void cancelTimeout() {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
    }

    private class ReaderTimeoutTask implements Runnable {
        private final ChannelHandlerContext ctx;

        ReaderTimeoutTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            if (!ctx.channel().isOpen()) return;

            long elapsed = System.currentTimeMillis() - lastReadTimestamp;
            if (elapsed >= timeUnit.toMillis(readTimeout)) {
                // Threshold exceeded since the last successful read
                cancelTimeout();
                try {
                    log.debug("[IdleAfterReadStateHandler] Read timeout detected for channel: {}", ctx.channel().remoteAddress());
                    channelIdle(ctx, IdleStateEvent.READER_IDLE_STATE_EVENT);
                } catch (Throwable t) {
                    ctx.fireExceptionCaught(t);
                }
            } else {
                // Not reached yet; reschedule the task with the remaining delay
                long delay = timeUnit.toMillis(readTimeout) - elapsed;
                timeoutFuture = ctx.executor().schedule(this, delay, TimeUnit.MILLISECONDS);
            }
        }
    }

    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        // Downstream handlers (e.g., AbstractEndpointHandler) will handle the closure
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        cancelTimeout();
        ctx.fireChannelInactive();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        cancelTimeout();
    }
}
