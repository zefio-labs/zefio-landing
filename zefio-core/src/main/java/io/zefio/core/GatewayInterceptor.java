package io.zefio.core;

import dev.failsafe.RetryPolicy;
import io.zefio.core.payload.Payload;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Defines a non-blocking processing node within the flow.
 * The core 'executeAsync' method ensures the thread is never blocked,
 * leveraging the appropriate executor based on the 'isBlockingType' flag.
 */
public interface GatewayInterceptor extends GatewayPlugin {

    /**
     * Executes the interceptor logic asynchronously.
     * @param flowExecutor The CPU-bound executor for downstream processing.
     */
    CompletableFuture<Payload> executeAsync(Payload payload, Executor flowExecutor);

    /** Overrides the default retry behavior for resilient execution. */
    default void setRetryPolicy(RetryPolicy<Payload> policy) { }

    default RetryPolicy<Payload> getRetryPolicy() {
        return RetryPolicy.<Payload>builder().withMaxRetries(0).build();
    }

    /**
     * If true, the engine should execute this on the I/O thread pool
     * to prevent starving the CPU flow pool.
     */
    default boolean isBlockingType() {
        return false;
    }
}
