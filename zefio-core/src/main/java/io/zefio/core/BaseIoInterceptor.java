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
 * Base class for interceptors performing blocking I/O operations.
 * It manages the delegation of blocking tasks to a dedicated I/O thread pool
 * to prevent worker thread starvation in the SEDA pipeline.
 */
public abstract class BaseIoInterceptor extends BaseGatewayPlugin implements IoInterceptor {

    public BaseIoInterceptor(PluginContext context) {
        super(context, new ModuleMetricsAggregator(PluginType.interceptor, context.getFlowName() + "-" + context.getPluginName()));
    }

    /**
     * Internal blocking I/O logic to be implemented by child classes.
     */
    protected abstract Payload blockingProcessInternal(Payload payload) throws FlowException;

    /**
     * Executes the I/O task asynchronously by delegating it to the shared I/O pool.
     * Connectors using native async APIs (e.g., Netty) should override this method.
     */
    protected CompletableFuture<Payload> handleIoAsync(Payload payload, Executor flowExecutor) {
        return CompletableFuture.supplyAsync(() -> {
            MDCUtils.restoreMdc(payload);
            try {
                return blockingProcessInternal(payload);
            } finally {
                MDC.clear();
            }
        }, this.sharedIoPool);
    }

    @Override
    public CompletableFuture<Payload> executeAsync(Payload payload, Executor flowExecutor) {
        this.metricsAggregator.incrementPayloadReceivedCount();
        long start = System.currentTimeMillis();

        return CompletableFuture.completedFuture(payload)
                .thenCompose(evt -> {
                    MDCUtils.restoreMdc(evt);
                    try {
                        // Offload blocking I/O to the dedicated sharedIoPool
                        return handleIoAsync(evt, flowExecutor);
                    } finally {
                        // Worker thread is released immediately without waiting for I/O completion
                        MDC.clear();
                    }
                })
                .whenCompleteAsync((result, ex) -> {
                    // Jump back to the flow executor (Worker thread) for metrics and downstream processing
                    handleMetrics(result, ex, start);
                }, flowExecutor);
    }
}
