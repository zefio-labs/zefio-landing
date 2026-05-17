package io.zefio.gateway.netty.chunked;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Manages timeouts for chunked message aggregation.
 * Ensures that partial buffers are released if subsequent chunks do not arrive within the limit.
 */
public class ChunkAggregatorTimeoutManager {
    private static final Logger log = LoggerFactory.getLogger(ChunkAggregatorTimeoutManager.class);

    @Getter
    public static class SessionWrapper implements Delayed {
        private final String channelId;
        private final ByteBuf buffer;
        private long expirationNanoTime;

        public SessionWrapper(String channelId, ByteBuf buffer, long timeoutMillis) {
            this.channelId = channelId;
            this.buffer = buffer;
            refresh(timeoutMillis);
        }

        /**
         * Resets the expiration timer. Should be called on every chunk arrival.
         */
        public void refresh(long timeoutMillis) {
            this.expirationNanoTime = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(expirationNanoTime - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            long diff = getDelay(TimeUnit.NANOSECONDS) - o.getDelay(TimeUnit.NANOSECONDS);
            return diff == 0 ? 0 : (diff < 0 ? -1 : 1);
        }
    }

    private final Map<String, SessionWrapper> sessionMap = new ConcurrentHashMap<>();
    private final DelayQueue<SessionWrapper> timeoutQueue = new DelayQueue<>();
    private final long timeoutMillis;
    private final Thread timeoutThread;

    public ChunkAggregatorTimeoutManager(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;

        this.timeoutThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    SessionWrapper wrapper = timeoutQueue.take();
                    String id = wrapper.getChannelId();

                    // Atomically remove and release if the wrapper is still the current session
                    if (sessionMap.remove(id, wrapper)) {
                        wrapper.getBuffer().release();
                        log.warn("Chunk aggregation timed out for Channel [{}]. Buffer released.", id);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in ChunkAggregator timeout thread", e);
                }
            }
        }, "Zefio-ChunkTimeout-Thread");

        this.timeoutThread.setDaemon(true);
        this.timeoutThread.start();
    }

    /**
     * Retrieves an existing session or creates a new one.
     */
    public SessionWrapper getOrCreate(String channelId) {
        return sessionMap.computeIfAbsent(channelId, id -> {
            SessionWrapper w = new SessionWrapper(id, Unpooled.buffer(), timeoutMillis);
            timeoutQueue.offer(w);
            return w;
        });
    }

    /**
     * Extends the session lifetime.
     */
    public void refresh(String channelId) {
        SessionWrapper wrapper = sessionMap.get(channelId);
        if (wrapper != null) {
            wrapper.refresh(timeoutMillis);
        }
    }

    /**
     * Discards the existing session. Used when a new START chunk arrives unexpectedly.
     */
    public void clearSession(String channelId) {
        SessionWrapper old = sessionMap.remove(channelId);
        if (old != null) {
            timeoutQueue.remove(old);
            old.getBuffer().release();
        }
    }

    /**
     * Removes the session and returns the aggregated buffer upon successful completion.
     */
    public ByteBuf remove(String channelId) {
        SessionWrapper wrapper = sessionMap.remove(channelId);
        if (wrapper != null) {
            timeoutQueue.remove(wrapper);
            return wrapper.getBuffer();
        }
        return null;
    }
}
