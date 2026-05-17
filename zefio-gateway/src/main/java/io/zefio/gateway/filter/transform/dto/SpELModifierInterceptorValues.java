package io.zefio.gateway.filter.transform.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.dto.InterceptorValues;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Configuration DTO for the SpEL Modifier Interceptor.
 * Enables powerful data orchestration and transformation through sequential assignments.
 */
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder(alphabetic = true)
@Data
@Schema(description = "Configuration model for SpEL-based data mutation.")
public class SpELModifierInterceptorValues extends InterceptorValues {

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.HIGH, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(
            description = "List of data assignment sequences to perform. Executed sequentially.",
            nullable = false,
            type = "array",
            implementation = SpelAssignment.class,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private List<SpelAssignment> assignments;
}
