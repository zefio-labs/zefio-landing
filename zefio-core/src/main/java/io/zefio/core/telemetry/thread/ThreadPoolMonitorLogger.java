package io.zefio.core.telemetry.thread;

import io.micrometer.core.instrument.Gauge;
import io.zefio.core.config.monitor.MonitorProperties.ThreadPoolThreshold;
import io.zefio.core.telemetry.AbstractMonitorLogger;
import io.zefio.core.telemetry.MonitorConstants;
import io.zefio.core.telemetry.MonitorInitContext;

/**
 * Monitor logger for ThreadPool resources.
 * Binds real-time thread pool metrics to the Micrometer registry and
 * provides automated warning mechanisms based on defined thresholds.
 */
public class ThreadPoolMonitorLogger extends AbstractMonitorLogger {

    private final ThreadPoolStateTracker tracker;
    private final int maxPoolSize;
    private final int queueCapacity;
    private final ThreadPoolThreshold threshold;

    // State flags to ensure warning/recovery logs are emitted only once per state change
    private boolean highQueueRatioWarned = false;
    private boolean highPoolMaxRatioWarned = false;
    private boolean coreExhaustedWarned = false;

    public ThreadPoolMonitorLogger(MonitorInitContext monitorInitContext,
                                   ThreadPoolStateTracker tracker, ThreadPoolThreshold threadPoolThreshold) {
        super(monitorInitContext, threadPoolThreshold.getIntervalSeconds());
        this.tracker = tracker;
        this.maxPoolSize = tracker.getMaxPoolSize();
        this.queueCapacity = tracker.getQueueCapacity();
        this.threshold = threadPoolThreshold;
    }

    @Override
    protected void bindMetrics() {
        // 1. Number of threads actively executing tasks
        registerMeter(Gauge.builder(MonitorConstants.THREAD_POOL_ACTIVE_THREADS, this, logger -> logger.tracker.getActiveCount())
                .tags(this.commonTags)
                .description("Number of threads actively executing tasks")
                .register(this.meterRegistry));

        // 2. Total current number of threads in the pool
        registerMeter(Gauge.builder(MonitorConstants.THREAD_POOL_SIZE, this, logger -> logger.tracker.getPoolSize())
                .tags(this.commonTags)
                .register(this.meterRegistry));

        // 3. Current number of tasks waiting in the queue
        registerMeter(Gauge.builder(MonitorConstants.THREAD_POOL_QUEUE_SIZE, this, logger -> logger.tracker.getQueueSize())
                .tags(this.commonTags)
                .register(this.meterRegistry));

        // 4. Ratio of active threads relative to CorePoolSize
        registerMeter(Gauge.builder(MonitorConstants.THREAD_POOL_CORE_USAGE_RATIO, this, ThreadPoolMonitorLogger::getCoreUsageRatio)
                .tags(this.commonTags)
                .register(this.meterRegistry));

        // 5. Ratio of total pool size relative to MaxPoolSize
        registerMeter(Gauge.builder(MonitorConstants.THREAD_POOL_MAX_USAGE_RATIO, this, ThreadPoolMonitorLogger::getMaxUsageRatio)
                .tags(this.commonTags)
                .register(this.meterRegistry));

        // 6. Registered threshold value for dashboard visualization
        registerMeter(Gauge.builder(MonitorConstants.THREAD_POOL_THRESHOLD_QUEUE, this, logger -> logger.threshold.getQueueCapacityRatio())
                .tags(this.commonTags).register(this.meterRegistry));
    }

    private double getCoreUsageRatio() {
        int core = tracker.getCorePoolSize();
        return core > 0 ? (double) tracker.getActiveCount() / core : 0;
    }

    private double getMaxUsageRatio() {
        return maxPoolSize > 0 ? (double) tracker.getPoolSize() / maxPoolSize : 0;
    }

    @Override
    protected String getMonitorPrefix() {
        return "ThreadPoolMetrics";
    }

    @Override
    protected String createInfoLogMessage() {
        tracker.refresh();

        int corePoolSize = tracker.getCorePoolSize();
        int poolSize = tracker.getPoolSize();
        int activeCount = tracker.getActiveCount();
        int queueSize = tracker.getQueueSize();
        long completed = tracker.getCompletedTaskCount();

        int peakActive = tracker.getPeakActiveCount();
        int peakQueue = tracker.getPeakQueueSize();
        int idleCount = poolSize - activeCount;

        String message = String.format(
                "Pool=%d Active=%d(Peak=%d) Idle=%d Queue=%d(Peak=%d) Completed=%d | Config: Core=%d, Max=%d, QCap=%d",
                poolSize, activeCount, peakActive, idleCount, queueSize, peakQueue,
                completed, corePoolSize, maxPoolSize, queueCapacity);

        // Reset peak values for the next monitoring window
        tracker.resetPeaks();

        return message;
    }

