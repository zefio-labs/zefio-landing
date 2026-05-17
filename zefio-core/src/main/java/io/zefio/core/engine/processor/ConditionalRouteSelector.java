package io.zefio.core.engine.processor;

import io.zefio.core.GatewayInterceptor;
import io.zefio.core.engine.processor.dto.SwitchBranch;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.spel.PayloadExpressionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Implements conditional branching logic (Switch pattern) within the pipeline.
 * It evaluates defined branches sequentially using SpEL expressions against the current
 * payload and any existing errors, then executes the matched branch's processing chain.
 */
public class ConditionalRouteSelector implements Processor {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final String name;
    private final List<SwitchBranch> branches;
    private final List<Processor> defaultSteps;

    public ConditionalRouteSelector(String name, List<SwitchBranch> branches, List<Processor> defaultSteps) {
        this.name = name;
        this.branches = branches != null ? branches : new ArrayList<>();
        this.defaultSteps = defaultSteps;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void initialise() throws Exception {
        for (SwitchBranch branch : branches) {
            for (Processor step : branch.getSteps()) step.initialise();
        }
        if (defaultSteps != null) {
            for (Processor step : defaultSteps) step.initialise();
        }
    }

    @Override
    public CompletableFuture<Payload> executeAsync(Payload payload, Executor flowExecutor) {
        List<Processor> selectedSteps = defaultSteps;
        String matchedCondition = "DEFAULT";

        // 1. Evaluate branch conditions (Sequential search)
        for (SwitchBranch branch : branches) {
            try {
                // Evaluates the condition by passing both the payload and any current Throwable (error object)
                Boolean isMatch = PayloadExpressionEvaluator.evaluate(
                        branch.getCondition(),
                        payload,
                        payload.getThrowable(),
                        Boolean.class
                );

                if (Boolean.TRUE.equals(isMatch)) {
                    selectedSteps = branch.getSteps();
                    matchedCondition = branch.getCondition();
                    break; // Select the first matching branch and exit search
                }
            } catch (Exception e) {
                // Defensive design: If a specific SpEL evaluation fails, skip to the next branch instead of crashing
                log.warn("[{}] SpEL Evaluation failed for condition: [{}]. Skipping this branch. Cause: {}",
                        name, branch.getCondition(), e.getMessage());
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("[{}] Routed to branch: [{}]", name, matchedCondition);
        }

        // 2. Execute the selected pipeline using asynchronous chaining
        return executeChainAsync(selectedSteps, payload, flowExecutor);
    }

    /**
     * Sequentially chains the selected steps into an asynchronous execution pipeline.
     */
    private CompletableFuture<Payload> executeChainAsync(List<Processor> stepsToRun, Payload payload, Executor flowExecutor) {
        if (stepsToRun == null || stepsToRun.isEmpty()) {
            return CompletableFuture.completedFuture(payload);
        }
        CompletableFuture<Payload> pipelineFuture = CompletableFuture.completedFuture(payload);
        for (Processor step : stepsToRun) {
            pipelineFuture = pipelineFuture.thenCompose(currentEvent -> step.executeAsync(currentEvent, flowExecutor));
        }
        return pipelineFuture;
    }

    @Override
    public boolean isBlockingType() {
        boolean branchBlocking = branches.stream()
                .flatMap(b -> b.getSteps().stream())
                .anyMatch(Processor::isBlockingType);
        boolean defaultBlocking = defaultSteps != null && defaultSteps.stream().anyMatch(Processor::isBlockingType);
        return branchBlocking || defaultBlocking;
    }

    @Override
    public List<GatewayInterceptor> extractFilters() {
        List<GatewayInterceptor> extracted = new ArrayList<>();
        for (SwitchBranch branch : branches) {
            for (Processor step : branch.getSteps()) extracted.addAll(step.extractFilters());
        }
        if (defaultSteps != null) {
            for (Processor step : defaultSteps) extracted.addAll(step.extractFilters());
        }
        return extracted;
    }

    @Override
    public void close() {
        for (SwitchBranch branch : branches) {
            for (Processor step : branch.getSteps()) step.close();
        }
        if (defaultSteps != null) {
            for (Processor step : defaultSteps) step.close();
        }
    }
}
