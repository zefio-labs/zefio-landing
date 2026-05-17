package io.zefio.gateway.filter.modify.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import lombok.Data;

/**
 * Defines a single modification task for JSON data using JsonPath selectors.
 */
@JsonPropertyOrder(alphabetic = true)
@Data
public class JsonValueModifierChild {

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "The JsonPath selector targeting the value to be modified or extracted.",
            nullable = false, example = "$.user.name")
    private String jsonPath;

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "The replacement value or the property key for metadata reference.",
            nullable = false, example = "userName")
    private String valueOrProperty;
}
