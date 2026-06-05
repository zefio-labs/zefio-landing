package io.zefio.core;

import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ResponseListener;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Package-private tracking proxy callback class that explicitly handles transaction increments
 * and decrements across distinct SEDA compute boundaries, avoiding early return leaks.
 */
public class TrackingProxyCallback implements ResponseListener {
    private final ResponseListener delegate;
    private final AtomicInteger counter;
    private final AtomicBoolean completed = new AtomicBoolean(false);

    public TrackingProxyCallback(ResponseListener delegate, AtomicInteger counter) {
        this.delegate = delegate;
        this.counter = counter;
    }

    @Override
    public void success(Payload evt) {
        if (completed.compareAndSet(false, true)) {
            try {
                if (delegate != null) {
                    delegate.success(evt);
                }
            } finally {
                counter.decrementAndGet();
            }
        }
    }

    @Override
    public void error(Payload evt) {
        if (completed.compareAndSet(false, true)) {
            try {
                if (delegate != null) {
                    delegate.error(evt);
                }
            } finally {
                counter.decrementAndGet();
            }
        }
    }

    /**
     * Safely releases the atomic transaction counter without executing
     * duplicate network responses or populating redundant error logs.
     */
    public void silentRelease() {
        if (completed.compareAndSet(false, true)) {
            counter.decrementAndGet();
        }
    }
}
