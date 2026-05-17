package io.zefio.core;

import io.zefio.core.common.base.PluginType;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.ApplicationAttributes;
import io.zefio.core.payload.Payload;
import io.zefio.core.schema.dto.UpstreamValues;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.util.MDCUtils;
import io.zefio.core.payload.ExchangePattern;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.util.TelegramFactory;
import io.zefio.core.telemetry.module.ModuleMetricsAggregator;
import org.slf4j.MDC;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Base implementation for all Upstream modules.
 * Handles the central control of requests and responses for target systems,
 * including transcoding, payload transformation, and metrics aggregation.
 */
public abstract class BaseUpstream extends BaseIoInterceptor implements Upstream {
    protected ExchangePattern exchangePattern;
    protected final String upstreamTelegramName;
    protected volatile PayloadBuilder upstreamBuilder;

    protected long transactionTimeoutMillis;
    protected final Charset requestEncoding;
    protected final Charset responseEncoding;

    public BaseUpstream(PluginContext context) {
        super(context);

        this.exchangePattern = context.getExchangePattern();
        this.upstreamTelegramName = context.getTelegramName();

        UpstreamValues values = yamlMapper.convertValue(context.getContext(), UpstreamValues.class);
        this.requestEncoding = values.getRequestEncoding();
        this.responseEncoding = values.getResponseEncoding();

        // Sets the metrics type specifically for Upstream components
        this.metricsAggregator = new ModuleMetricsAggregator(PluginType.upstream, context.getFlowName() + "-" + context.getPluginName());
    }

    @Override
    public void initialise() throws Exception {
        super.initialise();

        // Pre-warming the Upstream Builder cache during startup
        if (this.upstreamTelegramName != null) {
            getEventBuilder();
            log.info("{} Upstream Builder [{}] cached successfully.", this.pluginName, this.upstreamTelegramName);
        } else {
            log.info("{} Upstream will operate in Pure Bypass Mode.", this.pluginName);
        }
    }

