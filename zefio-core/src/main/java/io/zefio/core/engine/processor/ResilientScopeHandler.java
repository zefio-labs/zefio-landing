package io.zefio.core.engine.processor;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.payload.Payload;
import io.zefio.core.config.flow.OnErrorPolicy;
import io.zefio.core.GatewayInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Provides a resilient execution scope within the processing pipeline.
 * It coordinates error handling strategies such as retries and conditional recovery
 * paths (Fallback, Continue, Stop, Throw) to ensure robust processing for both
 * ingress and upstream communication.
 */
public class ResilientScopeHandler implements Processor {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final String name;
    private final List<Processor> childSteps;
    private final List<Processor> fallbackSteps;

    private final OnErrorPolicy onErrorPolicy;
    private final RetryPolicy<Payload> scopeRetryPolicy;
    private final ScheduledExecutorService failsafePool;

    public ResilientScopeHandler(String name, List<Processor> childSteps,
                                 List<Processor> fallbackSteps,
                                 OnErrorPolicy onErrorPolicy,
                                 RetryPolicy<Payload> scopeRetryPolicy,
                                 ScheduledExecutorService failsafePool) {
        this.name = name;
        this.childSteps = childSteps;
        this.fallbackSteps = fallbackSteps;
        this.onErrorPolicy = onErrorPolicy != null ? onErrorPolicy : OnErrorPolicy.THROW;
        this.scopeRetryPolicy = scopeRetryPolicy != null ? scopeRetryPolicy : RetryPolicy.<Payload>builder().withMaxRetries(0).build();
        this.failsafePool = failsafePool;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void initialise() throws Exception {
        for (Processor step : childSteps) step.initialise();
        if (fallbackSteps != null) {
            for (Processor step : fallbackSteps) step.initialise();
        }
    }

    @Override
    public CompletableFuture<Payload> executeAsync(Payload payload, Executor flowExecutor) {
        // Implements asynchronous retry logic using Failsafe combined with CompletableFuture chaining
        return Failsafe.with(scopeRetryPolicy)
                .with(failsafePool)
                .getStageAsync(() -> {
                    // Deep copy the payload for each retry attempt to ensure data isolation
                    Payload clonedForAttempt = payload.copyFactory(payload);

                    // Ensure the ingress response callback is preserved for consistent client communication
                    clonedForAttempt.setCallback(payload.getCallback());

                    return executeChainAsync(childSteps, clonedForAttempt, flowExecutor);
                })
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        Throwable rootCause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
                        // Execute final error routing based on the defined OnErrorPolicy
                        return handleFinalErrorAsync(payload, rootCause, flowExecutor);
                    }
                    return CompletableFuture.completedFuture(result);
                })
                .thenCompose(future -> future); // Flatten the nested CompletableFuture result
    }

    private CompletableFuture<Payload> executeChainAsync(List<Processor> stepsToRun, Payload payload, Executor flowExecutor) {
        if (stepsToRun == null || stepsToRun.isEmpty()) return CompletableFuture.completedFuture(payload);

        CompletableFuture<Payload> pipelineFuture = CompletableFuture.completedFuture(payload);
        for (Processor step : stepsToRun) {
            pipelineFuture = pipelineFuture.thenCompose(currentEvent -> step.executeAsync(currentEvent, flowExecutor));
        }
        return pipelineFuture;
    }

    /**
     * Logic for handling terminal failures according to the OnErrorPolicy strategy.
     */
    private CompletableFuture<Payload> handleFinalErrorAsync(Payload payload, Throwable rootCause, Executor flowExecutor) {
        switch (this.onErrorPolicy) {
            case FALLBACK:
                log.warn("[{}] Error occurred! Executing Plan-B (FALLBACK policy). Cause: {}", name, rootCause.getMessage());
                if (fallbackSteps != null && !fallbackSteps.isEmpty()) {
                    // Inject the error into the payload temporarily for downstream analysis in the fallback chain
                    payload.setThrowable(rootCause);

                    // If fallback chain succeeds, clear the error state to allow the pipeline to proceed normally
                    return executeChainAsync(fallbackSteps, payload, flowExecutor)
                            .thenApply(resEvent -> {
                                resEvent.setThrowable(null);
                                return resEvent;
                            });
                } else {
                    log.warn("[{}] No fallbackSteps defined; treating as CONTINUE.", name);
                    return CompletableFuture.completedFuture(payload);
                }

            case CONTINUE:
                log.warn("[{}] Error occurred! Ignoring error and proceeding (CONTINUE policy).", name);
                // Clear the error state to ignore the failure and maintain flow continuity
                payload.setThrowable(null);
                return CompletableFuture.completedFuture(payload);

            case STOP:
                log.warn("[{}] Error occurred! Stopping remaining flow immediately (STOP policy).", name);
                // Halts subsequent processing by returning the current payload as the final result
                return CompletableFuture.completedFuture(payload);

            case THROW:
            default:
                log.error("[{}] Error occurred! Propagating exception upward (THROW policy). Cause: {}", name, rootCause.getMessage());
                FlowException ex = (rootCause instanceof FlowException) ?
                        (FlowException) rootCause : new FlowException(FlowResultStatus.PIPELINE_EXECUTION_ERROR, rootCause.getMessage());
                CompletableFuture<Payload> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(new CompletionException(ex));
                return failedFuture;
        }
    }

    @Override
    public boolean isBlockingType() {
        boolean mainBlocking = childSteps.stream().anyMatch(Processor::isBlockingType);
        boolean fallbackBlocking = fallbackSteps != null && fallbackSteps.stream().anyMatch(Processor::isBlockingType);
        return mainBlocking || fallbackBlocking;
    }

    @Override
    public List<GatewayInterceptor> extractFilters() {
        List<GatewayInterceptor> extracted = new java.util.ArrayList<>();
        for (Processor step : childSteps) extracted.addAll(step.extractFilters());
        if (fallbackSteps != null) {
            for (Processor step : fallbackSteps) extracted.addAll(step.extractFilters());
        }
        return extracted;
    }

    @Override
    public void close() {
        for (Processor step : childSteps) step.close();
        if (fallbackSteps != null) {
            for (Processor step : fallbackSteps) step.close();
        }
    }
}
