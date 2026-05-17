package io.zefio.core.telemetry.thread;

import lombok.Getter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks the real-time state and peak usage metrics of a ThreadPoolTaskExecutor.
 * Captures current pool size, active threads, and queue depth, while maintaining
 * high-water marks (peaks) for resource diagnostics.
 */
public class ThreadPoolStateTracker {

    @Getter
    private final ThreadPoolTaskExecutor executor;

    @Getter
    private final int corePoolSize;
    @Getter
    private final int maxPoolSize;
    @Getter
    private final int queueCapacity;

    private final AtomicInteger poolSize = new AtomicInteger();
    private final AtomicInteger activeCount = new AtomicInteger();
    private final AtomicInteger queueSize = new AtomicInteger();
    private final AtomicLong completedTaskCount = new AtomicLong();

    /** Tracks the maximum number of active threads recorded since the last peak reset. */
    private final AtomicInteger peakActiveCount = new AtomicInteger();
    /** Tracks the maximum queue size recorded since the last peak reset. */
    private final AtomicInteger peakQueueSize = new AtomicInteger();

    public ThreadPoolStateTracker(ThreadPoolTaskExecutor executor) {
        this.executor = executor;
        this.corePoolSize = executor.getCorePoolSize();
        this.maxPoolSize = executor.getMaxPoolSize();
        this.queueCapacity = executor.getQueueCapacity();

        refresh();
    }

    /**
     * Synchronizes the tracker with the current state of the underlying ThreadPoolExecutor.
     * Updates current metrics and adjusts peak values if new maximums are detected.
     */
    public synchronized void refresh() {
        ThreadPoolExecutor tpe = executor.getThreadPoolExecutor();

        int currentPoolSize = executor.getPoolSize();
        int currentActive = executor.getActiveCount();
        int currentQueue = tpe.getQueue().size();

        poolSize.set(currentPoolSize);
        activeCount.set(currentActive);
        queueSize.set(currentQueue);
        completedTaskCount.set(tpe.getCompletedTaskCount());

        // Update high-water marks (peak values) for the current observation period
        peakActiveCount.accumulateAndGet(currentActive, Math::max);
        peakQueueSize.accumulateAndGet(currentQueue, Math::max);
    }

    /**
     * Resets the peak counters to the current active and queue values.
     * Typically called after a monitoring log is emitted to start a new observation window.
     */
    public void resetPeaks() {
        peakActiveCount.set(activeCount.get());
        peakQueueSize.set(queueSize.get());
    }

    public int getPoolSize() { return poolSize.get(); }
    public int getActiveCount() { return activeCount.get(); }
    public int getQueueSize() { return queueSize.get(); }
    public long getCompletedTaskCount() { return completedTaskCount.get(); }

    public int getPeakActiveCount() { return peakActiveCount.get(); }
    public int getPeakQueueSize() { return peakQueueSize.get(); }
}
