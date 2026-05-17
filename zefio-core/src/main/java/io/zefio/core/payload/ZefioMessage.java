package io.zefio.core.payload;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.ApplicationAttributes;
import io.zefio.core.common.util.FlowErrorUtils;
import io.zefio.core.payload.util.BytesUtils;
import io.zefio.core.payload.util.TelegramFactory;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concrete implementation of the Payload interface.
 * Manages message data with high-performance caching for Map (logical) and byte[] (physical) states.
 * Supports lazy transcoding and recursive deep-copying for parallel processing branches.
 */
public class ZefioMessage implements Payload, Serializable {

    private static final long serialVersionUID = 1L;
    private final Logger log = LoggerFactory.getLogger(getClass());

    // Single-line mapper optimized for high-performance logging
    private static final ObjectMapper LOG_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private final transient ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    @Setter @Getter
    private String telegramName;
    @Setter @Getter
    private String trxID;

    @Setter @Getter
    private byte[] requestBody;
    private Object body;

    // Caches to prevent redundant parsing/baking operations
    private transient Map<String, Object> cachedMap;
    private transient byte[] cachedBytes;

    private Charset requestEncoding;
    @Getter
    private Charset currentEncoding;

    @Setter @Getter
    private Date requestTime;
    @Setter @Getter
    private Date elapsedTime;
    @Setter @Getter
    private long startTime;
    @Setter @Getter
    private long queueWaitTime;
    @Setter @Getter
    private long remoteTime;
    @Setter @Getter
    private boolean suppressStatLog;
    @Setter @Getter
    private ResponseListener callback;
    private boolean bodyModified = false;

    private Throwable throwable;

    private final Map<String, Object> headers = new ConcurrentHashMap<>();

    public ZefioMessage(byte[] body, Charset encoding) {
        this.currentEncoding = encoding;
        this.requestEncoding = encoding;
        setBody(body);
        this.requestBody = body;
        this.startTime = System.currentTimeMillis();
        this.requestTime = new Date(this.startTime);
        this.elapsedTime = new Date();
    }

    @Override
    public Payload copyFactory(Payload payload) {
        ZefioMessage original = (ZefioMessage) payload;
        ZefioMessage newEvent = new ZefioMessage(null, original.currentEncoding);

        // Copy physical state
        byte[] originalBytes = original.getBody();
        if (originalBytes != null) {
            newEvent.syncPhysicalState(Arrays.copyOf(originalBytes, originalBytes.length), original.getCurrentEncoding());
        }

        // Inheritance of parsed data to child flows
        if (original.cachedMap != null) {
            newEvent.cachedMap = new ConcurrentHashMap<>(original.cachedMap);
        }

        newEvent.setRequestBody(original.getRequestBody() != null ? Arrays.copyOf(original.getRequestBody(), original.getRequestBody().length) : null);
        newEvent.setStartTime(original.getStartTime());
        newEvent.setQueueWaitTime(original.getQueueWaitTime());
        newEvent.setRemoteTime(original.getRemoteTime());
        newEvent.setSuppressStatLog(original.isSuppressStatLog());

        // CRITICAL: Callbacks are NOT copied to prevent duplicate responses from child flows
        newEvent.setCallback(null);

        // Deep copy of headers to isolate processing branches
        newEvent.setHeader(new ConcurrentHashMap<>(original.getHeaders()));

        if (original.getRequestTime() != null) newEvent.setRequestTime(new Date(original.getRequestTime().getTime()));
        if (original.getElapsedTime() != null) newEvent.setElapsedTime(new Date(original.getElapsedTime().getTime()));

        newEvent.setTelegramName(original.getTelegramName());
        newEvent.setBodyModified(original.isBodyModified());
        newEvent.setTrxID(original.getTrxID());

        return newEvent;
    }

