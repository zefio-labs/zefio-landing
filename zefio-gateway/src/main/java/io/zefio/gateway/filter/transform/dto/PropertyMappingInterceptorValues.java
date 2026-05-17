package io.zefio.gateway.filter.transform.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.dto.InterceptorValues;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * Configuration for mapping properties between different metadata scopes.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PropertyMappingInterceptorValues extends InterceptorValues {

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "Mapping configuration (Key: Target attribute name, Value: Source attribute name)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "{\"file.req.remotePath\": \"http.req.x-remote-path\"}")
    private Map<String, String> mappings;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.GENERAL)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Whether to overwrite the target property if it already exists",
            nullable = false, example = "true", defaultValue = "true")
    private boolean overwrite = true;

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.GENERAL)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Whether to ignore the source property if it is null",
            nullable = false, example = "true", defaultValue = "true")
    private boolean ignoreNullSource = true;

    public boolean hasMappings() {
        return mappings != null && !mappings.isEmpty();
    }
}
