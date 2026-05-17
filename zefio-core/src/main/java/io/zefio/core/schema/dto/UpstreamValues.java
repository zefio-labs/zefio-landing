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
 * Configuration schema for Upstream (Outbound) modules.
 * Defines how to handle encoding when sending requests to and receiving responses
 * from external target systems.
 */
@JsonPropertyOrder(alphabetic = true)
@Data
public class UpstreamValues implements RequestEncodingSupport, ResponseEncodingSupport {

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonDeserialize(using = CharsetDeserializer.class)
    @Schema(description = "Request Encoding. If defined, the engine performs transcoding " +
            "before sending to the upstream target.",
            nullable = true, example = "UTF-8")
    protected Charset requestEncoding;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonDeserialize(using = CharsetDeserializer.class)
    @Schema(description = "Response Encoding. If null, follows the requestEncoding. " +
            "Ignored for Fire-and-Forget flows.",
            nullable = true, example = "UTF-8")
    protected Charset responseEncoding;

    /**
     * Returns the target request encoding.
     * Returns null if no specific transcoding is required (inherits from previous stage).
     */
    @JsonIgnore
    public Charset getRequestEncoding() {
        return this.requestEncoding;
    }

    /**
     * Returns the target response encoding.
     */
    @JsonIgnore
    public Charset getResponseEncoding() {
        return this.responseEncoding;
    }
}
