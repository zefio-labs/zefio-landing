package io.zefio.gateway.error.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.gateway.error.base.ErrorMessage;
import lombok.Data;

/**
 * Defines a rule for composing error messages at a specific key location in structured payloads.
 */
@Data
public class KeyedMessageCompositionRule {

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "Composition mode: REQ (use request body), ERROR (use fault body), EMPTY (truncate). Defaults to appending after the request body.",
            nullable = true, example = "REQ", allowableValues = {"REQ", "ERROR", "EMPTY"})
    private ErrorMessage mode;

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "The starting location for composition (JSON key or XML tag).",
            nullable = true, example = "key1")
    private String key;
}
