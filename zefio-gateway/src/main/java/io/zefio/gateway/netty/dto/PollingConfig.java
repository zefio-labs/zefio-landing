package io.zefio.gateway.netty.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import lombok.Data;

/**
 * Configuration for health-check polling (Heartbeats).
 * Enables the engine to monitor connection viability and trigger automatic reconnections.
 */
@JsonPropertyOrder(alphabetic = true)
@Data
public class PollingConfig {

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "Request message for polling (Raw string excluding Length/Delimiter headers). Must match server expectations.",
            nullable = true, example = "HDRREQPOLL")
    protected String request;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "Expected response message for polling. If the response does not match, the health check fails.",
            nullable = true, example = "HDRRESPOLL")
    protected String response;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "Timestamp format to append to the polling response. Useful for identifying stale heartbeats.",
            nullable = true, example = "MMddHHmmss")
    protected String format;

    @AIOpsTuning(hotDeployable = true, min = "1000", max = "300000", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.NETWORK_TIMEOUT)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Interval frequency between polling attempts in milliseconds. Tunable to reduce network overhead.",
            nullable = true, example = "30000", defaultValue = "30000")
    protected Long duration = 30000L;

    @AIOpsTuning(hotDeployable = true, min = "1", max = "50", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.RESOURCE_SCALE)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Maximum consecutive failures before a reconnection is triggered (duration * reconnectCount).",
            nullable = true, example = "5", defaultValue = "5")
    protected Integer reconnectCount = 5;
}
