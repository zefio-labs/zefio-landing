package io.zefio.core.telemetry.queue;

import io.micrometer.core.instrument.Gauge;
import io.zefio.core.config.monitor.MonitorProperties.QueueThreshold;
import io.zefio.core.telemetry.AbstractMonitorLogger;
import io.zefio.core.telemetry.MonitorConstants;
import io.zefio.core.telemetry.MonitorInitContext;
import io.zefio.core.telemetry.provider.IQueueStatusProvider;

/**
 * Periodic monitor logger for internal SEDA queues.
 * Tracks queue size and usage ratios, providing alerts when thresholds are breached.
 */
public class QueueMonitorLogger extends AbstractMonitorLogger {

    private final IQueueStatusProvider statusProvider;
    private final QueueThreshold threshold;

    // Flag to track warning state and prevent redundant alert notifications
    private boolean highQueueUsageWarned = false;

    public QueueMonitorLogger(MonitorInitContext monitorInitContext, IQueueStatusProvider statusProvider,
                              QueueThreshold threshold) {
        super(monitorInitContext, threshold.getIntervalSeconds());
        this.statusProvider = statusProvider;
        this.threshold = threshold;
    }

    @Override
    protected void bindMetrics() {
        // 1. Current depth of the internal queue
        registerMeter(Gauge.builder(MonitorConstants.QUEUE_SIZE, this, logger -> logger.statusProvider.getQueueSize())
                .tags(this.commonTags)
                .description("Current number of events in the internal queue")
                .register(this.meterRegistry));

        // 2. Current queue usage ratio (0.0 ~ 1.0)
        registerMeter(Gauge.builder(MonitorConstants.QUEUE_USAGE_RATIO, this, QueueMonitorLogger::getQueueUsageRatio)
                .tags(this.commonTags)
                .description("Internal queue usage ratio")
                .register(this.meterRegistry));

        // 3. Total capacity of the queue
        registerMeter(Gauge.builder(MonitorConstants.QUEUE_CAPACITY, this, logger -> logger.statusProvider.getQueueCapacity())
                .tags(this.commonTags)
                .register(this.meterRegistry));
    }

    private double getQueueUsageRatio() {
        int capacity = statusProvider.getQueueCapacity();
        if (capacity <= 0) return 0.0;
        return (double) statusProvider.getQueueSize() / capacity;
    }

    @Override protected String getMonitorPrefix() { return "QueueMetrics"; }

    @Override
    protected String createInfoLogMessage() {
        int size = statusProvider.getQueueSize();
        int capacity = statusProvider.getQueueCapacity();
        double usage = (capacity > 0) ? (double) size / capacity * 100 : 0;
        return String.format("Size=%d/%d Usage=%.2f%%", size, capacity, usage);
    }

    @Override
    protected void checkAndPrintWarnings() {
        if (statusProvider.getQueueCapacity() <= 0) return;

        double currentUsageRatio = (double) statusProvider.getQueueSize() / statusProvider.getQueueCapacity();
        double usageThreshold = this.threshold.getUsageRatio();

        if (currentUsageRatio >= usageThreshold) {
            if (!highQueueUsageWarned) {
                log.warn("{} WARN: Internal Queue usage is high: {}% >= {}% (Size={}/Capacity={})",
                        getLogTag(),
                        String.format("%.2f", currentUsageRatio * 100),
                        String.format("%.2f", usageThreshold * 100),
                        statusProvider.getQueueSize(),
                        statusProvider.getQueueCapacity());
                highQueueUsageWarned = true;
            }
        } else {
            // Emits recovery log when queue levels return to normal
            if (highQueueUsageWarned) {
                log.info("{} RECOVERY: Internal Queue usage returned to normal: {}%",
                        getLogTag(), String.format("%.2f", currentUsageRatio * 100));
                highQueueUsageWarned = false;
            }
        }
    }

    @Override
    public void reset() {
        log.info("{} Resetting Queue alert states.", getLogTag());
        this.highQueueUsageWarned = false;
    }
}
