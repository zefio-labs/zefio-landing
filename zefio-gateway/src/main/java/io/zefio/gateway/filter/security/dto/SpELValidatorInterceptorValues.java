package io.zefio.gateway.filter.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.schema.dto.InterceptorValues;
import io.zefio.jdk.annotation.ZefioNotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration DTO for the SpEL Validator Interceptor.
 * Used to enforce business guardrails by evaluating boolean expressions.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SpELValidatorInterceptorValues extends InterceptorValues {

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.HIGH, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @ZefioNotBlank
    @Schema(description = "SpEL condition expression to evaluate (rejection occurs if result is false)",
            example = "#{body['AMOUNT'] >= 100}")
    private String condition;

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "Error message to return when the condition fails",
            defaultValue = "Validation failed")
    private String errorMessage = "Validation failed for condition";

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "Error status code to throw upon failure (FlowResultStatus)",
            defaultValue = "VALIDATION_FAILED")
    private String errorStatus = FlowResultStatus.VALIDATION_FAILED.name();
}
