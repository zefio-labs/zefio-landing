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
 * Configuration schema for Two-way Ingress modules.
 * Manages encoding for both the ingress request and the egress response.
 */
@JsonPropertyOrder(alphabetic = true)
@Data
public class TwowayIngressValues implements RequestEncodingSupport, ResponseEncodingSupport {

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonDeserialize(using = CharsetDeserializer.class)
    @Schema(description = "Character encoding for the incoming request",
            nullable = true, example = "UTF-8", defaultValue = "System Default")
    protected Charset requestEncoding;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonDeserialize(using = CharsetDeserializer.class)
    @Schema(description = "Character encoding for the outgoing response",
            nullable = true, example = "UTF-8", defaultValue = "System Default")
    protected Charset responseEncoding;

    @JsonIgnore
    public Charset getRequestEncoding() {
        return requestEncoding != null ? requestEncoding : Charset.forName(System.getProperty("file.encoding"));
    }

    @JsonIgnore
    public Charset getResponseEncoding() {
        return responseEncoding != null ? responseEncoding : Charset.forName(System.getProperty("file.encoding"));
    }
}
