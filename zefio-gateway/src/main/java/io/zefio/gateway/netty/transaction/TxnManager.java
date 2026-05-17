package io.zefio.gateway.netty.transaction;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.ApplicationAttributes;
import io.zefio.core.common.util.FlowErrorUtils;
import io.zefio.gateway.netty.util.NettyUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Core Transaction Manager handling asynchronous request-response correlation.
 * Integrates Caffeine for timeout management and provides Netty-to-Java Future bridging.
 */
public class TxnManager<T> {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Cache<String, TimeoutPromiseWrapper<T>> pendingPromiseCache;
    private final String EMPTY_KEY = "EMPTY_KEY";
    private final ITxnManager<T> txnManager;
    private final String moduleName;
    private final boolean isClientSend;

    public TxnManager(ITxnManager<T> txnManager, String moduleName, long transactionTimeoutMillis, boolean isClientSend) {
        this.txnManager = txnManager;
        this.moduleName = moduleName;
        this.isClientSend = isClientSend;

        this.pendingPromiseCache = Caffeine.newBuilder()
                .expireAfterWrite(transactionTimeoutMillis, TimeUnit.MILLISECONDS)
                .removalListener((String key, TimeoutPromiseWrapper<T> wrapper, RemovalCause cause) -> {
                    // Fail the promise only if evicted due to expiration (TIMEOUT) or capacity constraints
                    if (cause.wasEvicted() && wrapper != null) {
                        NettyUtils.runWithMdc(wrapper.getMdcContext(), () -> {
                            if (!wrapper.getPromise().isDone()) {
                                log.debug("[{}] [{}] Transaction marked as TIMEOUT (Cause: {})", moduleName, key, cause);
                                wrapper.getPromise().tryFailure(new FlowException(FlowResultStatus.TIMEOUT, "Transaction timeout expired"));
                            }
                        });
                    }
                })
                .build();
    }

    /**
     * Internal method to register a transaction promise in the pending cache.
     * Uses atomic compute logic to prevent duplicate requests from overwriting active transactions.
     */
    private Promise<T> registerTransaction(Channel channel, T body, String key) {
        Promise<T> promise = channel.eventLoop().newPromise();
        if (!channel.isActive()) {
            promise.setFailure(new ClosedChannelException());
            return promise;
        }

        TimeoutPromiseWrapper<T> newWrapper = new TimeoutPromiseWrapper<>(key, promise);
        newWrapper.setMdcContext(MDC.getCopyOfContextMap());

        ConcurrentMap<String, TimeoutPromiseWrapper<T>> map = pendingPromiseCache.asMap();

        // Protect active transactions; only allow replacement if the previous transaction is done
        TimeoutPromiseWrapper<T> actualResult = map.compute(key, (k, existing) -> {
            if (existing != null && !existing.getPromise().isDone()) return existing;
            return newWrapper;
        });

        if (actualResult != newWrapper) {
            promise.setFailure(new FlowException(FlowResultStatus.DUPLICATE_REQUEST, "Duplicate request key detected: " + key));
            return promise;
        }

        if (this.isClientSend) {
            channel.writeAndFlush(body).addListener(future -> {
                NettyUtils.runWithMdc(newWrapper.getMdcContext(), () -> {
                    if (future.isSuccess()) {
                        log.debug("[{}] [{}] Request successfully written to buffer; awaiting response.", this.moduleName, key);
                    } else {
                        log.warn("[{}] [{}] Network write failed", this.moduleName, key, future.cause());
                        pendingPromiseCache.invalidate(key);
                        promise.setFailure(future.cause());
                    }
                });
            });
        } else {
            log.debug("[{}] [{}] Registered to pending map (Inbound registration)", this.moduleName, key);
        }
        return promise;
    }

    /**
     * Initiates an asynchronous send and returns a Java CompletableFuture.
     */
    public CompletableFuture<T> sendAsync(Channel channel, T body) {
        String key = this.txnManager.getKey(channel, body);
        if (key.isEmpty()) {
            CompletableFuture<T> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new FlowException(FlowResultStatus.NOT_CORRELATION_KEY, "Correlation key is missing."));
            return failedFuture;
        }

