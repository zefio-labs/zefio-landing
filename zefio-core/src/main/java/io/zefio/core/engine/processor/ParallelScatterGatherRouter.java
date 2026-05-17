package io.zefio.core.engine.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.GatewayInterceptor;
import io.zefio.core.engine.processor.dto.ScatterGather;
import io.zefio.core.payload.Payload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Implements the Scatter-Gather pattern to execute multiple processing branches in parallel.
 * It manages payload cloning for data isolation, handles execution timeouts via a shared pool,
 * and supports flexible aggregation strategies such as merging results into a unified JSON map.
 */
public class ParallelScatterGatherRouter implements Processor {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final ObjectMapper mapper = new ObjectMapper();
    private final ScatterGather values;

    private final String name;
    private final List<Processor> childSteps;
    private final ScheduledExecutorService sharedScheduledPool;

    public ParallelScatterGatherRouter(String name, List<Processor> childSteps, Map<String, Object> config, ScheduledExecutorService scheduledPool) {
        this.name = name;
        this.childSteps = childSteps;
        this.sharedScheduledPool = scheduledPool;
        this.values = config != null ? mapper.convertValue(config, ScatterGather.class) : new ScatterGather();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void initialise() throws Exception {
        for (Processor step : childSteps) step.initialise();
    }

    @Override
    public CompletableFuture<Payload> executeAsync(Payload payload, Executor flowExecutor) {
        if (childSteps == null || childSteps.isEmpty()) {
            log.debug("[{}] No child steps to execute. Skipping Scatter-Gather.", name);
            return CompletableFuture.completedFuture(payload);
        }

        // 1. Fan-Out: Distribute parallel tasks to worker threads
        List<CompletableFuture<Payload>> futures = childSteps.stream()
                .map(step -> {
                    // Create a perfect deep copy of the payload to ensure thread isolation
                    Payload clonedPayload = payload.copyFactory(payload);

                    return CompletableFuture.supplyAsync(() -> clonedPayload, flowExecutor)
                            .thenCompose(cloned -> step.executeAsync(cloned, flowExecutor));
                })
                .collect(Collectors.toList());

        // 2. Handle Error Policy branching
        CompletableFuture<?>[] waitFutures;
        if (values.getErrorPolicy() == ScatterGather.ErrorPolicy.BEST_EFFORT) {
            // BEST_EFFORT: Wrap failures to prevent the aggregate future from completing prematurely
            waitFutures = futures.stream().map(f -> f.handle((res, ex) -> null)).toArray(CompletableFuture[]::new);
        } else {
            // FAIL_FAST: Propagate the original future (any exception triggers immediate failure)
            waitFutures = futures.toArray(new CompletableFuture[0]);
        }

        CompletableFuture<Void> allOf = CompletableFuture.allOf(waitFutures);
        CompletableFuture<Payload> resultFuture = new CompletableFuture<>();

        allOf.whenComplete((v, ex) -> {
            if (ex != null) {
                resultFuture.completeExceptionally(ex);
            } else {
                resultFuture.complete(aggregateResults(futures, payload));
            }
        });

        // 3. Timeout Scheduling
        long timeoutMillis = this.values.getTimeout();
        ScheduledFuture<?> timeoutTask = this.sharedScheduledPool.schedule(() -> {
            if (!resultFuture.isDone()) {
                log.error("[{}] Scatter-Gather Timeout exceeded ({}ms)", name, timeoutMillis);
                resultFuture.completeExceptionally(new CompletionException(new FlowException(FlowResultStatus.TIMEOUT, "Scatter-Gather Timeout")));
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);

        resultFuture.whenComplete((res, ex) -> timeoutTask.cancel(false));

        return resultFuture.exceptionally(throwable -> {
            if (throwable instanceof CompletionException && throwable.getCause() instanceof FlowException) {
                throw (CompletionException) throwable;
            }
            throw new CompletionException(new FlowException(FlowResultStatus.PIPELINE_EXECUTION_ERROR, "Scatter-Gather Execution Failed: " + throwable.getMessage()));
        });
    }

    private Payload aggregateResults(List<CompletableFuture<Payload>> futures, Payload originalPayload) {
        Map<String, Object> aggregatedMap = new LinkedHashMap<>();

        // 4. Gather and merge results
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<Payload> future = futures.get(i);
            Processor step = childSteps.get(i);

            try {
                Payload resultPayload = future.join();
                if (resultPayload != null) {
                    // Merge response headers and metadata
                    ((Payload) originalPayload).mergeResponse(resultPayload, true);

                    // Aggregation Strategy branching
                    if (values.getAggregationType() == ScatterGather.AggregationType.MAP_MERGE) {
                        byte[] stepBody = resultPayload.getBody();
                        if (stepBody != null && stepBody.length > 0) {
                            Object parsedBody;
                            try {
                                // Prevent JSON escaping by parsing the raw body into a structured node
                                parsedBody = mapper.readTree(stepBody);
                            } catch (Exception parseEx) {
                                // Fallback to plain text if not a valid JSON structure
                                parsedBody = new String(stepBody, resultPayload.getCurrentEncoding());
                            }
                            aggregatedMap.put(step.getName(), parsedBody);
                        } else {
                            aggregatedMap.put(step.getName(), null);
                        }
                    }
                }
            } catch (Exception e) {
                // Handle node failures under the BEST_EFFORT policy
                log.warn("[{}] Node '{}' failed. Ignored due to BEST_EFFORT policy. Error: {}", name, step.getName(), e.getMessage());
                if (values.getAggregationType() == ScatterGather.AggregationType.MAP_MERGE) {
                    aggregatedMap.put(step.getName(), Collections.singletonMap("error", e.getMessage()));
                }
            }
        }

        // 5. Serialize aggregated map for MAP_MERGE strategy
        if (values.getAggregationType() == ScatterGather.AggregationType.MAP_MERGE) {
            try {
                byte[] finalMergedBody = mapper.writeValueAsBytes(aggregatedMap);
                originalPayload.setBody(finalMergedBody);
                log.debug("[{}] MAP_MERGE Aggregation complete. Final Size: {} bytes", name, finalMergedBody.length);
            } catch (Exception e) {
                log.error("[{}] Failed to serialize aggregated map to JSON bytes.", name, e);
            }
        }

        log.debug("[{}] Successfully gathered {} parallel responses.", name, futures.size());
        return originalPayload;
    }

    @Override
    public boolean isBlockingType() {
        return childSteps.stream().anyMatch(Processor::isBlockingType);
    }

    @Override
    public List<GatewayInterceptor> extractFilters() {
        List<GatewayInterceptor> extracted = new java.util.ArrayList<>();
        for (Processor step : childSteps) extracted.addAll(step.extractFilters());
        return extracted;
    }

    @Override
    public void close() {
        for (Processor step : childSteps) step.close();
    }
}
