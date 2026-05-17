package io.zefio.core.telemetry.provider;

/**
 * Interface for components that support metric reset operations.
 * Used by the MetricsResetScheduler to clear cumulative statistics periodically.
 */
public interface IMetricsResettable {
    /**
     * Resets internal metrics and alert states to their initial values.
     */
    void resetMetrics();
}
