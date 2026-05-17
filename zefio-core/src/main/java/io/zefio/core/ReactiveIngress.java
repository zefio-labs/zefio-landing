package io.zefio.core;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.base.MDCKey;
import io.zefio.core.common.util.FlowErrorUtils;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.util.MDCUtils;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ZefioMessage;
import io.zefio.core.payload.util.IngressErrorUtils;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Base class for non-blocking Ingress modules (Kafka, MQ, Netty).
 * It orchestrates both synchronous and asynchronous ingress messages,
 * managing MDC context, payload creation, and edge-level error responses.
 */
public abstract class ReactiveIngress extends BaseIngress {

    protected IngressHandler ingressHandler;

    public ReactiveIngress(PluginContext context) {
        super(context);
    }

    @Override
    public void receive(IngressHandler handler) {
        this.ingressHandler = handler;
        try {
            // Delegate actual startup logic to the specific implementation (Listener or Poller)
            doStart();
        } catch (Exception e) {
            logFatalStartupError(e);
        }
    }

    protected abstract void doStart() throws Exception;

    // --- Entry point for Asynchronous Ingress (Kafka, RabbitMQ, etc.) ---
    public void onAsyncIngressMessage(Object rawData, String source) {
        handleIngressInternal(rawData, source, null);
    }

    // --- Entry point for Synchronous Ingress (HTTP, etc.) ---
    public byte[] onSyncIngressMessage(Object rawData, String source) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();

        handleIngressInternal(rawData, source, future);

        long timeout = (this.transactionTimeoutMillis > 0) ? this.transactionTimeoutMillis : 30000L;
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            FlowException flowEx = new FlowException(e, FlowResultStatus.TIMEOUT);
            log.error("{} Sync Request Timeout ({}ms).", logHeader, timeout);
            return IngressErrorUtils.buildEdgeErrorPayload(this.ingressBuilder, flowEx, responseEncoding);
        } catch (Exception e) {
            FlowException flowEx = FlowErrorUtils.convert(e);
            log.error("{} Sync Request Error: {}", logHeader, flowEx.getMessage());
            return IngressErrorUtils.buildEdgeErrorPayload(this.ingressBuilder, flowEx, responseEncoding);
        }
    }

    /**
     * Unified internal handler for data extraction, flow delegation, and error management.
     */
    private void handleIngressInternal(Object realData, String source, CompletableFuture<byte[]> syncFuture) {
        try {
            Payload payload = ingressBuilder.withBody(realData, requestEncoding);
            setMdcContext(payload, source);

            if (log.isInfoEnabled()) {
                log.info("Received from [{}] data [{}]", source, new String(payload.getBody(), requestEncoding));
            }

            if (!isTwoWay()) {
                // Fire-and-Forget Pattern
                payload.setCallback(new FireAndForgetCallback(getMetricsAggregator()));
                ingressHandler.onPayload(payload);

                if (syncFuture != null) syncFuture.complete(new byte[0]);
            } else {
                // Request-Reply Pattern
                payload.setCallback(new RequestReplyCallback(getMetricsAggregator(), ingressBuilder, responseEncoding) {
                    @Override
                    public Payload response(Payload payload) {
                        MDCUtils.restoreMdc(payload);

                        if (syncFuture != null) {
                            syncFuture.complete(payload.getBody());
                        } else {
                            completeAndSend(realData, payload);
                        }
                        return payload;
                    }
                });
                ingressHandler.onPayload(payload);
            }
        } catch (Exception e) {
            // Edge Reject handling before the payload enters the SEDA queue
            FlowException edgeEx = FlowErrorUtils.convert(e);
            log.error("{} Ingress Edge Reject [{}]: {}", logHeader, edgeEx.getStatus().name(), edgeEx.getMessage(), edgeEx);

            if (isTwoWay()) {
                byte[] errorPayload = IngressErrorUtils.buildEdgeErrorPayload(this.ingressBuilder, edgeEx, responseEncoding);

                if (syncFuture != null) {
                    syncFuture.complete(errorPayload);
                } else {
                    Payload errorEvent = new ZefioMessage(errorPayload, responseEncoding);
                    errorEvent.setThrowable(edgeEx);
                    completeAndSend(realData, errorEvent);
                }
            } else {
                if (syncFuture != null) syncFuture.completeExceptionally(edgeEx);
            }
        } finally {
            MDC.clear();
        }
    }

    protected void completeAndSend(Object rawMessage, Payload payload) {
        log.warn("{} completeAndSend not implemented.", logHeader);
    }

    private void setMdcContext(Payload payload, String source) {
        MDC.put(MDCKey.CID.getKey(), source);
        MDC.put(MDCKey.FLOW.getKey(), flowName);
        MDC.put(MDCKey.TID.getKey(), payload.getTrxID());

        payload.setMdcContext(MDC.getCopyOfContextMap());
        MDCUtils.restoreMdc(payload);
    }
}
