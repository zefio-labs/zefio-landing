package io.zefio.core;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.util.MDCUtils;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.ResponseListener;
import io.zefio.core.telemetry.module.ModuleMetricsAggregator;
import io.zefio.core.telemetry.stat.StatLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.charset.Charset;
import java.util.Date;

/**
 * Standard callback for Request-Reply interaction patterns.
 * Handles final response formatting for Ingress clients and records execution metrics.
 */
public abstract class RequestReplyCallback implements ResponseListener {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected ModuleMetricsAggregator metricsAggregator;
    protected final PayloadBuilder ingressBuilder;
    protected Charset responseEncoding;

    public RequestReplyCallback(ModuleMetricsAggregator metricsAggregator, PayloadBuilder ingressBuilder, Charset responseEncoding) {
        this.metricsAggregator = metricsAggregator;
        this.metricsAggregator.incrementPayloadReceivedCount();
        this.ingressBuilder = ingressBuilder;
        this.responseEncoding = responseEncoding;
    }

    @Override
    public void success(Payload payload) {
        boolean isFinalSuccess = true;
        try {
            // Finalize Upstream payload for the Ingress response
            payload = this.ingressBuilder.finalizeUpstreamPayload(payload, this.responseEncoding);
            payload = response(payload);
        } catch (Exception e) {
            isFinalSuccess = false;

            if (e instanceof FlowException) {
                throw (FlowException) e;
            } else {
                throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
            }
        } finally {
            finalizeTransaction(payload, isFinalSuccess);
        }
    }

    @Override
    public void error(Payload payload) {
        try {
            // Finalize Upstream error payload for the Ingress response
            payload = this.ingressBuilder.finalizeUpstreamPayload(payload, this.responseEncoding);
            payload = response(payload);
        } catch (Exception e) {
            if (e instanceof FlowException) {
                throw (FlowException) e;
            } else {
                throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
            }
        } finally {
            finalizeTransaction(payload, false);
        }
    }

    /**
     * Finalizes the transaction by restoring MDC, calculating metrics, and logging stats.
     */
    private void finalizeTransaction(Payload payload, boolean isSuccess) {
        if (payload.getMdcContext() != null) {
            MDCUtils.restoreMdc(payload);
        }

        try {
            payload.setElapsedTime(new Date());
            long totalElapsed = System.currentTimeMillis() - payload.getStartTime();
            this.metricsAggregator.addExecutionTime(totalElapsed);

            // Log detailed response and STAT metrics only if suppressed flag is false
            if (!payload.isSuppressStatLog()) {

                // 1. Transaction response logging
                if (isSuccess) {
                    this.metricsAggregator.incrementPayloadAcceptedCount();
                    log.info("{}", payload.response());
                } else {
                    this.metricsAggregator.incrementPayloadFailedCount();
                    log.error("{}", payload.response());
                }

                // 2. Resource/Stat logging
                StatLogger.log(payload);

            } else {
                // If suppressed (e.g., internal router flows), update metrics without detailed logging
                if (isSuccess) this.metricsAggregator.incrementPayloadAcceptedCount();
                else this.metricsAggregator.incrementPayloadFailedCount();

                log.debug("Transaction finished silently (suppressed). TID: {}", payload.getTrxID());
            }
        } finally {
            // Clear MDC context before thread return to prevent cross-transaction pollution
            MDC.clear();
        }
    }

    public abstract Payload response(Payload payload);
}
