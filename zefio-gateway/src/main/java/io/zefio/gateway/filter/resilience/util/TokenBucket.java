package io.zefio.gateway.filter.resilience.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe, lock-free Token Bucket implementation.
 */
public class TokenBucket {
    private final long capacity;
    private final long refillRatePerSecond;
    private final AtomicLong tokens;
    private final AtomicLong lastRefillTimestamp;

    public TokenBucket(long capacity, long refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.tokens = new AtomicLong(capacity); // Initialized as full
        this.lastRefillTimestamp = new AtomicLong(System.nanoTime());
    }

    /**
     * Attempts to consume tokens from the bucket.
     *
     * @param numTokens Number of tokens to consume.
     * @return true if tokens were consumed successfully.
     */
    public boolean tryConsume(int numTokens) {
        refill();
        long currentTokens = tokens.get();
        if (currentTokens >= numTokens) {
            // Guarantee thread safety via CAS operation
            return tokens.compareAndSet(currentTokens, currentTokens - numTokens);
        }
        return false;
    }

    /**
     * Refills the bucket based on the elapsed time since the last refill.
     */
    private void refill() {
        long now = System.nanoTime();
        long lastRefill = lastRefillTimestamp.get();
        long elapsedTime = now - lastRefill;

        // Calculate tokens to add by converting nanoseconds to seconds
        long tokensToAdd = (elapsedTime * refillRatePerSecond) / 1_000_000_000L;

        if (tokensToAdd > 0) {
            // Update timestamp while preventing race conditions
            if (lastRefillTimestamp.compareAndSet(lastRefill, now)) {
                long currentTokens = tokens.get();
                long newTokens = Math.min(capacity, currentTokens + tokensToAdd);
                tokens.set(newTokens);
            }
        }
    }
}
