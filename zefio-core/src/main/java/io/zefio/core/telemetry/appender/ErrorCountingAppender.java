package io.zefio.core.telemetry.appender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.zefio.core.telemetry.logging.LoggingMonitorLogger;
import lombok.Setter;

/**
 * Logback Appender that filters ERROR level events and delegates them to the LoggingMonitorLogger.
 * Does not write logs to physical storage; its primary purpose is real-time metrics aggregation.
 */
@Setter
public class ErrorCountingAppender extends AppenderBase<ILoggingEvent> {

    /** The monitor logger instance injected via the framework or manual configuration. */
    private LoggingMonitorLogger monitorLogger;

    @Override
    public void doAppend(ILoggingEvent eventObject) {
        if (!isStarted() || monitorLogger == null) {
            return;
        }
        super.doAppend(eventObject);
    }

    /**
     * Filters log events and triggers analysis if the severity is ERROR.
     */
    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLevel() == Level.ERROR) {
            try {
                monitorLogger.handleErrorEvent(event);
            } catch (Exception e) {
                addError("Failed to delegate error event to monitorLogger.", e);
            }
        }
    }
}
