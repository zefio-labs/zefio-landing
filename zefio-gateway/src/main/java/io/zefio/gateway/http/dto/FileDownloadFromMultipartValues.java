package io.zefio.gateway.http.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.dto.InterceptorValues;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration for dynamic file downloads where the directory path is extracted via byte offsets
 * from the incoming request payload.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class FileDownloadFromMultipartValues extends InterceptorValues {

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Zero-based byte offset in the request payload where the directory path string starts.",
            nullable = true, example = "1", defaultValue = "0")
    protected Integer dirStart = 0;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "The number of bytes to read from the 'dirStart' offset to extract the directory path.",
            nullable = true, example = "10", defaultValue = "0")
    protected Integer dirLength = 0;

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.GENERAL)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "If true, returns the processing result summary in JSON format.",
            nullable = true, example = "true", defaultValue = "true")
    private boolean returnJson = true;
}
