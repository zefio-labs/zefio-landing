package io.zefio.gateway.filter.resilience;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.BaseComputeInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.gateway.filter.resilience.dto.AdvancedRateLimitValues;
import io.zefio.gateway.filter.resilience.util.TokenBucket;
import io.zefio.core.payload.Payload;

import java.util.concurrent.TimeUnit;

/**
 * Advanced Rate Limit filter supporting multi-keyed identification using the Token Bucket algorithm.
 */
public class AdvancedRateLimit extends BaseComputeInterceptor {

    private final AdvancedRateLimitValues values;

    // Single bucket for GLOBAL mode (Flow-wide rate control)
    private final TokenBucket globalBucket;

    // Dynamic bucket management for PER_KEY mode (Identifier isolation)
    private Cache<String, TokenBucket> bucketCache;

    public AdvancedRateLimit(PluginContext context) {
        super(context);
        this.values = yamlMapper.convertValue(context.getContext(), AdvancedRateLimitValues.class);

        this.globalBucket = new TokenBucket(values.getBurstCapacity(), values.getReplenishRate());

        // Activate Caffeine Cache for PER_KEY mode to prevent OOM
        if (values.getLimitType() == AdvancedRateLimitValues.LimitType.PER_KEY) {
            this.bucketCache = Caffeine.newBuilder()
                    .expireAfterAccess(10, TimeUnit.MINUTES)    // Evict after 10 minutes of inactivity
                    .maximumSize(100_000)                       // Protect memory by limiting to 100k keys
                    .build();
        }
    }

    @Override
    public String getDescription() {
        return "Advanced Rate Limit filter supporting multi-keyed isolation based on the Token Bucket algorithm.";
    }

    @Override
    public Payload process(Payload payload) throws FlowException {
        TokenBucket targetBucket = globalBucket;

        // 1. Identify key and assign bucket for PER_KEY mode
        if (values.getLimitType() == AdvancedRateLimitValues.LimitType.PER_KEY) {
            String targetKey = extractKey(payload, values.getKeyProperty());
            targetBucket = bucketCache.get(targetKey,
                    k -> new TokenBucket(values.getBurstCapacity(), values.getReplenishRate()));
        }

        // 2. Attempt token consumption (supports WAIT policy)
        boolean consumed = targetBucket.tryConsume(1);

        if (!consumed) {
            if (values.getRejectPolicy() == AdvancedRateLimitValues.RejectPolicy.WAIT) {
                // Throttling mode: Sleep and retry (Caution: excessive waiting can exhaust the CPU pool)
                try {
                    TimeUnit.MILLISECONDS.sleep(values.getWaitTimeoutMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                consumed = targetBucket.tryConsume(1);
            }
        }

        // 3. Final Rejection Logic
        if (!consumed) {
            log.warn("Rate Limit Exceeded! Policy: {}", values.getLimitType());
            throw new FlowException(FlowResultStatus.TOO_MANY_REQUESTS, "Too Many Requests. Rate limit exceeded.");
        }

        return payload;
    }

    private String extractKey(Payload payload, String propertyKey) {
        // Defense logic: Handle cases where the key is not configured in YAML
        if (propertyKey == null || propertyKey.trim().isEmpty()) {
            log.warn("Rate limit type is PER_KEY, but keyProperty is empty. Using 'DEFAULT_LIMIT_KEY'.");
            return "DEFAULT_LIMIT_KEY";
        }

        // Extract value from Event global properties
        Object value = payload.getHeader(propertyKey);

        if (value != null && !value.toString().trim().isEmpty()) {
            return value.toString().trim();
        }

        // Defense logic: Prevent bypassing the limit by omitting headers or attributes
        if (log.isDebugEnabled()) {
            log.debug("Rate limit key property [{}] is missing in the current Event. Using 'UNKNOWN_LIMIT_KEY'.", propertyKey);
        }
        return "UNKNOWN_LIMIT_KEY";
    }
}
