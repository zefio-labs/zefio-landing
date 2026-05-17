package io.zefio.core.telemetry.logging;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.Appender;
import io.micrometer.core.instrument.Gauge;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.FlowErrorUtils;
import io.zefio.core.config.monitor.MonitorProperties.LoggingMonitorThreshold;
import io.zefio.core.telemetry.AbstractMonitorLogger;
import io.zefio.core.telemetry.MonitorConstants;
import io.zefio.core.telemetry.MonitorInitContext;
import io.zefio.core.telemetry.appender.ErrorCountingAppender;
import io.zefio.core.telemetry.history.InMemoryHistoryManager;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Monitor logger for system logging infrastructure.
 * Tracks error rates, exceptions by location, and Logback AsyncAppender queue health.
 * Implements smart throttling for high-frequency fatal errors.
 */
public class LoggingMonitorLogger extends AbstractMonitorLogger {

    private final LoggingMonitorThreshold threshold;

    /** Throttling cooldown time for fatal logs to prevent log flooding (1 minute). */
    private static final long FATAL_LOG_COOLDOWN_MS = 60000;

    /** Internal state for error throttling by exception type. */
    private static class ErrorThrottleState {
        AtomicLong lastLogTime = new AtomicLong(0);
        AtomicLong suppressedCount = new AtomicLong(0);
    }

    private final Map<String, ErrorThrottleState> fatalThrottleMap = new ConcurrentHashMap<>();

    /** Error counts mapped by location (ClassName.MethodName:LineNumber). */
    private final Map<String, AtomicLong> errorLocationCounts = new ConcurrentHashMap<>();

    /** Error counts mapped by exception status or type. */
    private final Map<String, AtomicLong> exceptionTypeCounts = new ConcurrentHashMap<>();

    private final InMemoryHistoryManager historyManager;
    private final AtomicLong errorCount = new AtomicLong(0);
    private boolean highQueueWarned = false;
    private volatile long previousTotalErrorCount = 0;

    private static final String ASYNC_APPENDER_NAME = "ASYNC_FILE";
    private final AsyncAppender asyncAppender;

    private static final String ERROR_COUNTING_NAME = "ERROR_COUNTING";
    private final ErrorCountingAppender errorCountingAppender;

    public LoggingMonitorLogger(MonitorInitContext monitorInitContext, LoggingMonitorThreshold loggingThreshold, InMemoryHistoryManager historyManager) {
        super(monitorInitContext, loggingThreshold.getIntervalSeconds());
        this.threshold = loggingThreshold;
        this.historyManager = historyManager;

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        // Initialize monitoring targets from Logback context
        this.asyncAppender = findAppender(loggerContext, ASYNC_APPENDER_NAME, AsyncAppender.class);
        this.errorCountingAppender = findAppender(loggerContext, ERROR_COUNTING_NAME, ErrorCountingAppender.class);
    }

    @Override
    protected void bindMetrics() {
        // Total cumulative error count
        registerMeter(Gauge.builder(MonitorConstants.LOGGING_ERRORS_TOTAL, this, logger -> logger.errorCount.get())
                .tags(this.commonTags)
                .description("Total number of ERROR logs encountered")
                .register(this.meterRegistry));

        // AsyncAppender queue usage ratio
        registerMeter(Gauge.builder(MonitorConstants.LOGGING_ASYNC_QUEUE_RATIO, this, LoggingMonitorLogger::getAsyncAppenderQueueRatio)
                .tags(this.commonTags)
                .description("Logback AsyncAppender queue usage ratio")
                .register(this.meterRegistry));

        // Error count within the current monitoring period
        registerMeter(Gauge.builder(MonitorConstants.LOGGING_ASYNC_QUEUE_RATIO, this, logger -> errorCount.get() - previousTotalErrorCount)
                .tags(this.commonTags)
                .register(this.meterRegistry));
    }

    private <T extends Appender<?>> T findAppender(LoggerContext context, String name, Class<T> clazz) {
        ch.qos.logback.classic.Logger rootLogger = context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        Appender<?> appender = rootLogger.getAppender(name);

        if (clazz.isInstance(appender)) {
            log.info("{} Found Appender '{}' for direct status monitoring.", getLogTag(), name);
            return clazz.cast(appender);
        }
        log.error("{} Appender '{}' not found or type mismatch. Expected: {}", getLogTag(), name, clazz.getSimpleName());
        return null;
    }

