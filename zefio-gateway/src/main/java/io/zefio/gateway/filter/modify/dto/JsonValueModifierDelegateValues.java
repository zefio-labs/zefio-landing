package io.zefio.gateway.filter.modify.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.dto.InterceptorValues;
import io.zefio.gateway.filter.modify.deserializer.JsonValueModifierChildDeserializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Configuration for the JSON modification delegate.
 * Manages a collection of JsonPath-based mutation rules.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class JsonValueModifierDelegateValues extends InterceptorValues {

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.HIGH, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @JsonDeserialize(using = JsonValueModifierChildDeserializer.class)
    @Schema(description = "A list of JsonValueModifierChild modification rules.",
            nullable = false, type = "array", implementation = JsonValueModifierChild.class)
    private List<JsonValueModifierChild> children;

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Used in BODY_TO_PROPERTY mode. If true, the targeted JSON node is deleted after extraction; if false, it is retained.",
            nullable = true, example = "true", defaultValue = "true")
    private Boolean removeExtracted = true;

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "The direction of the data transfer: PROPERTY_TO_BODY, BODY_TO_PROPERTY, or MODIFY_BODY.",
            nullable = true, defaultValue = "MODIFY_BODY")
    private ValueModifierDirection direction = ValueModifierDirection.MODIFY_BODY;
}
