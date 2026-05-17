package io.zefio.core;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.base.PluginType;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.util.MDCUtils;
import io.zefio.core.payload.Payload;
import io.zefio.core.telemetry.module.ModuleMetricsAggregator;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Base class for CPU-bound interceptors.
 * Executes business logic synchronously on the current worker thread
 * as it does not involve blocking I/O operations.
 */
public abstract class BaseComputeInterceptor extends BaseGatewayPlugin implements ComputeInterceptor {

    public BaseComputeInterceptor(PluginContext context) {
        super(context, new ModuleMetricsAggregator(PluginType.interceptor, context.getFlowName() + "-" + context.getPluginName()));
    }

    @Override
    public CompletableFuture<Payload> executeAsync(Payload payload, Executor executor) {
        this.metricsAggregator.incrementPayloadReceivedCount();
        long start = System.currentTimeMillis();

        // 1. Current Thread: Ingress Worker
        return CompletableFuture.completedFuture(payload)
                .thenApply(evt -> {
                    MDCUtils.restoreMdc(evt);
                    try {
                        // Execute business logic directly on the worker thread
                        Payload result = process(evt);

                        // Preserve log context within the Payload for potential downstream async transitions
                        result.setMdcContext(MDC.getCopyOfContextMap());
                        return result;

                    } finally {
                        MDC.clear();
                    }
                })
                .whenComplete((result, ex) -> {
                    // Finalize metrics on the worker thread
                    handleMetrics(result, ex, start);
                });
    }

    /**
     * Core business logic implementation for CPU-bound tasks.
     */
    @Override
    public abstract Payload process(Payload payload) throws FlowException;
}
