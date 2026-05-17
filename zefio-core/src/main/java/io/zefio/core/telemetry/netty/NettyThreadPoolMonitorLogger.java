package io.zefio.core.telemetry.netty;

import io.micrometer.core.instrument.Gauge;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.zefio.core.config.monitor.MonitorProperties.NettyEventLoopThreshold;
import io.zefio.core.telemetry.AbstractMonitorLogger;
import io.zefio.core.telemetry.MonitorConstants;
import io.zefio.core.telemetry.MonitorInitContext;

/**
 * Monitor logger for Netty EventLoopGroup resources.
 * Tracks thread activity, pending tasks, and direct memory usage.
 * Provides detailed diagnostics for individual executors when thresholds are breached.
 */
public class NettyThreadPoolMonitorLogger extends AbstractMonitorLogger {

    private final NettyEventLoopStateTracker tracker;
    private final NettyEventLoopThreshold threshold;

    private boolean highPendingTaskWarned = false;
    private boolean highActiveThreadWarned = false;

    public NettyThreadPoolMonitorLogger(MonitorInitContext monitorInitContext,
                                        NettyEventLoopStateTracker tracker,
                                        NettyEventLoopThreshold threshold) {
        super(monitorInitContext, threshold.getIntervalSeconds());
        this.tracker = tracker;
        this.threshold = threshold;
    }

    @Override
    protected void bindMetrics() {
        // 1. Total number of Netty EventLoop threads
        registerMeter(Gauge.builder(MonitorConstants.NETTY_THREADS_TOTAL, this, logger -> logger.tracker.getTotalThreads())
                .tags(this.commonTags)
                .description("Total number of Netty EventLoop threads")
                .register(this.meterRegistry));

        // 2. Number of active Netty EventLoop threads
        registerMeter(Gauge.builder(MonitorConstants.NETTY_THREADS_ACTIVE, this, logger -> logger.tracker.getActiveThreads())
                .tags(this.commonTags)
                .description("Number of active Netty EventLoop threads")
                .register(this.meterRegistry));

        // 3. Total number of pending tasks across all EventLoops
        registerMeter(Gauge.builder(MonitorConstants.NETTY_PENDING_TASKS, this, logger -> logger.tracker.getTotalPendingTasks())
                .tags(this.commonTags)
                .description("Total number of pending tasks in Netty EventLoops")
                .register(this.meterRegistry));

        // 4. Number of active Netty channels (Ingress/Upstream connections)
        registerMeter(Gauge.builder(MonitorConstants.NETTY_CHANNELS_ACTIVE, this, logger -> logger.tracker.getActiveChannels())
                .tags(this.commonTags)
                .description("Number of active Netty channels (connections)")
                .register(this.meterRegistry));

        // 5. Current used Netty direct memory in bytes
        registerMeter(Gauge.builder(MonitorConstants.NETTY_DIRECT_MEMORY_BYTES, this, logger -> logger.tracker.getUsedDirectMemory())
                .tags(this.commonTags)
                .baseUnit("bytes")
                .description("Netty direct memory usage")
                .register(this.meterRegistry));
    }

    @Override
    protected String getMonitorPrefix() {
        return "NettyEventLoop";
    }

    @Override
    protected String createInfoLogMessage() {
        tracker.refresh();

        int totalThreads = tracker.getTotalThreads();
        int activeThreads = tracker.getActiveThreads();
        int pendingTasks = tracker.getTotalPendingTasks();
        int channels = tracker.getActiveChannels();
        long directMem = tracker.getUsedDirectMemory();

        double directMemMb = directMem / (1024.0 * 1024.0);

        return String.format("TotalThreads=%d ActiveThreads=%d PendingTasks=%d Channels=%d DirectMem=%.2fMB",
                totalThreads, activeThreads, pendingTasks, channels, directMemMb);
    }

    @Override
    protected void checkAndPrintWarnings() {
        int totalThreads = tracker.getTotalThreads();
        int activeThreads = tracker.getActiveThreads();
        int pendingTasks = tracker.getTotalPendingTasks();

        boolean warnTriggered = false;

        // 1. Pending Task Ratio Check
        if (totalThreads > 0) {
            double currentPendingRatio = (double) pendingTasks / totalThreads;
            double pendingThreshold = this.threshold.getPendingTaskRatio();

            if (currentPendingRatio >= pendingThreshold) {
                if (!highPendingTaskWarned) {
                    log.warn("{} WARN: QUEUE SIZE high: pendingTasks={} >= {} x totalThreads={}",
                            getLogTag(), pendingTasks, pendingThreshold, totalThreads);
                    highPendingTaskWarned = true;
                }
                warnTriggered = true;
            } else if (highPendingTaskWarned) {
                log.info("{} RECOVERY: Queue size returned to normal.", getLogTag());
                highPendingTaskWarned = false;
            }
        }

        // 2. Active Thread Ratio Check
        if (totalThreads > 0) {
            double currentActiveRatio = (double) activeThreads / totalThreads;
            double activeThreshold = this.threshold.getActiveThreadRatio();

            if (currentActiveRatio >= activeThreshold) {
                if (!highActiveThreadWarned) {
                    log.warn("{} WARN: ACTIVE THREADS high: activeThreads={} >= {}% of totalThreads={}",
                            getLogTag(), activeThreads, String.format("%.0f", activeThreshold * 100), totalThreads);
                    highActiveThreadWarned = true;
                }
                warnTriggered = true;
            } else if (highActiveThreadWarned) {
                log.info("{} RECOVERY: Active threads ratio returned to normal.", getLogTag());
                highActiveThreadWarned = false;
            }
        }

        // Trigger detailed executor diagnostics if any threshold is breached
        if (warnTriggered) {
            printActiveExecutorDetails();
        }
    }

    /**
     * Iterates through individual executors in the EventLoopGroup and logs
     * those with pending tasks for granular bottleneck identification.
     */
    private void printActiveExecutorDetails() {
        if (tracker.getEventLoopGroup() instanceof MultithreadEventLoopGroup) {
            MultithreadEventLoopGroup mtelg = (MultithreadEventLoopGroup) tracker.getEventLoopGroup();
            StringBuilder activeDetail = new StringBuilder();
            for (EventExecutor executor : mtelg) {
                if (executor instanceof SingleThreadEventExecutor) {
                    SingleThreadEventExecutor ste = (SingleThreadEventExecutor) executor;
                    int p = ste.pendingTasks();
                    if (p > 0) {
                        activeDetail.append(String.format("    %s : pendingTasks=%d%n", executor.toString(), p));
                    }
                }
            }
            if (activeDetail.length() > 0) {
                log.warn("{} Active Executor details:\n{}", getLogTag(), activeDetail.toString());
            }
        }
    }

    @Override
    public void reset() {
        log.info("{} Resetting Netty Thread Pool alert states.", getLogTag());
        this.highPendingTaskWarned = false;
        this.highActiveThreadWarned = false;
    }
}
