package io.zefio.core.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Base class for periodic resource monitoring and telemetry logging.
 * Manages the lifecycle of monitoring tasks within the shared scheduler
 * and ensures proper registration/cleanup of Micrometer meters.
 */
public abstract class AbstractMonitorLogger {

    protected final Logger log;
    protected final String flowName;
    protected final String moduleName;
    protected final String monitorPrefix;
    private final long periodSeconds;

    protected final MeterRegistry meterRegistry;
    protected final List<Tag> commonTags;

    private final ScheduledExecutorService sharedScheduler;
    private ScheduledFuture<?> monitorTaskFuture;

    /** Tracks all meters registered by this logger for clean removal during shutdown. */
    private final List<Meter> registeredMeters = new ArrayList<>();

    /**
     * Refreshes internal state and generates an informational log message.
     */
    protected abstract String createInfoLogMessage();

    /**
     * Evaluates thresholds and prints warning logs if resources exceed defined limits.
     */
    protected abstract void checkAndPrintWarnings();

    /**
     * Returns the prefix for the specific monitoring target (e.g., "ThreadPool", "Netty").
     */
    protected abstract String getMonitorPrefix();

    /**
     * Hook for child classes to bind specific Micrometer metrics.
     */
    protected abstract void bindMetrics();

    public AbstractMonitorLogger(MonitorInitContext monitorInitContext, long periodSeconds) {
        this.flowName = monitorInitContext.getFlowName();
        this.moduleName = monitorInitContext.getModuleName();
        this.monitorPrefix = getMonitorPrefix();
        this.sharedScheduler = monitorInitContext.getSharedScheduler();
        this.periodSeconds = periodSeconds;
        this.meterRegistry = monitorInitContext.getMeterRegistry();

        String flowLabel = monitorInitContext.getFlowLabel();
        String moduleLabel = monitorInitContext.getModuleLabel();

        // Initialize common tags for Prometheus/Micrometer metrics
        this.commonTags = new ArrayList<>();
        this.commonTags.add(Tag.of("flowName", flowName != null ? flowName : "Global"));
        this.commonTags.add(Tag.of("flowLabel", (flowLabel != null && !flowLabel.isEmpty()) ? flowLabel : (flowName != null ? flowName : "Global")));

        if (moduleName != null && !moduleName.trim().isEmpty()) {
            this.commonTags.add(Tag.of("moduleName", moduleName));
            this.commonTags.add(Tag.of("moduleLabel", (moduleLabel != null && !moduleLabel.isEmpty()) ? moduleLabel : moduleName));
        }

        this.commonTags.add(Tag.of("type", monitorPrefix));

        // Direct all monitoring logs to the unified 'sys.monitor' logger
        this.log = LoggerFactory.getLogger("sys.monitor");

        bindMetrics();
    }

    /**
     * Registers a meter and tracks it for subsequent cleanup during shutdown.
     */
    protected <T extends Meter> T registerMeter(T meter) {
        if (meter != null) {
            this.registeredMeters.add(meter);
        }
        return meter;
    }

    protected String getLogTag() {
        if (this.moduleName != null && !this.moduleName.trim().isEmpty()) {
            return String.format("[%s-%s-%s]", this.monitorPrefix, this.flowName, this.moduleName);
        } else {
            return String.format("[%s-%s]", this.monitorPrefix, this.flowName);
        }
    }

    /**
     * Starts the periodic monitoring task using the shared scheduler pool.
     */
    public void start() {
        if (this.sharedScheduler == null || this.sharedScheduler.isShutdown() || this.sharedScheduler.isTerminated()) {
            log.warn("{} Cannot register monitoring task: Shared Scheduler Pool is unavailable.", getLogTag());
            return;
        }

        if (this.monitorTaskFuture == null || this.monitorTaskFuture.isDone()) {
            this.monitorTaskFuture = this.sharedScheduler.scheduleAtFixedRate(
                    this::logStatus, this.periodSeconds, this.periodSeconds, TimeUnit.SECONDS
            );
            log.info("{} Monitoring task registered to Shared Scheduler.", getLogTag());
        }
    }

    private void logStatus() {
        if (log.isInfoEnabled()) {
            log.info("{} {}", getLogTag(), createInfoLogMessage());
        }

        if (log.isWarnEnabled()) {
            checkAndPrintWarnings();
        }
    }

    /**
     * Resets cumulative metrics such as counters or high-water marks.
     */
    public abstract void reset();

    /**
     * Cancels scheduled tasks and removes all registered meters from the registry.
     * Prevents memory leaks and metric duplication during flow reloads or hot-deployments.
     */
    public void shutdown() {
        if (this.monitorTaskFuture != null) {
            this.monitorTaskFuture.cancel(true);
            this.monitorTaskFuture = null;
        }

        // Remove all meters associated with this logger from the Micrometer registry
        for (Meter meter : registeredMeters) {
            if (meter != null && meter.getId() != null) {
                meterRegistry.remove(meter);
            }
        }
        registeredMeters.clear();
        log.info("{} Logger shutdown complete.", getLogTag());
    }
}
