package io.zefio.core;

import io.zefio.core.common.base.PluginType;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.util.TelegramFactory;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.FramingField;
import io.zefio.core.payload.builder.config.FramingType;
import io.zefio.core.payload.util.BytesUtils;
import io.zefio.core.telemetry.module.ModuleMetricsAggregator;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Base class for CPU-bound fault handlers.
 * Ensures that any pending sync waiters (SyncBridge) are notified and released
 * when a terminal error occurs during pipeline execution.
 */
public abstract class BaseComputeFaultHandler extends BaseComputeInterceptor implements FaultHandler {

    public BaseComputeFaultHandler(PluginContext context) {
        super(context);
        this.metricsAggregator = new ModuleMetricsAggregator(PluginType.faultHandler, context.getFlowName() + "-" + context.getPluginName());
    }

    /**
     * Overrides the execution logic to ensure post-processing notification.
     * After the error processing is complete, it triggers a bridge notification
     * to wake up any associated waiters.
     */
    @Override
    public CompletableFuture<Payload> executeAsync(Payload payload, Executor executor) {
        return super.executeAsync(payload, executor)
                .thenApply(resultEvent -> {
                    // Centralized post-processing: Notify and release pending waiters (SyncBridge)
                    this.notifyBridgeIfPending(resultEvent, this.syncBridge);
                    return resultEvent;
                });
    }

    /**
     * Helper for TCP stream protection: Appends framing headers to raw text error messages.
     * Used primarily for manual error responses in Fallback scenarios.
     */
    protected byte[] safeAppendFixedLength(Payload payload, byte[] body) {
        String telegramName = payload.getTelegramName();
        if (telegramName == null) return body;

        // Retrieve builder and metadata from the global factory
        PayloadBuilder builder = TelegramFactory.getBuilder(telegramName);

        if (builder != null && builder.getTelegram() != null) {
            FramingField framing = builder.getTelegram().getValues().getFraming();

            if (framing != null && framing.getType() == FramingType.Length) {
                int lengthSize = framing.getLengthDataSize() != null ? framing.getLengthDataSize() : 0;
                if (lengthSize > 0) {
                    return BytesUtils.appendLength(body, lengthSize, Boolean.TRUE.equals(framing.getLengthDataInclude()));
                }
            }
        }
        return body;
    }
}
