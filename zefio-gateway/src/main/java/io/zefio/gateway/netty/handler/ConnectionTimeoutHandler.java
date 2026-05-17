package io.zefio.gateway.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handler for enforcing an absolute connection timeout (Time-to-Live).
 * Unlike idle timeouts, this timer is started once at channel activation and never resets.
 *
 * | Handler                   | Metric           | Reset on Data? | Pros                      | Cons                                  |
 * | ------------------------- | ---------------- | -------------- | ------------------------- | ------------------------------------- |
 * | ConnectionTimeoutHandler  | Total Duration   | No             | Predictable TTL           | Terminates even if active/slow        |
 * | IdleAfterReadStateHandler | Inactivity       | Yes            | Dynamic idle harvesting   | Risk of closure during slow chunking  |
 */
public class ConnectionTimeoutHandler extends ChannelInboundHandlerAdapter {

    private final long timeoutMillis;
    private final TimeUnit unit;
    private ScheduledFuture<?> timeoutFuture;

    public ConnectionTimeoutHandler(long timeout, TimeUnit unit) {
        if (timeout <= 0) throw new IllegalArgumentException("Timeout must be > 0");
        this.timeoutMillis = unit.toMillis(timeout);
        this.unit = unit;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // Start the absolute timer upon channel activation
        timeoutFuture = ctx.executor().schedule(() -> {
            if (ctx.channel().isOpen()) {
                ctx.close();
                System.out.println("[ConnectionTimeoutHandler] Absolute timeout reached. Closing connection: " + ctx.channel());
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);

        ctx.fireChannelActive();
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

    private void cancelTimeout() {
        if (timeoutFuture != null && !timeoutFuture.isDone()) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
    }
}
