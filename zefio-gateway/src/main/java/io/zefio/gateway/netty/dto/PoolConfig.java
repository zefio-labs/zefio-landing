package io.zefio.gateway.netty.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import lombok.Data;

/**
 * Configuration for the Netty Channel Object Pool.
 * Orchestrates persistent connection management, background scaling, and resource eviction.
 */
@JsonPropertyOrder(alphabetic = true)
@Data
public class PoolConfig {

    // --- 1. Basic Pool Maintenance ---

    @AIOpsTuning(hotDeployable = true, min = "0", max = "5000", riskLevel = AIOpsTuning.RiskLevel.HIGH, category = AIOpsTuning.Category.RESOURCE_SCALE)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Maximum number of persistent clients to maintain in the pool. Set to 0 to disable pooling.",
            nullable = true, example = "100", defaultValue = "0")
    protected Integer poolMaxSize = 0;

    @AIOpsTuning(hotDeployable = true, min = "100", max = "10000", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.NETWORK_TIMEOUT)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Maximum time to wait for an available resource when the pool is exhausted (ms).",
            nullable = true, example = "1000", defaultValue = "500")
    protected Long poolMaxWaitMillis = 500L;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.GENERAL)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Resource acquisition strategy (true: LIFO - Last In First Out; false: FIFO).",
            nullable = true, example = "false", defaultValue = "true")
    protected Boolean lifo = true;

    // --- 2. Background Refill & Reconnection ---

    @AIOpsTuning(hotDeployable = true, min = "500", max = "30000", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.NETWORK_TIMEOUT)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Interval between reconnection attempts after a disconnection is detected (ms).",
            nullable = true, example = "2000", defaultValue = "1000")
    protected Long poolReconnectIntervalMillis = 1000L;

    @AIOpsTuning(hotDeployable = true, min = "100", max = "5000", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.RESOURCE_SCALE)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Interval between asynchronous pool refill attempts (ms). Controls the speed of background scaling.",
            nullable = true, example = "1000", defaultValue = "500")
    protected Long poolFillIntervalMillis = 500L;

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.RESOURCE_SCALE)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Enable Exponential Backoff for asynchronous pool refilling to prevent retry storms during target downtime.",
            nullable = true, example = "true", defaultValue = "true")
    protected Boolean useFillBackoff = true;

    // --- 3. Evictor (Idle Resource Cleanup) ---

    @AIOpsTuning(hotDeployable = true, min = "5000", max = "300000", riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.RESOURCE_SCALE)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Cycle interval for checking idle connections (ms). Set to -1 to disable the evictor thread.",
            nullable = true, example = "30000", defaultValue = "10000")
    protected Long timeBetweenEvictionRunsDurationMillis = 10000L;

    @AIOpsTuning(hotDeployable = true, min = "10000", max = "3600000", riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.RESOURCE_SCALE)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Maximum time a connection can remain idle before being eligible for eviction (ms).",
            nullable = true, example = "120000", defaultValue = "60000")
    protected Long minEvictableIdleDurationMillis = 60000L;

    // --- 4. Transient (One-time) Connection Settings ---

    @AIOpsTuning(hotDeployable = true, min = "0", max = "10", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Maximum retries for a single request when pooling is disabled.",
            nullable = true, example = "5", defaultValue = "3")
    protected Integer onceMaxRetries = 3;

    @AIOpsTuning(hotDeployable = true, min = "1000", max = "30000", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.NETWORK_TIMEOUT)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Total timeout for a single connection attempt when pooling is disabled (ms).",
            nullable = true, example = "5000", defaultValue = "3000")
    protected Long onceTryTimeoutMillis = 3000L;

    @AIOpsTuning(hotDeployable = true, min = "100", max = "10000", riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.NETWORK_TIMEOUT)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Initial delay for the backoff strategy during transient reconnection (ms).",
            nullable = true, example = "2000", defaultValue = "1000")
    protected Long onceBackoffDelayMillis = 1000L;
}
