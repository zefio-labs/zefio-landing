package io.zefio.core.telemetry.netty;

import io.micrometer.core.instrument.Gauge;
import io.zefio.core.config.monitor.MonitorProperties.ConnectionPoolThreshold;
import io.zefio.core.telemetry.AbstractMonitorLogger;
import io.zefio.core.telemetry.MonitorConstants;
import io.zefio.core.telemetry.MonitorInitContext;

/**
 * Monitor logger for connection pool resources.
 * Tracks active and idle connections and triggers warnings when usage exceeds
 * defined Upstream resource thresholds.
 */
public class ConnectionPoolMonitorLogger extends AbstractMonitorLogger {

    private final IConnectionPoolStatusProvider poolProvider;
    private final ConnectionPoolThreshold threshold;
    private boolean highPoolUsageWarned = false;

    public ConnectionPoolMonitorLogger(MonitorInitContext monitorInitContext,
                                       IConnectionPoolStatusProvider poolProvider,
                                       ConnectionPoolThreshold threshold) {
        super(monitorInitContext, threshold.getIntervalSeconds());
        this.poolProvider = poolProvider;
        this.threshold = threshold;
    }

    @Override
    protected void bindMetrics() {
        // Current active (borrowed) connections
        registerMeter(Gauge.builder(MonitorConstants.CONNECTION_POOL_ACTIVE, this, logger -> logger.poolProvider.getActiveConnections())
                .tags(this.commonTags).register(this.meterRegistry));

        // Current idle connections in the pool
        registerMeter(Gauge.builder(MonitorConstants.CONNECTION_POOL_IDLE, this, logger -> logger.poolProvider.getIdleConnections())
                .tags(this.commonTags).register(this.meterRegistry));

        // Maximum configured pool size
        registerMeter(Gauge.builder(MonitorConstants.CONNECTION_POOL_MAX, this, logger -> logger.poolProvider.getMaxConnections())
                .tags(this.commonTags).register(this.meterRegistry));

        // Current connection pool usage ratio
        registerMeter(Gauge.builder(MonitorConstants.CONNECTION_POOL_USAGE_RATIO, this, logger -> {
            int max = logger.poolProvider.getMaxConnections();
            return max > 0 ? (double) logger.poolProvider.getActiveConnections() / max : 0.0;
        }).tags(this.commonTags).register(this.meterRegistry));
    }

    @Override
    protected String getMonitorPrefix() { return "ConnectionPool"; }

    @Override
    protected String createInfoLogMessage() {
        int active = poolProvider.getActiveConnections();
        int idle = poolProvider.getIdleConnections();
        int max = poolProvider.getMaxConnections();
        double ratio = max > 0 ? (double) active / max * 100 : 0.0;
        return String.format("Active=%d Idle=%d Max=%d Usage=%.2f%%", active, idle, max, ratio);
    }

    @Override
    protected void checkAndPrintWarnings() {
        int max = poolProvider.getMaxConnections();
        if (max > 0) {
            double usageRatio = (double) poolProvider.getActiveConnections() / max;
            double poolUsageThreshold = this.threshold.getUsageRatio();

            if (usageRatio >= poolUsageThreshold) {
                if (!highPoolUsageWarned) {
                    log.warn("{} WARN: Connection Pool usage is high: {}% >= {}% (Active={}/Max={})",
                            getLogTag(),
                            String.format("%.2f", usageRatio * 100),
                            String.format("%.2f", poolUsageThreshold * 100),
                            poolProvider.getActiveConnections(),
                            max);
                    highPoolUsageWarned = true;
                }
            } else {
                if (highPoolUsageWarned) {
                    log.info("{} RECOVERY: Connection Pool usage returned to normal: {}%",
                            getLogTag(),
                            String.format("%.2f", usageRatio * 100));
                    highPoolUsageWarned = false;
                }
            }
        }
    }

    @Override
    public void reset() {
        log.info("{} Resetting Connection Pool alert states.", getLogTag());
        this.highPoolUsageWarned = false;
    }
}
