package io.zefio.gateway.netty.transaction;

import io.netty.util.concurrent.Promise;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * Wrapper for managing the state of a pending asynchronous transaction.
 * Stores the logging context (MDC) to ensure traceability throughout the lifecycle.
 */
public class TimeoutPromiseWrapper<T> {

    @Getter
    private final String key;
    @Getter
    private final Promise<T> promise;
    @Getter
    @Setter
    private ScheduledFuture<?> timeoutFuture;

    /**
     * Stores the MDC map at the time of the request.
     * Guarantees context persistence even if the request and response channels/threads differ.
     */
    @Getter
    @Setter
    private Map<String, String> mdcContext;

    public TimeoutPromiseWrapper(String key, Promise<T> promise) {
        this.key = key;
        this.promise = promise;
        this.mdcContext = MDC.getCopyOfContextMap();
    }
}
