package io.zefio.gateway.netty.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.jdk.annotation.ZefioNotBlank;
import lombok.Data;

/**
 * Global Netty configuration parameters.
 * Tunes socket behavior, resource allocation, and transaction lifecycle management.
 */
@Data
public class NettyValues {

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @ZefioNotBlank
    @Schema(description = "Server port for Ingress or destination port for Upstream",
            nullable = false, example = "9000")
    protected Integer port;

    @AIOpsTuning(hotDeployable = true, restartRequired = true, min = "1", max = "1024", riskLevel = AIOpsTuning.RiskLevel.HIGH, category = AIOpsTuning.Category.RESOURCE_SCALE)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Number of Netty worker threads. Changes typically require a restart of the EventLoopGroup.",
            nullable = true, example = "16", defaultValue = "Available Processors × 2")
    protected Integer workThreadCount = Runtime.getRuntime().availableProcessors() * 2;

    @AIOpsTuning(hotDeployable = true, min = "0", max = "30000", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.NETWORK_TIMEOUT)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "TCP connection timeout (ms)",
            nullable = true, example = "5000", defaultValue = "0 (No timeout)")
    protected Integer connectTimeout = 0;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.GENERAL)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Socket option: SO_KEEPALIVE",
            nullable = true, example = "true", defaultValue = "true")
    protected Boolean soKeepAlive = true;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.GENERAL)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Socket option: TCP_NODELAY (Nagle's algorithm disabled)",
            nullable = true, example = "true", defaultValue = "true")
    protected Boolean tcpNoDelay = true;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.GENERAL)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Socket option: SO_REUSEADDR",
            nullable = true, example = "true", defaultValue = "true")
    protected Boolean soReUseAddr = true;

    @AIOpsTuning(hotDeployable = true, min = "0", max = "300000", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.NETWORK_TIMEOUT)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Idle connection management for TCP or Request Timeout for HTTP/WS (ms)",
            nullable = true, example = "60000", defaultValue = "0 (Disabled)")
    protected Integer readTimeout = 0;

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Enable business-level session persistence (Keep-Alive)",
            nullable = true, defaultValue = "true")
    protected Boolean keepAlive = true;

    @AIOpsTuning(hotDeployable = true, min = "1024", max = "104857600", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.RESOURCE_SCALE)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Maximum content length allowed (bytes). Prevents OOM by rejecting large payloads.",
            nullable = true, example = "10485760", defaultValue = "10485760 (10MB)")
    protected Integer maxContentLength = 10 * 1024 * 1024;

    @AIOpsTuning(hotDeployable = true, min = "1000", max = "120000", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.NETWORK_TIMEOUT)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Response wait duration for Request-Response patterns (ms)",
            nullable = true, example = "30000", defaultValue = "30000")
    protected Long transactionTimeoutMillis = 30000L;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Strategy for matching incoming responses to upstream requests.",
            nullable = true, example = "TELEGRAM", defaultValue = "TELEGRAM")
    protected ResponseMatchingType responseMatchingType = ResponseMatchingType.TELEGRAM;

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.GENERAL)
    @Schema(description = "Health check polling configuration (requires both request and response to be active)",
            nullable = true, implementation = PollingConfig.class)
    protected PollingConfig polling;

    public PollingConfig getPolling() {
        return polling == null ? new PollingConfig() : polling;
    }
}
