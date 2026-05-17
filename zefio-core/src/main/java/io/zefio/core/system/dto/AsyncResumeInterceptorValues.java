package io.zefio.core.system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.dto.InterceptorValues;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration DTO for the Async Resume Interceptor.
 * Responsible for waking up a suspended pipeline when an asynchronous response is received.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsyncResumeInterceptorValues extends InterceptorValues {

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "Alternative bridge key property name used to correlate asynchronous responses. Defaults to Event.getTrxID() if left empty.",
            nullable = true, example = "CORRELATION_ID")
    private String bridgeKeyProperty;

}