        channel.attr(ApplicationAttributes.CORRELATION_ID).set(key);
        Promise<T> promise = registerTransaction(channel, body, key);
        TimeoutPromiseWrapper<T> wrapper = pendingPromiseCache.getIfPresent(key);

        return toCompletableFuture(promise, key, wrapper);
    }

    /**
     * Completes the transaction when a response message matches a pending key.
     * Restores the original MDC context to provide accurate logging for the completion phase.
     */
    public void complete(Channel channel, T newValue) throws FlowException {
        String key = this.txnManager.getKey(channel, newValue);
        if (key.isEmpty()) throw new FlowException(FlowResultStatus.NOT_CORRELATION_KEY, "Response key is missing.");

        TimeoutPromiseWrapper<T> wrapper = pendingPromiseCache.asMap().remove(key);

        // Guard against orphaned responses (responses arriving after a timeout or for non-existent requests)
        if (wrapper == null) {
            log.warn("[{}] [{}] Orphaned or unmatched response received. Dropping message.", this.moduleName, key);
            throw new FlowException(FlowResultStatus.ORPHANED_RESPONSE, "Unmatched response received.");
        }

        // Restore logging context from the original request
        Map<String, String> mdcContext = wrapper.getMdcContext();
        if (mdcContext != null) MDC.setContextMap(mdcContext);
        else MDC.clear();

        if (wrapper.getPromise().trySuccess(newValue)) {
            log.info("[{}] [{}] Transaction successfully completed.", this.moduleName, key);
        } else {
            log.warn("[{}] [{}] Failed to complete promise; already resolved by another process.", this.moduleName, key);
            throw new FlowException(FlowResultStatus.ALREADY_COMPLETED, "Transaction already completed.");
        }
    }

    /**
     * Bridging Utility: Converts a Netty Promise into a Java CompletableFuture.
     */
    @SuppressWarnings("unchecked")
    protected CompletableFuture<T> toCompletableFuture(io.netty.util.concurrent.Future<T> promise, String key, TimeoutPromiseWrapper<T> wrapper) {
        CompletableFuture<T> future = new CompletableFuture<>();
        promise.addListener(f -> {
            if (f.isSuccess()) future.complete((T) f.getNow());
            else {
                FlowException ex = FlowErrorUtils.convert(f.cause());
                FlowResultStatus status = ex.getStatus();

                // Do not invalidate for duplicates or already-processed timeouts
                if (wrapper != null &&
                        status != FlowResultStatus.DUPLICATE_REQUEST &&
                        status != FlowResultStatus.TIMEOUT &&
                        !EMPTY_KEY.equals(key)) {
                    pendingPromiseCache.invalidate(key);
                }

                log.warn("[{}] [{}] code[{}] msg[{}]", moduleName, key, status.getCode(), status.getMessage());
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    /**
     * Cleanup triggered when a channel is closed unexpectedly.
     * Ensures all pending transactions on the closed channel are failed with ALREADY_CLOSED status.
     */
    public void close(Channel channel) {
        String key = channel.attr(ApplicationAttributes.CORRELATION_ID).getAndSet(null);
        if (ObjectUtils.isEmpty(key)) return;

        TimeoutPromiseWrapper<T> wrapper = pendingPromiseCache.asMap().remove(key);
        if (wrapper != null && !wrapper.getPromise().isDone()) {
            // Propagate exception to trigger the Error Filter immediately
            wrapper.getPromise().tryFailure(
                    new FlowException(FlowResultStatus.ALREADY_CLOSED, "Network connection closed unexpectedly during transaction.")
            );
        }
    }

    /**
     * System-wide cleanup for shutdown sequences.
     */
    public void clear() {
        pendingPromiseCache.asMap().forEach((key, wrapper) -> {
            if (!wrapper.getPromise().isDone()) {
                wrapper.getPromise().tryFailure(new FlowException(FlowResultStatus.SYSTEM_SHUTDOWN, "System shutdown initiated."));
            }
        });
        long count = pendingPromiseCache.estimatedSize();
        pendingPromiseCache.invalidateAll();

        log.info("[{}] Transaction Manager cleared. Total [{}] pending requests cancelled.", moduleName, count);
    }
}
