package io.zefio.core;

import io.zefio.core.telemetry.module.ModuleMetricsAggregator;

/**
 * The base interface for all framework components.
 * Manages the essential lifecycle and provides metadata and metrics
 * collection capabilities for monitoring.
 */
public interface GatewayPlugin {
    String getPluginName();
    String getPluginLabel();
    void initialise() throws Exception;
    void close();
    void refresh();
    String getDescription();

    /** Returns the aggregator responsible for tracking execution counts, latencies, and errors. */
    ModuleMetricsAggregator getMetricsAggregator();
}
