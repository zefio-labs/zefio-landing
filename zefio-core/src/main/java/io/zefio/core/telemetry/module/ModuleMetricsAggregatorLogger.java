package io.zefio.core.telemetry.module;

import io.micrometer.core.instrument.Gauge;
import io.zefio.core.config.monitor.MonitorProperties.ModuleMetricsThreshold;
import io.zefio.core.telemetry.AbstractMonitorLogger;
import io.zefio.core.telemetry.MonitorConstants;
import io.zefio.core.telemetry.MonitorInitContext;

/**
 * Periodically reports aggregated module metrics to the monitoring system.
 * Evaluates performance thresholds (failure rate, execution time) and manages alert states.
 */
public class ModuleMetricsAggregatorLogger extends AbstractMonitorLogger {

    private final ModuleMetricsAggregator metricsAggregator;
    private final ModuleMetricsThreshold threshold;

    private boolean highFailureRateWarned = false;
    private boolean highAvgExecTimeWarned = false;
    private long previousAcceptedCount = 0;
    private volatile double currentTps = 0.0;

    public ModuleMetricsAggregatorLogger(MonitorInitContext monitorInitContext,
                                         ModuleMetricsAggregator metricsAggregator, ModuleMetricsThreshold moduleMetricsThreshold) {
        super(monitorInitContext, moduleMetricsThreshold.getIntervalSeconds());
        this.metricsAggregator = metricsAggregator;
        this.threshold = moduleMetricsThreshold;
    }

    @Override
    protected void bindMetrics() {
        // Accepted event count
        registerMeter(Gauge.builder(MonitorConstants.MODULE_ACCEPTED, this, logger -> logger.metricsAggregator.getPayloadAcceptedCount())
                .tags(this.commonTags)
                .description("Total number of accepted events")
                .register(this.meterRegistry));

        // Failed event count
        registerMeter(Gauge.builder(MonitorConstants.MODULE_FAILED, this, logger -> logger.metricsAggregator.getPayloadFailedCount())
                .tags(this.commonTags)
                .description("Total number of failed events")
                .register(this.meterRegistry));

        // Average execution time (ms)
        registerMeter(Gauge.builder(MonitorConstants.MODULE_EXEC_AVG, this, logger -> logger.metricsAggregator.getExecutionAvg())
                .tags(this.commonTags)
                .register(this.meterRegistry));

        // Maximum execution time (ms)
        registerMeter(Gauge.builder(MonitorConstants.MODULE_EXEC_MAX, this, logger -> logger.metricsAggregator.getExecutionMax())
                .tags(this.commonTags)
                .register(this.meterRegistry));

        // Current Transactions Per Second (TPS)
        registerMeter(Gauge.builder(MonitorConstants.MODULE_TPS, this, logger -> logger.currentTps)
                .tags(this.commonTags)
                .description("Current Transactions Per Second")
                .register(this.meterRegistry));

        // Alert status indicators for monitoring systems
        registerMeter(Gauge.builder(MonitorConstants.MODULE_ALERT_FAILURE_RATE, this, logger -> logger.highFailureRateWarned ? 1.0 : 0.0)
                .tags(this.commonTags)
                .description("Alert status for high failure rate")
                .register(this.meterRegistry));

        registerMeter(Gauge.builder(MonitorConstants.MODULE_ALERT_SLOW_AVG, this, logger -> logger.highAvgExecTimeWarned ? 1.0 : 0.0)
                .tags(this.commonTags)
                .description("Alert status for high average execution time")
                .register(this.meterRegistry));
    }

    @Override
    protected String getMonitorPrefix() {
        return "ModuleMetrics";
    }

    @Override
    protected String createInfoLogMessage() {
        long currentAccepted = metricsAggregator.getPayloadAcceptedCount();
        long failed = metricsAggregator.getPayloadFailedCount();
        double avgExec = metricsAggregator.getExecutionAvg();
        long maxExec = metricsAggregator.getExecutionMax();

        long intervalSec = this.threshold.getIntervalSeconds();
        long delta = currentAccepted - previousAcceptedCount;
        this.currentTps = (intervalSec > 0 && delta > 0) ? (double) delta / intervalSec : 0.0;
        this.previousAcceptedCount = currentAccepted;

        return String.format("Acc=%d Fail=%d FailRate=%.2f%% | TPS=%.1f Avg=%.3fms Max=%dms",
                currentAccepted, failed,
                currentAccepted > 0 ? (failed / (double) currentAccepted) * 100 : 0.00,
                currentTps, avgExec, maxExec);
    }

    @Override
    protected void checkAndPrintWarnings() {
        long failed = metricsAggregator.getPayloadFailedCount();
        long accepted = metricsAggregator.getPayloadAcceptedCount();
        double avgExec = metricsAggregator.getExecutionAvg();

        // 1. High Failure Rate Alert
        final double failureRateThreshold = this.threshold.getFailureRate();
        final double currentFailureRate = accepted > 0 ? failed / (double) accepted : 0.0;

        if (currentFailureRate > failureRateThreshold) {
            if (!highFailureRateWarned) {
                log.warn("{} WARN: High failure rate detected: {}% failed out of {} accepted (Failed={})",
                        getLogTag(), String.format("%.2f", currentFailureRate * 100), accepted, failed);
                highFailureRateWarned = true;
            }
        } else if (highFailureRateWarned) {
            log.info("{} RECOVERY: Failure rate returned to normal: {}%",
                    getLogTag(), String.format("%.2f", currentFailureRate * 100));
            highFailureRateWarned = false;
        }

        // 2. High Average Execution Time Alert
        final int avgExecThreshold = this.threshold.getAvgExecTimeMs();

        if (avgExec > avgExecThreshold) {
            if (!highAvgExecTimeWarned) {
                log.warn("{} WARN: Average execution time high: {}ms > {}ms",
                        getLogTag(), avgExec, avgExecThreshold);
                highAvgExecTimeWarned = true;
            }
        } else if (highAvgExecTimeWarned) {
            log.info("{} RECOVERY: Average execution time returned to normal: {}ms",
                    getLogTag(), avgExec);
            highAvgExecTimeWarned = false;
        }
    }

    @Override
    public void reset() {
        log.info("{} Resetting internal states.", getLogTag());
        this.highFailureRateWarned = false;
        this.highAvgExecTimeWarned = false;
        this.previousAcceptedCount = 0;
        this.currentTps = 0.0;
    }
}
