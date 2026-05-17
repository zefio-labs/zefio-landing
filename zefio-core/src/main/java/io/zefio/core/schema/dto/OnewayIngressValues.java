package io.zefio.core.schema.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.deserializer.CharsetDeserializer;
import lombok.Data;

import java.nio.charset.Charset;

/**
 * Configuration schema for One-way Ingress modules.
 * Focuses on defining the character set for the incoming request body.
 */
@JsonPropertyOrder(alphabetic = true)
@Data
public class OnewayIngressValues implements RequestEncodingSupport {

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonDeserialize(using = CharsetDeserializer.class)
    @Schema(description = "Character encoding for the incoming request",
            nullable = true, example = "UTF-8", defaultValue = "System Default")
    protected Charset requestEncoding;

    /**
     * Returns the configured request encoding or the system default if null.
     */
    @JsonIgnore
    public Charset getRequestEncoding() {
        return requestEncoding != null ? requestEncoding : Charset.forName(System.getProperty("file.encoding"));
    }
}
