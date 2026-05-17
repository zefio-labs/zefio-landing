package io.zefio.core.system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.dto.InterceptorValues;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration DTO for the Async Suspend Interceptor.
 * Responsible for pausing the current pipeline thread and parking the transaction in memory
 * until a corresponding async response triggers the resume interceptor.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsyncSuspendInterceptorValues extends InterceptorValues {

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "Alternative bridge key property name used to park the transaction. Defaults to Event.getTrxID() if left empty.",
            nullable = true, example = "CORRELATION_ID")
    private String bridgeKeyProperty;

    @AIOpsTuning(hotDeployable = true, min = "1000", max = "120000", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.NETWORK_TIMEOUT)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Maximum duration in milliseconds to keep the transaction parked in memory before throwing a TimeoutException.",
            nullable = true, defaultValue = "30000", example = "30000")
    private Long timeout = 30000L;

}
