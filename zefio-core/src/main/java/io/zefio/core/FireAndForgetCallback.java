package io.zefio.core;

import io.zefio.core.util.MDCUtils;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ResponseListener;
import io.zefio.core.telemetry.module.ModuleMetricsAggregator;
import io.zefio.core.telemetry.stat.StatLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Date;

/**
 * Callback implementation for Fire-and-Forget interaction patterns.
 * Finalizes the transaction metrics and logs without returning a response to the Ingress source.
 */
public class FireAndForgetCallback implements ResponseListener {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected ModuleMetricsAggregator metricsAggregator;

    public FireAndForgetCallback(ModuleMetricsAggregator metricsAggregator) {
        this.metricsAggregator = metricsAggregator;
        this.metricsAggregator.incrementPayloadReceivedCount();
    }

    @Override
    public void success(Payload payload) {
        finalizeTransaction(payload, true);
    }

    @Override
    public void error(Payload payload) {
        finalizeTransaction(payload, false);
    }

    /**
     * Finalizes the transaction by calculating elapsed time and recording statistics.
     */
    private void finalizeTransaction(Payload payload, boolean isSuccess) {
        if (payload.getMdcContext() != null) {
            MDCUtils.restoreMdc(payload);
        }

        try {
            payload.setElapsedTime(new Date());
            long totalElapsed = System.currentTimeMillis() - payload.getStartTime();
            this.metricsAggregator.addExecutionTime(totalElapsed);

            // Detailed transaction and STAT logging is performed only if suppressStatLog is false
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
                // If suppressed, update metrics silently without detailed logs
                if (isSuccess) this.metricsAggregator.incrementPayloadAcceptedCount();
                else this.metricsAggregator.incrementPayloadFailedCount();

                log.debug("Transaction finished silently (suppressed). TID: {}", payload.getTrxID());
            }
        } finally {
            // Clear MDC context before thread return
            MDC.clear();
        }
    }
}
