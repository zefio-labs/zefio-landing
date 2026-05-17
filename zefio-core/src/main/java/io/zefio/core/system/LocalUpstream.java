package io.zefio.core.system;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.FlowErrorUtils;
import io.zefio.core.BaseUpstream;
import io.zefio.core.PipelineService;
import io.zefio.core.Ingress;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.engine.flow.FlowService;
import io.zefio.core.system.dto.LocalUpstreamValues;
import io.zefio.core.engine.registry.RouteDefinitionRegistry;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ResponseListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes an internal call to another Zefio flow within the same JVM instance.
 * Capable of both One-Way (Fire-and-Forget) and Two-Way (Request-Reply) interactions.
 */
public class LocalUpstream extends BaseUpstream {

    private final LocalUpstreamValues values;

    public LocalUpstream(PluginContext context) {
        super(context);
        this.values = yamlMapper.convertValue(context.getContext(), LocalUpstreamValues.class);
    }

    @Override
    public String getDescription() {
        return "Internal Flow Call -> " + values.getTargetFlow() + " (" + (isTwoWay() ? "2-Way" : "1-Way") + ")";
    }

    /**
     * Prevents the main execution event from tracking this entire internal execution as Remote Time.
     */
    @Override
    protected boolean isTrackAsRemote() {
        return false;
    }

    /**
     * Optimization: Internal flow calls do not require physical byte specification finalization
     * (e.g., encoding conversion), so the parent's transformation logic is intentionally bypassed.
     */
    @Override
    protected void finalizePayloadBeforeSend(Payload payload) {
        // Do Nothing - Pass the object as is for internal invocations
    }

    @Override
    public Payload blockingProcessInternal(Payload payload) throws FlowException {
        return callInternalFlow(values.getTargetFlow(), payload, values.getTimeoutOrDefault());
    }

    protected Payload callInternalFlow(String targetName, Payload payload, long timeout) throws FlowException {
        // 1. Locate the target flow
        long startTime = System.currentTimeMillis();
        log.debug("Preparing to call target flow [{}] (Mode: {})", targetName, isTwoWay() ? "TwoWay" : "OneWay");

        PipelineService targetFlow = RouteDefinitionRegistry.getFlow(targetName);
        if (targetFlow == null) {
            throw new FlowException(FlowResultStatus.SERVICE_HANDLER_NOT_FOUND, "Target flow not found: " + targetName);
        }

        Payload clonedPayload = payload.copyFactory(payload);

        ResponseListener callerCallback = null;
        CompletableFuture<Payload> future;

        if (isTwoWay()) {
            future = new CompletableFuture<>();
            callerCallback = new ResponseListener() {
                @Override public void success(Payload res) { future.complete(res); }
                @Override public void error(Payload err) { future.completeExceptionally(err.getThrowable()); }
            };
        } else {
            future = null;
            callerCallback = new ResponseListener() {
                @Override public void success(Payload e) {}
                @Override public void error(Payload e) {
                    // The target flow has already logged the error, so we simply log a debug trace here.
                    log.debug("Target flow finished with error, but already logged by target processor.");
                }
            };
        }

        // Route through the target flow's Ingress instead of pushing directly to the internal queue.
        if (targetFlow instanceof FlowService) {
            Ingress targetIngress = ((FlowService) targetFlow).getIngress();

            if (targetIngress instanceof LocalIngress) {
                // Inject via the gatekeeper to ensure the target flow outputs [STAT] logs properly
                ((LocalIngress) targetIngress).inject(clonedPayload, callerCallback);
            } else {
                // Fallback direct dispatch
                clonedPayload.setCallback(callerCallback);
                targetFlow.dispatch(clonedPayload);
            }
        } else {
            clonedPayload.setCallback(callerCallback);
            targetFlow.dispatch(clonedPayload);
        }

        if (!isTwoWay()) {
            log.info("Dispatched OneWay event to [{}]", targetName);
            return payload;
        }

        try {
            // Blocking: Wait here until the target flow processing is complete
            Payload resultPayload = future.get(timeout, TimeUnit.MILLISECONDS);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Call to [{}] finished in {} ms", targetName, duration);

            return resultPayload;

        } catch (TimeoutException e) {
            throw new FlowException(e, FlowResultStatus.TIMEOUT);
        } catch (ExecutionException e) {
            // Convert the exception using FlowErrorUtils and throw it silently.
            // This ensures the parent flow's FlowProcessor catches and logs the failure exactly once.
            throw FlowErrorUtils.convert(e.getCause() != null ? e.getCause() : e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FlowException(e, FlowResultStatus.INTERRUPTED);
        }
    }
}
