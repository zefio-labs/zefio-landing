package io.zefio.gateway.filter.resilience.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.dto.InterceptorValues;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration DTO for the Advanced Rate Limit filter.
 * Protects downstream systems by controlling the flow of traffic using a Token Bucket algorithm.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AdvancedRateLimitValues extends InterceptorValues {
    public enum LimitType {
        GLOBAL,     // Limit based on the total flow aggregation
        PER_KEY     // Independent limit per specific key (IP, Header, etc.)
    }

    public enum RejectPolicy {
        FAIL_FAST,  // Immediately return 429 TOO_MANY_REQUESTS (Recommended)
        WAIT        // Block and wait up to waitTimeoutMillis until tokens are available (Throttling)
    }

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "Scope of the rate limit. Changing this requires cache reallocation.", defaultValue = "GLOBAL")
    private LimitType limitType = LimitType.GLOBAL;

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "The exact property key loaded in the Event to be used for identification (e.g., 'http.req.x-base-key'). Valid only for PER_KEY.")
    private String keyProperty = "";

    @AIOpsTuning(hotDeployable = true, min = "1", max = "100000", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.RESOURCE_SCALE)
    @Schema(description = "Number of tokens added per second (Average throughput).")
    private int replenishRate = 1000;

    @AIOpsTuning(hotDeployable = true, min = "1", max = "100000", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.RESOURCE_SCALE)
    @Schema(description = "Maximum capacity of the bucket (Burst traffic allowance).")
    private int burstCapacity = 1500;

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "Strategy when no tokens are available.", defaultValue = "FAIL_FAST")
    private RejectPolicy rejectPolicy = RejectPolicy.FAIL_FAST;

    @AIOpsTuning(hotDeployable = true, min = "0", max = "60000", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.NETWORK_TIMEOUT)
    @Schema(description = "Maximum blocking time in milliseconds when the policy is set to WAIT.")
    private long waitTimeoutMillis = 500L;
}