    @Override
    public void mergeResponse(Payload responsePayload, boolean includeMetrics) {
        if (responsePayload == null) return;
        ZefioMessage res = (ZefioMessage) responsePayload;

        // Atomic state absorption
        if (res.body instanceof Map) {
            this.setBodyMap(res.getBodyMap());
            this.setCurrentEncoding(res.getCurrentEncoding());
        } else {
            byte[] resBytes = res.getBody();
            if (resBytes != null) {
                this.syncPhysicalState(resBytes, res.getCurrentEncoding());
            }
        }

        if (res.cachedMap != null) this.cachedMap = res.cachedMap;
        if (res.cachedBytes != null) this.cachedBytes = res.cachedBytes;

        if (res.hasException()) this.setThrowable(res.getThrowable());

        if (res.getHeaders() != null) {
            res.getHeaders().forEach((k, v) -> {
                if (!ApplicationAttributes.MDC_CONTEXT_PROPERTY_KEY.equals(k)) {
                    this.setHeader(k, v);
                }
            });
        }

        // Aggregate metrics rather than overwriting
        if (includeMetrics) {
            this.addQueueWaitTime(res.getQueueWaitTime());
            this.addRemoteTime(res.getRemoteTime());
        }
    }

    private void syncPhysicalState(byte[] newBody, Charset encoding) {
        this.body = newBody;
        this.currentEncoding = encoding;
        this.requestEncoding = encoding;
        this.cachedBytes = null;
        this.cachedMap = null;
        this.bodyModified = true;
    }

    @Override
    public void addRemoteTime(long duration) {
        this.remoteTime += duration;
    }

    @Override
    public void addQueueWaitTime(long duration) {
        this.queueWaitTime += duration;
    }

    @Override
    public byte[] getBody() {
        // Fast Path: Match encoding and physical presence
        if (body instanceof byte[] && currentEncoding.equals(requestEncoding)) {
            return (byte[]) body;
        }

        if (cachedBytes != null) return cachedBytes;

        // Lazy Transcoding
        if (body instanceof byte[] && !currentEncoding.equals(requestEncoding)) {
            cachedBytes = BytesUtils.changeEncoding((byte[])body, requestEncoding, currentEncoding);
            return cachedBytes;
        }

        // Lazy Baking from logical Map
        if (body instanceof Map) {
            try {
                log.debug("[Performance] Baking Map to Bytes for Telegram: {}", telegramName);
                cachedBytes = TelegramFactory.getBuilder(telegramName).buildFromMap((Map) body, currentEncoding);
                return cachedBytes;
            } catch (Exception e) {
                throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
            }
        }
        return null;
    }

    @Override
    public void setBody(byte[] body) {
        syncPhysicalState(body, this.currentEncoding);
    }

    @Override
    public void setBodyMap(Map<String, Object> map) {
        this.body = map;
        this.cachedMap = map;
        this.cachedBytes = null;
        this.bodyModified = true;
    }

    @Override
    public Map<String, Object> getBodyMap() {
        if (body instanceof Map) return (Map<String, Object>) body;
        if (cachedMap != null) return cachedMap;

        byte[] targetBytes = this.getBody();
        if (targetBytes != null) {
            try {
                log.debug("[Performance] Parsing Bytes to Map for Telegram: {}", telegramName);
                Map<String, Object> parsed = TelegramFactory.getBuilder(telegramName).parseToMap(targetBytes, currentEncoding);
                cachedMap = new ConcurrentHashMap<>(parsed);
                return cachedMap;
            } catch (Exception e) {
                throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
            }
        }
        return null;
    }

    @Override
    public void setCurrentEncoding(Charset currentEncoding) {
        if (this.currentEncoding != null && !this.currentEncoding.equals(currentEncoding)) {
            this.cachedBytes = null;
        }
        this.currentEncoding = currentEncoding;
    }

    @Override
    public void setThrowable(Throwable throwable){ this.throwable = throwable; }

    @Override
    public Throwable getThrowable(){ return this.throwable; }

    @Override
    public boolean hasException() { return this.throwable != null; }

