package io.zefio.core.engine.processor;

import dev.failsafe.Failsafe;
import io.zefio.core.GatewayInterceptor;
import io.zefio.core.payload.Payload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Wraps a single GatewayInterceptor to participate in the asynchronous pipeline execution.
 * It leverages the Failsafe library to provide robust, node-level retry logic while
 * isolating execution from common thread pools to prevent resource pollution.
 */
public class LeafFilterProcessor implements Processor {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final GatewayInterceptor filter;
    private final ScheduledExecutorService failsafePool;

    public LeafFilterProcessor(GatewayInterceptor filter, ScheduledExecutorService failsafePool) {
        this.filter = filter;
        this.failsafePool = failsafePool;
    }

    @Override
    public String getName() {
        return filter.getPluginName();
    }

    @Override
    public void initialise() throws Exception {
        filter.initialise();
    }

    @Override
    public CompletableFuture<Payload> executeAsync(Payload payload, Executor flowExecutor) {
        // Wrap with Failsafe for robust node-level retry resilience
        return Failsafe.with(filter.getRetryPolicy())
                .with(this.failsafePool) // Prevent pollution of the common thread pool
                .onFailure(e -> {
                    // Log the retry count and failure reason at the node level,
                    // but delegate actual error processing to the high-level errorHandler.
                    log.warn("[{}] Node execution completely failed after {} attempts. Error: {}",
                            filter.getPluginName(), e.getAttemptCount(), e.getException().getMessage());
                })
                .getStageAsync(() -> filter.executeAsync(payload, flowExecutor));
    }

    @Override
    public boolean isBlockingType() {
        return filter.isBlockingType();
    }

    @Override
    public List<GatewayInterceptor> extractFilters() {
        return java.util.Collections.singletonList(this.filter);
    }

    @Override
    public void close() {
        filter.close();
    }
}
