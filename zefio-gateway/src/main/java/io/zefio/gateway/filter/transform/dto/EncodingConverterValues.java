package io.zefio.gateway.filter.transform.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.deserializer.CharsetDeserializer;
import io.zefio.jdk.annotation.ZefioNotBlank;
import lombok.Data;

import java.nio.charset.Charset;

/**
 * DTO for character set conversion.
 * Changing this at runtime is high risk as it alters the binary representation of data.
 */
@JsonPropertyOrder(alphabetic = true)
@Data
public class EncodingConverterValues {

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @ZefioNotBlank
    @JsonDeserialize(using = CharsetDeserializer.class)
    @Schema(description = "The target Encoding to convert the payload to",
            nullable = false, example = "UTF-8")
    protected Charset changeEncoding;
}
