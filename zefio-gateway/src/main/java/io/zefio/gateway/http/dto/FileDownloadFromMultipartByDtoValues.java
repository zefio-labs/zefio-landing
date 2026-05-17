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
 * Configuration for file downloads extracted from multipart requests using DTO-based target paths.
 * Allows for dynamic directory management at runtime.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class FileDownloadFromMultipartByDtoValues extends InterceptorValues {

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.HIGH, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "Target directory path on the server where downloaded files will be stored.",
            nullable = true, example = "/data/downloads/target")
    private String targetDirectory;

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.GENERAL)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "If true, the processing summary (file names, status) is returned as a JSON response body.",
            nullable = true, example = "true", defaultValue = "true")
    private boolean returnJson = true;
}