    public boolean initializeAndStart() {
        if (this.asyncAppender == null || this.errorCountingAppender == null) {
            log.error("{} Infrastructure appenders missing. Monitoring degraded.", getLogTag());
            return false;
        }

        try {
            this.errorCountingAppender.setMonitorLogger(this);
            return true;
        } catch (Exception e) {
            log.error("{} Failed to link ErrorCountingAppender.", getLogTag(), e);
            return false;
        }
    }

    @Override
    public void start() {
        super.start();
        if (this.errorCountingAppender != null && !this.errorCountingAppender.isStarted()) {
            this.errorCountingAppender.start();
        }
        log.info("{} Logging monitor started.", getLogTag());
    }

    /**
     * Processes a log error event, aggregates metrics, and applies throttling for fatal errors.
     */
    public void handleErrorEvent(ILoggingEvent event) {
        String locationKey = "Unknown";
        String exceptionType = "None";
        String detailMessage = event.getFormattedMessage();

        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (!(throwableProxy instanceof ThrowableProxy)) return;

        Throwable rawThrowable = ((ThrowableProxy) throwableProxy).getThrowable();
        FlowException flowEx = FlowErrorUtils.convert(rawThrowable);

        FlowResultStatus status = flowEx.getStatus();
        exceptionType = (status != null) ? status.name() : "UNKNOWN_STATUS";
        detailMessage = (status != null) ? status.getMessage() + " - " + flowEx.getMessage() : flowEx.getMessage();

        // Extract simplified location info
        StackTraceElementProxy[] steps = throwableProxy.getStackTraceElementProxyArray();
        if (steps != null && steps.length > 0) {
            StackTraceElement element = steps[0].getStackTraceElement();
            String fullClassName = element.getClassName();
            String simpleClassName = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
            locationKey = String.format("%s.%s:%d", simpleClassName, element.getMethodName(), element.getLineNumber());
        }

        String severity = FlowErrorUtils.decideSeverity(status, rawThrowable);

        if ("FATAL".equals(severity)) {
            errorCount.incrementAndGet();
            exceptionTypeCounts.computeIfAbsent(exceptionType, k -> new AtomicLong(0)).incrementAndGet();
            errorLocationCounts.computeIfAbsent(locationKey, k -> new AtomicLong(0)).incrementAndGet();

            try {
                this.meterRegistry.counter(MonitorConstants.LOGGING_ERROR_DETAIL,
                        "flow", this.flowName != null ? this.flowName : "global",
                        "exception", exceptionType,
                        "location", locationKey
                ).increment();
            } catch (Exception e) {}

            // Throttling logic for fatal logs
            long now = System.currentTimeMillis();
            ErrorThrottleState state = fatalThrottleMap.computeIfAbsent(exceptionType, k -> new ErrorThrottleState());
            long lastTime = state.lastLogTime.get();

            if (now - lastTime >= FATAL_LOG_COOLDOWN_MS) {
                if (state.lastLogTime.compareAndSet(lastTime, now)) {
                    long suppressed = state.suppressedCount.getAndSet(0);
                    String logMessage;

                    if (suppressed > 0) {
                        logMessage = String.format("[FATAL] %s (%s) at %s. (Suppressed %d occurrences in last %ds)",
                                exceptionType, detailMessage, locationKey, suppressed, (FATAL_LOG_COOLDOWN_MS/1000));
                    } else {
                        logMessage = String.format("[FATAL] %s (%s) at %s", exceptionType, detailMessage, locationKey);
                    }

                    historyManager.addErrorLog(logMessage);
                    log.error(logMessage);
                } else {
                    state.suppressedCount.incrementAndGet();
                }
            } else {
                state.suppressedCount.incrementAndGet();
            }

        } else {
            // Standard ERROR processing without alert metric increment
            String logMessage = String.format("[ERROR] %s (%s) at %s", exceptionType, detailMessage, locationKey);
            historyManager.addErrorLog(logMessage);
            log.warn(logMessage);
        }
    }

    @Override
    protected String getMonitorPrefix() {
        return "LogMetrics";
    }