    @Override
    public PayloadBuilder getEventBuilder() {
        // Fast Path: Return from cache if already initialized
        if (upstreamBuilder != null) {
            return upstreamBuilder;
        }

        // Slow Path: Synchronized initialization from factory
        synchronized (this) {
            if (upstreamBuilder == null) {
                PayloadBuilder builder = TelegramFactory.getBuilder(upstreamTelegramName);
                if (builder == null) {
                    throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "Telegram Builder not found: " + upstreamTelegramName);
                }
                this.upstreamBuilder = builder;
            }
        }
        return this.upstreamBuilder;
    }

    /**
     * Resets the Upstream builder cache to support configuration reloads.
     */
    @Override
    public void refresh() {
        this.upstreamBuilder = null;
    }

    @Override
    public boolean isTwoWay() {
        return this.exchangePattern == ExchangePattern.RequestReply;
    }

    /**
     * Upstream components always track execution time as Remote communication time.
     */
    @Override
    protected boolean isTrackAsRemote() { return true; }

    /**
     * Orchestrates the asynchronous request/reply flow for the Upstream target.
     */
    @Override
    public CompletableFuture<Payload> executeAsync(Payload payload, Executor flowExecutor) {

        return CompletableFuture.completedFuture(payload)
                // 1. Request processing before transmission (Finalizing encoding and length)
                .thenApply(evt -> {
                    MDCUtils.restoreMdc(evt);
                    try {
                        finalizePayloadBeforeSend(evt);
                        return evt;
                    } finally {
                        MDC.clear();
                    }
                })
                // 2. Execution of specific protocol logic (e.g., Netty, MQ)
                .thenCompose(origPayload -> super.executeAsync(origPayload, flowExecutor)

                        // 3. Response processing after reception (Merging and Transcoding)
                        .thenApply(rawResPayload -> {
                            Payload finalResult = rawResPayload;

                            if (isTwoWay() && rawResPayload != null && rawResPayload != origPayload) {
                                MDCUtils.restoreMdc(origPayload);
                                try {
                                    // Merge child response back into the original payload context
                                    origPayload.mergeResponse(rawResPayload, true);

                                    Charset targetEncoding = firstNonNull(
                                            this.responseEncoding,
                                            this.requestEncoding,
                                            rawResPayload.getCurrentEncoding()
                                    );

                                    // Check for dynamic encoding overrides from protocol headers
                                    String dynamicEncoding = (String) rawResPayload.getHeader(ApplicationAttributes.DYNAMIC_RESPONSE_ENCODING);
                                    if (dynamicEncoding != null) {
                                        try {
                                            targetEncoding = Charset.forName(dynamicEncoding);
                                            log.debug("Using dynamic encoding from protocol header: {}", targetEncoding);
                                        } catch (Exception e) {
                                            log.warn("Invalid dynamic encoding [{}], fallback to default", dynamicEncoding);
                                        }
                                    }

                                    origPayload.setCurrentEncoding(targetEncoding);
                                    finalResult = origPayload;

                                    int bodyLen = finalResult.getBody() != null ? finalResult.getBody().length : 0;
                                    if (log.isDebugEnabled()) {
                                        log.debug("Central Response Normalized [{}]: Encoding={}, Length={}", pluginName, finalResult.getCurrentEncoding(), bodyLen);
                                    }
                                    log.info("Response: trx[{}] length[{}]", finalResult.getTrxID(), bodyLen);

                                } finally {
                                    MDC.clear();
                                }
                            }

                            // Ensure the pipeline continues with the original context even for Fire-and-Forget
                            if (finalResult == null) {
                                finalResult = origPayload;
                            }

                            // Propagate business errors as exceptions to trigger the flow's error handling strategy
                            if (finalResult != null && finalResult.hasException()) {
                                throw new CompletionException(finalResult.getThrowable());
                            }
                            return finalResult;
                        })
                );
    }

    private Charset firstNonNull(Charset... values) {
        for (Charset v : values) {
            if (v != null) return v;
        }
        return null;
    }

    /**
     * Finalizes the physical specification (framing, encoding) before sending the payload.
     */
    protected void finalizePayloadBeforeSend(Payload payload) throws FlowException {
        Charset effectiveRequestEncoding = (this.requestEncoding != null) ?
                this.requestEncoding :
                payload.getCurrentEncoding();

        boolean isBypass = (this.upstreamTelegramName == null) ||
                (this.upstreamTelegramName.equals(payload.getTelegramName()) && !payload.isBodyModified());

        PayloadBuilder safeUpstreamBuilder = (this.upstreamTelegramName != null) ? getEventBuilder() : null;

        if (isBypass) {
            // Bypass Mode: Direct forwarding without transformation
            log.debug("[Bypass Mode] Zero-Copy Forwarding. (Telegram: {})", payload.getTelegramName());
            payload.setCurrentEncoding(effectiveRequestEncoding);

        } else {
            // Transform Mode: Perform parsing and re-serialization for heterogeneous communication
            log.info("[Transform Mode] {} -> {}", payload.getTelegramName(), this.upstreamTelegramName);
            try {
                PayloadBuilder upstreamBuilder = TelegramFactory.getBuilder(payload.getTelegramName());
                if (upstreamBuilder == null) {
                    throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "Cannot find Upstream builder for telegram: " + payload.getTelegramName());
                }

                Map<String, Object> canonicalMap = upstreamBuilder.parseToMap(payload.getBody(), payload.getCurrentEncoding());
                payload.setBodyMap(canonicalMap);
                payload.setCurrentEncoding(effectiveRequestEncoding);
                payload.setTelegramName(this.upstreamTelegramName);

            } catch (Exception e) {
                if (e instanceof FlowException) throw (FlowException) e;
                throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
            }
        }

        // Apply final framing and encoding corrections using the Upstream builder
        if (safeUpstreamBuilder != null) {
            safeUpstreamBuilder.finalizeUpstreamPayload(payload, effectiveRequestEncoding);
        }

        if (log.isDebugEnabled()) {
            log.debug("Central Request Finalized [{}]: Encoding={}, Length={}",
                    pluginName, payload.getCurrentEncoding(), payload.getBody().length);
        }
    }
}
