package io.zefio.gateway.error.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import lombok.Data;

/**
 * Defines a rule for overriding a specific value at a targeted key in structured payloads.
 */
@Data
public class KeyedValueOverride {

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "The value to be written to the specified location.",
            nullable = true, example = "ZZZ")
    private String value;

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "The key or tag in the payload where the value will be overwritten.",
            nullable = true, example = "key1")
    private String key;
}
