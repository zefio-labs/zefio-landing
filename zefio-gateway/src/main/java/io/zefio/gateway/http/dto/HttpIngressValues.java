package io.zefio.gateway.http.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.deserializer.MediaTypeDeserializer;
import io.zefio.core.schema.dto.TwowayIngressValues;
import io.zefio.gateway.netty.deserializer.HandlerDefinitionListDeserializer;
import io.zefio.gateway.netty.dto.HandlerDefinition;
import io.zefio.gateway.netty.dto.NettyValues;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.MediaType;

import java.util.List;

/**
 * Global configuration values for HTTP Ingress endpoints.
 * Integrates Netty network options and pipeline handler definitions.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class HttpIngressValues extends TwowayIngressValues {

    @JsonUnwrapped
    @Schema(description = "Netty-specific settings for the HTTP Ingress server", nullable = true)
    private NettyValues ingress;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonDeserialize(using = HandlerDefinitionListDeserializer.class)
    @Schema(description = "Ordered list of handlers to be registered in the Netty pipeline. Cannot be modified at runtime.",
            nullable = true, type = "array", implementation = HandlerDefinition.class)
    protected List<HandlerDefinition> handlers = Lists.newArrayList();

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.GENERAL)
    @JsonSetter(nulls = Nulls.SKIP)
    @JsonDeserialize(using = MediaTypeDeserializer.class)
    @Schema(description = "Default Content-Type for responses",
            nullable = true, example = "application/json, text/plain", defaultValue = "*/*")
    protected MediaType responseContentType = MediaType.ALL;
}
