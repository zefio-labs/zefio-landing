package io.zefio.gateway.filter.modify.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import lombok.Data;

/**
 * Defines a single modification task for fixed-length binary or text data.
 */
@JsonPropertyOrder(alphabetic = true)
@Data
public class FixedBinaryModifierChild {

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "The byte offset for modification. Used in MODIFY_BODY and PROPERTY_TO_BODY modes.",
            nullable = true, example = "0", defaultValue = "0")
    private Integer offset = 0;

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "In MODIFY_BODY mode: Literal value or property key. In PROPERTY_TO_BODY/BODY_TO_PROPERTY modes: Target property key.",
            nullable = false, example = "userName")
    private String valueOrProperty;

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "The length of data to be extracted. Required only for BODY_TO_PROPERTY mode.",
            nullable = false, example = "10")
    private Integer length;
}
