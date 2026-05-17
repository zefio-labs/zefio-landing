package io.zefio.gateway.error.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import lombok.Data;

/**
 * Defines a rule for overriding a specific value at a targeted byte offset in fixed-length payloads.
 */
@Data
public class OffsetValueOverride {

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "The value to be written to the specified location.",
            nullable = true, example = "ZZZ")
    private String value;

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "The 0-based byte offset where the value will be overwritten.",
            nullable = true, example = "0")
    private Integer offset;
}
