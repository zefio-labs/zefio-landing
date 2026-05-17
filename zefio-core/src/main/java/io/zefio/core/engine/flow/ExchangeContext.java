package io.zefio.core.engine.flow;

import io.zefio.core.payload.Payload;

/**
 * A wrapper class used to associate a processing Payload with the exact time
 * it entered a specific stage or queue, primarily used for tracking stay time and latency.
 */
public class ExchangeContext {
    public final Payload payload;
    public final long enqueuedTime;

    public ExchangeContext(Payload payload) {
        this.payload = payload;
        this.enqueuedTime = System.currentTimeMillis();
    }
}
