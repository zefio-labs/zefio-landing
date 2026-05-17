package io.zefio.core.schema.dto;

/**
 * Interface for Ingress modules that operate on a fixed time interval (polling).
 */
public interface ScheduledIngressSupport {
    /**
     * Returns the polling interval in milliseconds.
     */
    Long getPollingIntervalMs();
}
