package io.zefio.gateway.netty.client;

import io.netty.channel.Channel;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.telemetry.netty.IConnectionPoolStatusProvider;
import io.zefio.gateway.netty.dto.PoolConfig;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Connection Pool Manager for Upstream Netty Channels.
 * Optimized for SEDA Stage boundaries to prevent thread pool starvation.
 */
public class NettyChannelManager implements IConnectionPoolStatusProvider {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final ExecutorService sharedIoPool;
    private final AtomicBoolean running;
    private final GenericObjectPool<Channel> objectPool;
    private final PoolConfig poolConfig;
    private final int poolSize;

    private final AtomicLong consecutiveFailures = new AtomicLong(0);

    public NettyChannelManager(PooledObjectFactory<Channel> factory, PoolConfig poolConfig, ExecutorService sharedIoPool, AtomicBoolean running) {
        this.poolConfig = poolConfig;
        this.poolSize = poolConfig.getPoolMaxSize();
        this.sharedIoPool = sharedIoPool;
        this.running = running;

        GenericObjectPoolConfig<Channel> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(poolSize);
        config.setMaxIdle(poolSize);     // Maximum number of connections to keep in idle state
        config.setMinIdle(poolSize);     // Minimum number of connections to maintain in the pool

        // If true, blocks for maxWait; if false, throws NoSuchElementException immediately when exhausted.
        config.setBlockWhenExhausted(true);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);

        // Apply LIFO/FIFO settings from configuration
        config.setLifo(poolConfig.getLifo());

        // Optimization: Reduced wait time to 100-500ms to prevent SEDA stage thread starvation.
        long waitMillis = poolConfig.getPoolMaxWaitMillis() <= 0 ? 500 : poolConfig.getPoolMaxWaitMillis();
        config.setMaxWait(Duration.ofMillis(waitMillis));

        // Evictor Configuration: Periodically check and clean idle resources
        if (poolConfig.getTimeBetweenEvictionRunsDurationMillis() > 0) {
            config.setTestWhileIdle(true);
            config.setTimeBetweenEvictionRuns(Duration.ofMillis(poolConfig.getTimeBetweenEvictionRunsDurationMillis()));
            config.setMinEvictableIdleDuration(Duration.ofMillis(poolConfig.getMinEvictableIdleDurationMillis()));
        }

        this.objectPool = new GenericObjectPool<>(factory, config);

        try {
            // Asynchronously initialize the pool to avoid blocking the main startup thread.
            fillPoolToTargetSize(poolSize);
        } catch (Exception e) {
            // Pool creation failure is treated as a critical infrastructure error.
            throw new FlowException(e, FlowResultStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // --- IConnectionPoolStatusProvider Implementation ---

    @Override
    public int getActiveConnections() {
        return objectPool != null ? objectPool.getNumActive() : 0;
    }

    @Override
    public int getIdleConnections() {
        return objectPool != null ? objectPool.getNumIdle() : 0;
    }

    @Override
    public int getMaxConnections() {
        return poolSize;
    }

    @Override
    public int getWaitQueueSize() {
        return objectPool != null ? objectPool.getNumWaiters() : 0;
    }

    /**
     * Asynchronously refills the pool to the target size.
     * Prevents "Connection Storms" by synchronizing the count check and addition logic.
     */
    private void fillPoolToTargetSize(int targetSize) {
        if (running != null && !running.get()) return;

        sharedIoPool.submit(() -> {
            boolean needMore = false;

            // Synchronization block is kept minimal to protect against race conditions during expansion.
            synchronized (objectPool) {
                int current = objectPool.getNumIdle() + objectPool.getNumActive();
                if (current < targetSize) {
                    try {
                        objectPool.addObject();
                        consecutiveFailures.set(0); // Reset failure counter on success
                        log.info("✅ Channel pool supplemented: {}/{}", current + 1, targetSize);
                    } catch (Exception e) {
                        long fails = consecutiveFailures.incrementAndGet();
                        // Suppress redundant logs; only warn on the first failure of a sequence.
                        if (fails == 1) {
                            log.warn("❌ Channel pool refill failed (further background logs suppressed until recovery): {}", e.getMessage());
                        } else {
                            log.debug("Channel pool refill failure ongoing... (Failure Count: {})", fails);
                        }
                    }

                    // Re-check pool status to determine if further refilling is required.
                    if ((objectPool.getNumIdle() + objectPool.getNumActive()) < targetSize) {
                        needMore = true;
                    }
                }
            }

            // Recursive scheduling performed outside the synchronization block to reduce lock contention.
            if (needMore) {
                sharedIoPool.execute(() -> {
                    try {
                        Thread.sleep(poolConfig.getPoolFillIntervalMillis());
                        fillPoolToTargetSize(targetSize);
                    } catch (InterruptedException ignored) { }
                });
            }
        });
    }

    /**
     * Borrows an available channel from the pool.
     * Uses non-blocking logic with the configured MaxWait duration.
     */
    public Channel borrowChannel() throws FlowException {
        try {
            return objectPool.borrowObject();
        } catch (java.util.NoSuchElementException e) {
            // Resource exhaustion due to queue wait timeout
            throw new FlowException(e, FlowResultStatus.CONNECTION_POOL_EXHAUSTED);
        } catch (Exception e) {
            // General pool errors
            throw new FlowException(e, FlowResultStatus.SYSTEM_BUSY);
        }
    }

    /**
     * Returns a channel to the pool or invalidates it if the state is unhealthy.
     */
    public void returnChannel(Channel ch) {
        if (ch == null) return;

        log.debug("🔻 returnChannel: [ID: {}] isOpen={}, isActive={}, isWritable={}",
                ch.id().asShortText(), ch.isOpen(), ch.isActive(), ch.isWritable());

        try {
            if (ch.isOpen() && ch.isActive()) {
                objectPool.returnObject(ch);
            } else {
                log.warn("⚠️ Invalid channel detected. Invalidating and refilling pool.");
                objectPool.invalidateObject(ch);
                fillPoolToTargetSize(poolSize); // Automatic refill trigger
            }
        } catch (Exception e) {
            log.error("❌ Exception during returnChannel: {}", e.getMessage(), e);
        }
    }

    public void shutdown() {
        consecutiveFailures.set(0);
        try {
            objectPool.clear();
        } catch (Exception e) {
            log.warn("Exception during pool clear: {}", e.getMessage());
        }
        objectPool.close();
    }
}
