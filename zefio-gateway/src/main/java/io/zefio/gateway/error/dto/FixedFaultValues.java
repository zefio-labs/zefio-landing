package io.zefio.gateway.error.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.schema.dto.InterceptorValues;
import io.zefio.gateway.error.deserializer.OffsetErrorCodeReplacementRuleDeserializer;
import io.zefio.gateway.error.deserializer.OffsetMessageCompositionRuleDeserializer;
import io.zefio.gateway.error.deserializer.OffsetValueDeserializer;
import io.zefio.gateway.error.dto.common.OffsetErrorCodeReplacementRule;
import io.zefio.gateway.error.dto.common.OffsetMessageCompositionRule;
import io.zefio.gateway.error.dto.common.OffsetValueOverride;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Configuration for fault handling in fixed-length message formats.
 * Used within Ingress or Upstream flows to manipulate error responses based on offsets.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class FixedFaultValues extends InterceptorValues {

    @JsonDeserialize(using = OffsetValueDeserializer.class)
    @Schema(description = "List of configurations to override values at fixed offsets within the message body",
            nullable = true, example = "[{\"offset\": 0, \"value\": \"ZZZ\"}, {\"offset\": 10, \"value\": \"ABCD\"}]",
            type = "array", implementation = OffsetValueOverride.class)
    protected List<OffsetValueOverride> valueOverrides;

    @JsonDeserialize(using = OffsetErrorCodeReplacementRuleDeserializer.class)
    @Schema(description = "Rules to replace values at specific offsets based on the identified error code",
            nullable = true, example = "[{\"errorCode\": \"500\", \"offset\": 40, \"newCode\": \"AX999\"}, {\"errorCode\": \"xxx\", \"offset\": 40, \"newCode\": \"AX990\"}]",
            type = "array", implementation = OffsetErrorCodeReplacementRule.class)
    protected List<OffsetErrorCodeReplacementRule> errorCodeRules;

    @JsonDeserialize(using = OffsetMessageCompositionRuleDeserializer.class)
    @Schema(description = "Defines how the message is composed when an error occurs in the flow",
            nullable = true, example = "{\"offset\": 50, \"mode\": \"REQ\"}")
    protected OffsetMessageCompositionRule messageRule;

}
