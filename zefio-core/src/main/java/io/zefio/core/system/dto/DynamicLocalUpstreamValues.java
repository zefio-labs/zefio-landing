package io.zefio.core.system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.dto.UpstreamValues;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration DTO for Dynamic Local Upstream.
 * Enables intelligent routing by determining the target sub-flow dynamically at runtime
 * using a SpEL expression evaluated against the current payload.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DynamicLocalUpstreamValues extends UpstreamValues {

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "SpEL expression evaluated at runtime to determine the exact name of the target Flow to invoke.",
            nullable = false, example = "#{'flow-bank-' + body['bankCode']}")
    private String targetFlowExpression;

    @AIOpsTuning(hotDeployable = true, min = "1000", max = "120000", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.NETWORK_TIMEOUT)
    @Schema(description = "Maximum execution time allowed for the dynamically invoked sub-flow (ms).",
            nullable = true, defaultValue = "30000", example = "30000")
    private Long timeout = 30000L;

    /**
     * Helper method to safely return the configured timeout, defaulting to 30,000ms if invalid or missing.
     */
    public long getTimeoutOrDefault() {
        return (timeout != null && timeout > 0) ? timeout : 30000L;
    }
}