    @Override
    protected void checkAndPrintWarnings() {
        tracker.refresh();

        int corePoolSize = tracker.getCorePoolSize();
        int poolSize = tracker.getPoolSize();
        int activeCount = tracker.getActiveCount();
        int queueSize = tracker.getQueueSize();

        final double queueRatioThreshold = this.threshold.getQueueCapacityRatio();
        final double poolMaxRatioThreshold = this.threshold.getPoolMaxRatio();
        final double coreExhaustRatioThreshold = this.threshold.getCoreExhaustRatio();

        // 1. Queue Depth Monitoring
        if (queueCapacity > 0) {
            double currentQueueRatio = (double) queueSize / queueCapacity;

            if (currentQueueRatio >= queueRatioThreshold) {
                if (!highQueueRatioWarned) {
                    log.warn("{} WARN: QUEUE CAPACITY high: Queue={} ({}%) >= {}% of Capacity={}",
                            getLogTag(),
                            queueSize,
                            String.format("%.2f", currentQueueRatio * 100),
                            String.format("%.2f", queueRatioThreshold * 100),
                            queueCapacity);
                    highQueueRatioWarned = true;
                }
            } else if (highQueueRatioWarned) {
                log.info("{} RECOVERY: Queue capacity ratio returned to normal: {}% < {}%",
                        getLogTag(),
                        String.format("%.2f", currentQueueRatio * 100),
                        String.format("%.2f", queueRatioThreshold * 100));
                highQueueRatioWarned = false;
            }
        } else if (queueCapacity == 0 && queueSize > 0) {
            log.warn("{} FATAL: QUEUE CAPACITY is 0, but Queue Size is {}. Tasks are likely being rejected.", getLogTag(), queueSize);
        }

        // 2. Pool Scaling Monitoring
        if (maxPoolSize > 1) {
            // 2-1. Core Pool Exhaustion Check
            double currentCoreExhaustRatio = (double) activeCount / corePoolSize;

            if (currentCoreExhaustRatio >= coreExhaustRatioThreshold) {
                if (!coreExhaustedWarned) {
                    log.warn("{} WARN: CORE POOL EXHAUSTED: Active={} ({}%) >= {}% of CorePoolSize={}. Scaling may be required.",
                            getLogTag(),
                            activeCount,
                            String.format("%.2f", currentCoreExhaustRatio * 100),
                            String.format("%.2f", coreExhaustRatioThreshold * 100),
                            corePoolSize);
                    coreExhaustedWarned = true;
                }
            } else if (coreExhaustedWarned) {
                log.info("{} RECOVERY: Core pool ratio returned to normal: {}% < {}%",
                        getLogTag(),
                        String.format("%.2f", currentCoreExhaustRatio * 100),
                        String.format("%.2f", coreExhaustRatioThreshold * 100));
                coreExhaustedWarned = false;
            }

            // 2-2. Pool Size Near Max Limit Check
            if (corePoolSize < maxPoolSize) {
                double currentPoolMaxRatio = (double) poolSize / maxPoolSize;

                if (currentPoolMaxRatio >= poolMaxRatioThreshold) {
                    if (!highPoolMaxRatioWarned) {
                        log.warn("{} WARN: POOL SIZE near MAX: PoolSize={} ({}%) >= {}% of MaxPoolSize={}. No more scaling room.",
                                getLogTag(),
                                poolSize,
                                String.format("%.2f", currentPoolMaxRatio * 100),
                                String.format("%.2f", poolMaxRatioThreshold * 100),
                                maxPoolSize);
                        highPoolMaxRatioWarned = true;
                    }
                } else if (highPoolMaxRatioWarned) {
                    log.info("{} RECOVERY: Pool size ratio returned to normal: {}% < {}%",
                            getLogTag(),
                            String.format("%.2f", currentPoolMaxRatio * 100),
                            String.format("%.2f", poolMaxRatioThreshold * 100));
                    highPoolMaxRatioWarned = false;
                }
            }
        } else if (maxPoolSize == 1 && queueSize > 0) {
            log.warn("{} FATAL: MAX POOL SIZE is 1, Queue Size is {}. Tasks may be rejected.", getLogTag(), queueSize);
        }
    }

    @Override
    public void reset() {
        log.info("{} Resetting Thread Pool alert states and peak metrics.", getLogTag());
        this.highQueueRatioWarned = false;
        this.highPoolMaxRatioWarned = false;
        this.coreExhaustedWarned = false;
        this.tracker.resetPeaks();
    }
}