    @Override
    public void setHeader(Map<String, Object> map) { this.headers.putAll(map); }

    @Override
    public void setHeader(String key, Object value) { this.headers.put(key, value); }

    @Override
    public Map<String, Object> getHeaders() {
        synchronized (headers) {
            return ImmutableMap.copyOf(headers);
        }
    }

    @Override
    public Object getHeader(String key) { return this.headers.get(key); }

    @Override
    public boolean containsKeyHeader(String key) { return this.headers.containsKey(key); }

    @Override
    public Map<String, Object> getSubHeaders(String prefix) {
        Map<String, Object> result = Maps.newHashMap();
        synchronized (headers) {
            for (Entry<String, Object> entry : headers.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(prefix)) {
                    result.put(key.substring(prefix.length()), entry.getValue());
                }
            }
        }
        return ImmutableMap.copyOf(result);
    }

    @Override
    public void removeHeadersByPrefix(String prefix) {
        synchronized (headers) {
            headers.keySet().removeIf(key -> key.startsWith(prefix));
        }
    }

    @Override
    public void setMdcContext(Map<String, String> mdcContext) {
        if (mdcContext != null && !mdcContext.isEmpty()) {
            this.headers.put(ApplicationAttributes.MDC_CONTEXT_PROPERTY_KEY, mdcContext);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> getMdcContext() {
        Object context = this.headers.get(ApplicationAttributes.MDC_CONTEXT_PROPERTY_KEY);
        if (context instanceof Map) {
            return (Map<String, String>) context;
        }
        return null;
    }

    @Override
    public boolean isBodyModified() { return this.bodyModified; }

    @Override
    public void setBodyModified(boolean bodyModified) { this.bodyModified = bodyModified; }

    private long calculateElapsed() {
        return (elapsedTime != null && requestTime != null) ? elapsedTime.getTime() - requestTime.getTime() : 0;
    }

    private int getActualBodyLength() {
        if (body instanceof byte[]) return ((byte[]) body).length;
        if (cachedBytes != null) return cachedBytes.length;
        return -1; // Logical state (not yet baked)
    }

    @Override
    public String response() {
        try {
            Map<String, Object> logData = new java.util.LinkedHashMap<>();
            logData.put("log_type", "RESPONSE");
            logData.put("tid", this.trxID);
            logData.put("status", (this.throwable == null) ? "SUCCESS" : "ERROR");
            logData.put("elapsed_ms", calculateElapsed());
            logData.put("req_len", requestBody != null ? requestBody.length : 0);
            logData.put("res_len", getActualBodyLength());
            logData.put("encoding", currentEncoding != null ? currentEncoding.displayName() : "UNKNOWN");

            if (this.throwable != null) {
                logData.put("throw_msg", FlowErrorUtils.getErrorMessage(this.throwable));
            }

            if (log.isDebugEnabled()) {
                logData.put("props", this.getHeaders());
            }

            logData.put("req_body", formatPayload(requestBody, requestEncoding));
            logData.put("res_body", formatPayload(this.body, currentEncoding));

            return "[RESPONSE] " + LOG_MAPPER.writeValueAsString(logData);
        } catch (JsonProcessingException e) {
            return String.format("[RESPONSE] {\"log_type\":\"RESPONSE\",\"status\":\"LOG_ERROR\",\"tid\":\"%s\",\"msg\":\"%s\"}", this.trxID, e.getMessage());
        }
    }

    private String formatPayload(Object payload, Charset charset) {
        if (payload == null) return null;
        String content;
        if (payload instanceof byte[]) {
            content = new String((byte[]) payload, charset != null ? charset : StandardCharsets.UTF_8);
        } else if (payload instanceof Map) {
            try {
                content = LOG_MAPPER.writeValueAsString(payload);
            } catch (Exception e) {
                content = payload.toString();
            }
        } else {
            content = String.valueOf(payload);
        }
        return content.length() > 4000 ? content.substring(0, 4000) + "..." : content;
    }
}
