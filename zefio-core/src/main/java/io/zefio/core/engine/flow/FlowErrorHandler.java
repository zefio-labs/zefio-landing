package io.zefio.core.engine.flow;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.FlowErrorUtils;
import io.zefio.core.Ingress;
import io.zefio.core.engine.policy.ExceptionPolicyManager;
import io.zefio.core.engine.processor.Processor;
import io.zefio.core.util.MDCUtils;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ResponseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Orchestrates error handling for message flows.
 * It identifies appropriate error pipelines based on exception types, manages
 * asynchronous execution of error steps, and ensures the final response is delivered
 * through the ingress callback.
 */
public class FlowErrorHandler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final String flowName;
    private final Map<String, List<Processor>> errorPipelines;
    private final Ingress ingress;
    private final ExceptionPolicyManager policyManager;

    public FlowErrorHandler(FlowInitContext ctx) {
        this.flowName = ctx.getFlowName();
        this.errorPipelines = ctx.getErrorPipelines();
        this.ingress = ctx.getIngress();
        this.policyManager = ctx.getPolicyManager();
    }

    /**
     * Executes common error handling logic.
     * @param stageExecutor The thread pool of the current stage where the error occurred (used as fallback).
     */
    public void handleError(Throwable ex, Payload payload, ResponseListener callback, int attemptCount, Executor stageExecutor) {
        // Restore the original transaction's MDC to the current thread.
        MDCUtils.restoreMdc(payload);

        try {
            payload.setThrowable(ex);

            // Business-level errors are logged to be collected by monitoring dashboards.
            log.error("[{}] Stage execution failed: {}", flowName, ex.getMessage(), ex);

            // Log a warning only if the flow failed after multiple retries.
            if (attemptCount > 1) {
                log.warn("[{}] Flow failed after {} retries. Reason: {}", flowName, attemptCount, FlowErrorUtils.getErrorMessage(ex));
            }

            // If no response is needed per policy (e.g., connection already closed), terminate here.
            FlowResultStatus status = FlowErrorUtils.convert(ex).getStatus();
            if (!policyManager.shouldReply(status)) {
                log.info("[{}] Skip error response (Policy: No Reply). Reason: {}", flowName, status.getMessage());
                return;
            }

            // Use the current thread (Direct Execution) if the thread pool is exhausted (SYSTEM_BUSY) to prevent deadlocks.
            Executor fallbackExecutor = (status == FlowResultStatus.SYSTEM_BUSY || ex instanceof RejectedExecutionException)
                    ? Runnable::run : stageExecutor;

            // 1. Identify the appropriate error pipeline (Strategy Selection).
            List<Processor> matchedPipeline = findMatchingPipeline(ex);

            if (matchedPipeline != null && !matchedPipeline.isEmpty()) {
                // 2-A. Execute the error pipeline steps sequentially in an asynchronous manner.
                executePipelineAsync(matchedPipeline, payload, fallbackExecutor)
                        .whenComplete((res, ex2) -> {
                            if (ex2 != null) {
                                log.warn("[{}] Error pipeline failed mid-execution: {}", flowName, ex2.getMessage());
                            }
                            // Deliver final response after all error steps are completed.
                            executeFinalCallback(payload, callback);
                        });
            } else {
                // 2-B. Directly trigger callback if no matching error pipeline is defined.
                log.debug("[{}] No matching error pipeline found. Directing to callback.", flowName);
                executeFinalCallback(payload, callback);
            }
        } finally {
            // Clear current thread's MDC as logging and async delegation are complete.
            MDC.clear();
        }
    }

    /**
     * Routing logic based on exception type or error code.
     */
    private List<Processor> findMatchingPipeline(Throwable ex) {
        if (errorPipelines == null || errorPipelines.isEmpty()) return null;

        String targetErrorCode = (ex instanceof FlowException) ?
                ((FlowException) ex).getStatus().name() : ex.getClass().getSimpleName();

        for (Map.Entry<String, List<Processor>> entry : errorPipelines.entrySet()) {
            String declaredType = entry.getKey();
            if ("ANY".equalsIgnoreCase(declaredType) || declaredType.equalsIgnoreCase(targetErrorCode)) {
                log.info("[{}] Error [{}] matched with pipeline type: [{}]", flowName, targetErrorCode, declaredType);
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Asynchronously chains and executes a list of processors.
     */
    private CompletableFuture<Payload> executePipelineAsync(List<Processor> pipeline, Payload payload, Executor executor) {
        CompletableFuture<Payload> future = CompletableFuture.completedFuture(payload);
        for (Processor step : pipeline) {
            future = future.thenCompose(evt -> step.executeAsync(evt, executor));
        }
        return future;
    }

    /**
     * Handles final client callback processing based on the Ingress configuration.
     */
    private void executeFinalCallback(Payload payload, ResponseListener callback) {
        if (callback == null) return;

        // Two-Way: Sends error details to the client.
        // One-Way: Terminates the transaction flow.
        callback.error(payload);

        if (!ingress.isTwoWay()) {
            log.debug("[{}] One-way transaction flow terminated after error handling.", flowName);
        }
    }
}
