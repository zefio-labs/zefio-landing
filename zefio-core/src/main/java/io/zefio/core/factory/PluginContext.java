package io.zefio.core.factory;

import io.micrometer.core.instrument.MeterRegistry;
import io.zefio.core.beans.FlowSyncBridge;
import io.zefio.core.payload.ExchangePattern;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A context object provided to plugins during their instantiation.
 * It contains configuration parameters, metadata, and shared system resources
 * such as thread pools and monitoring registries.
 */
@Getter
@Builder
public class PluginContext {
    private final String flowName;
    private final String flowLabel;
    private final String pluginName;
    private final String pluginLabel;
    private final String telegramName;
    private final Map<String, Object> context;
    private final ExchangePattern exchangePattern;

    private final ScheduledExecutorService sharedScheduledPool;
    private final ExecutorService sharedIoPool;

    private final MeterRegistry meterRegistry;
    private final FlowSyncBridge flowSyncBridge;
}
