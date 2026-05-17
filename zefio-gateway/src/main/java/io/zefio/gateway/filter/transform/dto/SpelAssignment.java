package io.zefio.gateway.filter.transform.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.jdk.annotation.ZefioNotBlank;
import lombok.Data;

/**
 * Model representing a single data mutation rule via SpEL.
 */
@JsonPropertyOrder(alphabetic = true)
@Data
@Schema(description = "Model for a single SpEL assignment rule")
public class SpelAssignment {

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @ZefioNotBlank
    @Schema(description = "Destination path where data will be stored (L-Value)",
            example = "body.fullName", requiredMode = Schema.RequiredMode.REQUIRED)
    private String target;

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.HIGH, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @ZefioNotBlank
    @Schema(description = "SpEL expression to calculate the value (R-Value)",
            example = "#{body.firstName + ' ' + body.lastName}", requiredMode = Schema.RequiredMode.REQUIRED)
    private String expression;

    @Schema(description = "Description of the assignment rule for traceability",
            example = "Combine and create full name field")
    private String description;
}
