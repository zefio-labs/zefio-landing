package io.zefio.gateway.error.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import lombok.Data;

/**
 * Defines a rule for replacing specific error codes in structured payloads (JSON/XML).
 */
@Data
public class KeyedErrorCodeReplacementRule {

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "The target error code to match. Wildcards allowed: 5xx, xxx, etc.",
            nullable = true, example = "5xx")
    private String errorCode;

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "The new error code to be injected.",
            nullable = true, example = "AX999")
    private String newCode;

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "The location to overwrite the error code (JSON key or XML tag).",
            nullable = true, example = "key1")
    private String key;
}
