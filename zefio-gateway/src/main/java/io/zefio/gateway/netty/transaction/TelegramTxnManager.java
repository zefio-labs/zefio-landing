package io.zefio.gateway.netty.transaction;

import io.netty.channel.Channel;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.util.ApplicationAttributes;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.CorrelationField;
import io.zefio.core.payload.builder.config.CorrelationIdType;
import io.zefio.core.payload.spel.PayloadExpressionEvaluator;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Manages transactions using a Correlation ID (Telegram-based matching).
 * Supports complex extraction logic including SpEL and Fixed-offsets.
 */
public class TelegramTxnManager<T> implements ITxnManager<Payload> {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final TxnManager<Payload> txnManager;
    private final PayloadBuilder eventBuilder;

    public TelegramTxnManager(String moduleName, PayloadBuilder eventBuilder, long transactionTimeoutMillis, boolean isClientSend) {
        this.txnManager = new TxnManager<>(this, moduleName, transactionTimeoutMillis, isClientSend);
        this.eventBuilder = eventBuilder;
    }

    @Override
    public String getKey(Channel channel, Payload payload) {
        // Priority 1: Use existing TrxID if already set in the Payload object
        if (ObjectUtils.isNotEmpty(payload.getTrxID())) {
            return payload.getTrxID().trim();
        }

        // Priority 2: Check Channel Attributes (Crucial for HTTP upstream where body might be modified)
        String savedKey = channel.attr(ApplicationAttributes.CORRELATION_ID).get();
        if (StringUtils.isNotBlank(savedKey)) {
            log.debug("[Session-Match] Using TID from Channel Attribute: [{}]", savedKey);
            return savedKey.trim();
        }

        // Priority 3: Extract from the Byte Body (Standard path for Inbound requests)
        PayloadBuilder targetBuilder = this.eventBuilder;
        if (targetBuilder == null) return "";

        if (payload.getTelegramName() == null) {
            payload.setTelegramName(targetBuilder.getTelegram().getName());
        }

        CorrelationField correlation = targetBuilder.getTelegram().getValues().getCorrelation();
        String extractedKey = "";

        try {
            if (correlation.getType() == CorrelationIdType.SpEL) {
                extractedKey = PayloadExpressionEvaluator.evaluate(correlation.getExpression(), payload, String.class);
            } else if (correlation.getType() == CorrelationIdType.Offset) {
                extractedKey = targetBuilder.extractCorrelationId(null, payload.getBody(), payload.getCurrentEncoding());
            }
        } catch (Exception e) {
            log.debug("Correlation ID extraction failed.");
        }

        return ObjectUtils.isNotEmpty(extractedKey) ? extractedKey.trim() : "";
    }

    @Override
    public CompletableFuture<Payload> send(Channel channel, Payload payload) {
        return this.txnManager.sendAsync(channel, payload);
    }

    @Override
    public void complete(Channel channel, Payload payload) throws FlowException {
        this.txnManager.complete(channel, payload);
    }

    @Override
    public void close(Channel channel) {
        this.txnManager.close(channel);
    }

    @Override
    public void clear() {
        this.txnManager.clear();
    }
}
