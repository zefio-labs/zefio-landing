package io.zefio.gateway.error.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.gateway.error.base.ErrorMessage;
import lombok.Data;

/**
 * Defines a rule for composing error messages at a specific byte offset in fixed-length payloads.
 */
@Data
public class OffsetMessageCompositionRule {

    public static class Fields {
        public static final String OFFSET = "offset";
        public static final String MODE = "mode";
    }

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "Composition mode: REQ (use request body), ERROR (use fault body), EMPTY (truncate). Defaults to appending after the request body.",
            nullable = true, example = "REQ", allowableValues = {"REQ", "ERROR", "EMPTY"})
    private ErrorMessage mode;

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "The starting byte offset for message composition.",
            nullable = true, example = "50")
    private Integer offset;
}