    @Override
    protected String createInfoLogMessage() {
        long currentTotalCount = errorCount.get();
        long errorsInPeriod = currentTotalCount - previousTotalErrorCount;

        double queueRatio = getAsyncAppenderQueueRatio();
        String queueStatus = (asyncAppender == null) ? "N/A" : String.format("%.2f%%", queueRatio * 100);

        String topLocations = getTopErrorLocations(3);
        String topExceptions = getTopExceptionTypes(3);

        return String.format("ErrorCount(Period)=%d | AsyncQueueUsage=%s | TopLocations=[%s] | TopExceptions=[%s]",
                errorsInPeriod, queueStatus, topLocations, topExceptions);
    }

    @Override
    protected void checkAndPrintWarnings() {
        checkLogbackQueueWarning();
        checkErrorBurstWarning();

        this.previousTotalErrorCount = errorCount.get();
        errorLocationCounts.clear();
        exceptionTypeCounts.clear();
    }

    private String getTopErrorLocations(int limit) {
        return errorLocationCounts.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicLong>comparingByValue(Comparator.comparingLong(AtomicLong::get)).reversed())
                .limit(limit)
                .map(e -> String.format("%s(%d)", e.getKey(), e.getValue().get()))
                .collect(Collectors.joining("; "));
    }

    private String getTopExceptionTypes(int limit) {
        return exceptionTypeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicLong>comparingByValue(Comparator.comparingLong(AtomicLong::get)).reversed())
                .limit(limit)
                .map(e -> String.format("%s(%d)", e.getKey(), e.getValue().get()))
                .collect(Collectors.joining("; "));
    }

    private double getAsyncAppenderQueueRatio() {
        if (asyncAppender == null) return 0.0;

        try {
            int maxCapacity = asyncAppender.getQueueSize();
            int remainingCapacity = asyncAppender.getRemainingCapacity();
            if (maxCapacity > 0) {
                return (double) (maxCapacity - remainingCapacity) / maxCapacity;
            }
        } catch (Exception e) {
            log.error("{} Failed to retrieve Logback queue status.", getLogTag());
        }
        return 0.0;
    }

    private void checkLogbackQueueWarning() {
        if (asyncAppender == null) return;

        final double queueThreshold = this.threshold.getMaxQueueCapacityRatio();
        double currentRatio = getAsyncAppenderQueueRatio();

        if (currentRatio > queueThreshold) {
            if (!highQueueWarned) {
                log.warn("{} WARN: Logback Async Queue High usage: {}% > {}%",
                        getLogTag(), String.format("%.2f", currentRatio * 100), queueThreshold * 100);
                highQueueWarned = true;
            }
        } else if (highQueueWarned) {
            log.info("{} RECOVERY: Logback Async Queue usage returned to normal.", getLogTag());
            highQueueWarned = false;
        }
    }

    private void checkErrorBurstWarning() {
        final int errorThreshold = this.threshold.getErrorBurstRate();
        long errorsInPeriod = errorCount.get() - previousTotalErrorCount;

        if (errorsInPeriod > errorThreshold) {
            log.warn("{} WARN: High Error Rate detected: {} errors/period", getLogTag(), errorsInPeriod);
        } else if (errorsInPeriod > 0) {
            log.warn("{} Errors detected in period: {} count", getLogTag(), errorsInPeriod);
        }
    }

    @Override
    public void reset() {
        log.info("{} Resetting logging metrics and alert states.", getLogTag());
        this.errorCount.set(0);
        this.previousTotalErrorCount = 0;
        this.errorLocationCounts.clear();
        this.exceptionTypeCounts.clear();
        this.highQueueWarned = false;
        this.fatalThrottleMap.clear();

        try {
            io.micrometer.core.instrument.Meter meterToDrop = meterRegistry.find(MonitorConstants.LOGGING_ERROR_DETAIL).meter();
            if (meterToDrop != null) {
                meterRegistry.remove(meterToDrop);
            }
        } catch (Exception e) {
            log.warn("{} Failed to clear Prometheus meters.", getLogTag(), e);
        }
    }

    @Override
    public void shutdown() {
        log.info("{} Shutting down logging monitor.", getLogTag());
        super.shutdown();
        // Appenders are not stopped here to maintain global logging infrastructure integrity during hot-reloads.
    }
}
