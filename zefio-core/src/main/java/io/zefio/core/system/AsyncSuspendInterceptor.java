package io.zefio.core.system;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.base.PluginType;
import io.zefio.core.common.util.FlowErrorUtils;
import io.zefio.core.BaseGatewayPlugin;
import io.zefio.core.GatewayInterceptor;
import io.zefio.core.system.dto.AsyncSuspendInterceptorValues;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.telemetry.module.ModuleMetricsAggregator;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.*;

/**
 * Interceptor responsible for suspending the current flow execution.
 * It registers a correlation key with the FlowSyncBridge and waits for an asynchronous response non-blockingly.
 */
public class AsyncSuspendInterceptor extends BaseGatewayPlugin implements GatewayInterceptor {

    private final AsyncSuspendInterceptorValues values;

    public AsyncSuspendInterceptor(PluginContext context) {
        super(context, new ModuleMetricsAggregator(PluginType.interceptor, context.getFlowName() + "-" + context.getPluginName()));
        this.values = yamlMapper.convertValue(context.getContext(), AsyncSuspendInterceptorValues.class);
    }

    @Override
    public String getDescription() {
        String keySource = StringUtils.isNotBlank(values.getBridgeKeyProperty()) ?
                "Property[" + values.getBridgeKeyProperty() + "]" : "Default Transaction ID (TrxID)";

        return String.format("Suspends execution to wait for an asynchronous response using %s as the correlation key. " +
                        "(Defaults to Transaction ID (TrxID) if bridgeKeyProperty is not configured. Timeout is set to %d ms.)",
                keySource, values.getTimeout());
    }

    /**
     * Enabling this allows the base AbstractModule to automatically track this suspension duration as Remote Time.
     */
    @Override
    protected boolean isTrackAsRemote() {
        return true;
    }

    @Override
    public CompletableFuture<Payload> executeAsync(Payload payload, Executor flowExecutor) {
        this.metricsAggregator.incrementPayloadReceivedCount();
        long start = System.currentTimeMillis();

        try {
            String key = extractKey(payload);

            CompletableFuture<Payload> future = this.syncBridge.register(key);
            log.debug("[FlowSyncBridge] Started async wait (non-blocking, Remote Time tracking initiated) - Key: [{}], Timeout: {}ms", key, values.getTimeout());

            ScheduledFuture<?> timeoutTask = this.sharedScheduledPool.schedule(() -> {
                future.completeExceptionally(new TimeoutException("Async response wait timed out"));
            }, values.getTimeout(), TimeUnit.MILLISECONDS);

            return future
                    // 1. Finally block: Cancel the timeout task and prevent memory leaks regardless of success or failure
                    .whenComplete((res, ex) -> {
                        timeoutTask.cancel(false);
                        this.syncBridge.remove(key);
                    })

                    // 2. Success flow: Merge the incoming response payload into the current context
                    .thenApplyAsync(responseEvent -> {
                        payload.mergeResponse(responseEvent, false);
                        return payload;
                    }, flowExecutor)

                    .whenComplete((result, ex) -> {
                        // Trigger handleMetrics from the parent class to accumulate Remote Time automatically
                        handleMetrics(result, ex, start);
                    })

                    // 3. Failure flow: Map exceptions to standard FlowException
                    .exceptionally(ex -> {
                        Throwable rootCause = FlowErrorUtils.unwrap(ex);
                        FlowException finalException;

                        if (rootCause instanceof TimeoutException) {
                            finalException = new FlowException(rootCause, FlowResultStatus.TIMEOUT);
                        } else if (rootCause instanceof FlowException) {
                            finalException = (FlowException) rootCause;
                        } else {
                            finalException = new FlowException(rootCause, FlowResultStatus.ASYNC_EXECUTION_ERROR);
                        }

                        // Wrap the final exception according to framework standards and propagate it to the core engine
                        throw new CompletionException(finalException);
                    });

        } catch (Exception e) {
            Throwable rootCause = FlowErrorUtils.unwrap(e);
            FlowException finalException = (rootCause instanceof FlowException) ?
                    (FlowException) rootCause : new FlowException(rootCause, FlowResultStatus.ASYNC_EXECUTION_ERROR);

            handleMetrics(payload, finalException, start);

            CompletableFuture<Payload> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(finalException);
            return failedFuture;
        }
    }

    private String extractKey(Payload payload) throws FlowException {
        String key = payload.getTrxID();
        if (StringUtils.isNotBlank(values.getBridgeKeyProperty())) {
            Object customKeyObj = payload.getHeader(values.getBridgeKeyProperty());
            if (customKeyObj != null && StringUtils.isNotBlank(customKeyObj.toString())) {
                key = customKeyObj.toString().trim();
            } else {
                throw new FlowException(FlowResultStatus.NOT_CORRELATION_KEY, "Bridge key property not found in payload headers.");
            }
        }
        if (StringUtils.isBlank(key)) {
            throw new FlowException(FlowResultStatus.NOT_CORRELATION_KEY, "Correlation key for matching is blank.");
        }
        return key;
    }
}
