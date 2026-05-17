package io.zefio.gateway.error.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.schema.dto.InterceptorValues;
import io.zefio.gateway.error.deserializer.KeyedValueOverrideDeserializer;
import io.zefio.gateway.error.deserializer.KeyedErrorCodeReplacementRuleDeserializer;
import io.zefio.gateway.error.dto.common.KeyedMessageCompositionRule;
import io.zefio.gateway.error.dto.common.KeyedValueOverride;
import io.zefio.gateway.error.dto.common.KeyedErrorCodeReplacementRule;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Configuration for fault handling in JSON message formats.
 * Enables key-based manipulation of error data within the Ingress/Upstream pipeline.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class JsonFaultValues extends InterceptorValues {

    @JsonDeserialize(using = KeyedValueOverrideDeserializer.class)
    @Schema(description = "List of configurations to override values for specific keys within the JSON structure",
            nullable = true, example = "[{\"key\": \"key1\", \"value\": \"ZZZ\"}, {\"key\": \"key2\", \"value\": \"ABCD\"}]",
            type = "array", implementation = KeyedValueOverride.class)
    protected List<KeyedValueOverride> valueOverrides;

    @JsonDeserialize(using = KeyedErrorCodeReplacementRuleDeserializer.class)
    @Schema(description = "Rules to replace values for specific keys based on the identified error code",
            nullable = true, example = "[{\"errorCode\": \"500\", \"key\": \"key1\", \"newCode\": \"AX999\"}, {\"errorCode\": \"xxx\", \"key\": \"key2\", \"newCode\": \"AX990\"}]",
            type = "array", implementation = KeyedErrorCodeReplacementRule.class)
    protected List<KeyedErrorCodeReplacementRule> errorCodeRules;

    @Schema(description = "Strategy for processing and composing the message payload during error scenarios",
            nullable = true, example = "{\"key\": \"key1\", \"mode\": \"REQ\"}")
    protected KeyedMessageCompositionRule messageRule;

}
