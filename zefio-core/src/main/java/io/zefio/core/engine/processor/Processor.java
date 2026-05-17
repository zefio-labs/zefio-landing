package io.zefio.core.engine.processor;

import io.zefio.core.GatewayInterceptor;
import io.zefio.core.payload.Payload;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Core interface representing a processing node within the engine's pipeline.
 * It defines the contract for initializing components, executing logic asynchronously
 * within the SEDA framework, and identifying blocking operations for thread management.
 */
public interface Processor {
    String getName();

    void initialise() throws Exception;

    /**
     * Executes the node's logic asynchronously and returns the result as a CompletableFuture.
     */
    CompletableFuture<Payload> executeAsync(Payload payload, Executor flowExecutor);

    /**
     * Returns true if any child nodes contain I/O blocking operations,
     * allowing the engine to determine appropriate stage handoffs.
     */
    default boolean isBlockingType() {
        return false;
    }

    /**
     * Recursively traverses the internal tree to extract the list of active
     * GatewayInterceptor instances for monitoring and telemetry aggregation.
     */
    List<GatewayInterceptor> extractFilters();

    /**
     * Resource cleanup invoked during system or flow shutdown.
     */
    void close();
}
