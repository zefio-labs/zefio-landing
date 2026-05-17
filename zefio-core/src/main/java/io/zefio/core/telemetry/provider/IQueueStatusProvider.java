package io.zefio.core.telemetry.provider;

/**
 * Provider interface for retrieving internal queue status.
 * Provides current size and total capacity for monitoring usage ratios.
 */
public interface IQueueStatusProvider {
    int getQueueSize();
    int getQueueCapacity();
}
