package io.zefio.gateway.http.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.dto.InterceptorValues;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.Map;

/**
 * Configuration for injecting custom values into HTTP headers at the Request or Response stage.
 * Supports dynamic expression evaluation (e.g., SpEL) for high-flexibility header management.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class HttpHeaderValeSetterValues extends InterceptorValues {

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "A map of header names and their corresponding values (or SpEL expressions) to be injected.",
            nullable = true, example = "{\"X-AUTH-TOKEN\": \"#{event.properties['token']}\", \"X-GATEWAY-ID\": \"Zefio-01\"}")
    protected Map<String, String> headerKeyValues = Collections.emptyMap();

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.GENERAL)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Defines the pipeline stage for header injection: REQUEST (incoming) or RESPONSE (outgoing).",
            nullable = true, example = "RESPONSE", defaultValue = "REQUEST")
    protected HttpHeaderType targetType = HttpHeaderType.REQUEST;

    public enum HttpHeaderType { REQUEST, RESPONSE }
}
