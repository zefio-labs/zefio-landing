package io.zefio.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.failsafe.RetryPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import io.zefio.core.beans.FlowSyncBridge;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.util.MDCUtils;
import io.zefio.core.payload.Payload;
import io.zefio.core.telemetry.module.ModuleMetricsAggregator;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Base implementation for all framework plugins (Ingress, Upstream, Interceptors).
 * Provides common utilities for configuration parsing, lifecycle management,
 * and metrics aggregation.
 */
public abstract class BaseGatewayPlugin implements GatewayPlugin {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    protected final PluginContext context;
    protected final String flowName;
    protected final String flowLabel;
    @Getter
    protected final String pluginName;
    @Getter
    protected final String pluginLabel;

    protected final ScheduledExecutorService sharedScheduledPool;
    protected final ExecutorService sharedIoPool;
    protected final MeterRegistry meterRegistry;
    protected final FlowSyncBridge syncBridge;

    protected ModuleMetricsAggregator metricsAggregator;

    @Setter
    protected dev.failsafe.RetryPolicy<Payload> retryPolicy;


    public BaseGatewayPlugin(PluginContext context, ModuleMetricsAggregator metricsAggregator) {
        yamlMapper.findAndRegisterModules();
        yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        yamlMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
        yamlMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

        this.context = context;
        this.flowName = context.getFlowName();
        this.flowLabel = context.getFlowLabel();
        this.pluginName = context.getPluginName();
        this.pluginLabel = context.getPluginLabel();

        this.sharedScheduledPool = context.getSharedScheduledPool();
        this.sharedIoPool = context.getSharedIoPool();
        this.meterRegistry = context.getMeterRegistry();
        this.syncBridge = context.getFlowSyncBridge();
        this.metricsAggregator = metricsAggregator;
    }

    @Override
    public void initialise() throws Exception{
        this.metricsAggregator.start();
        log.info("{} initialised...", this.pluginName);
    }

    @Override
    public void close() {
        this.metricsAggregator.stop();
        log.info("{} closed.", this.pluginName);
    }

    /**
     * Interface for forcing cache eviction during hot-reloads.
     */
    @Override
    public void refresh() { }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof GatewayInterceptor) {
            return this.pluginName.equals(((GatewayInterceptor) obj).getPluginName());
        }
        return false;
    }

    @Override public ModuleMetricsAggregator getMetricsAggregator() { return this.metricsAggregator; }

    /**
     * Determines whether to track the execution time as Remote Time.
     * Defaults to false; should be overridden to return true in BaseUpstream.
     */
    protected boolean isTrackAsRemote() {
        return false;
    }

    /**
     * Common logic for metrics measurement and resource cleanup for all plugins.
     */
    protected void handleMetrics(Payload payload, Throwable ex, long startTime) {
        if (payload != null) {
            MDCUtils.restoreMdc(payload);
        }

        try {
            long elapsed = System.currentTimeMillis() - startTime;
            this.metricsAggregator.addExecutionTime(elapsed);

            if (ex == null) {
                this.metricsAggregator.incrementPayloadAcceptedCount();

                if (payload != null && isTrackAsRemote()) {
                    payload.addRemoteTime(elapsed);
                }
            } else {
                this.metricsAggregator.incrementPayloadFailedCount();
                log.error("[{}] Module execution failed: {}", pluginName, ex.getMessage());
            }
        } finally {
            MDC.clear();
        }
    }

    public RetryPolicy<Payload> getRetryPolicy() {
        if (this.retryPolicy != null) {
            return this.retryPolicy;
        }
        return RetryPolicy.<Payload>builder().withMaxRetries(0).build();
    }
}
