package io.zefio.gateway.websocket.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.deserializer.DefaultStringDeserializer;
import io.zefio.core.schema.dto.TwowayIngressValues;
import io.zefio.jdk.annotation.ZefioNotBlank;
import io.zefio.gateway.netty.deserializer.HandlerDefinitionListDeserializer;
import io.zefio.gateway.netty.dto.HandlerDefinition;
import io.zefio.gateway.netty.dto.NettyValues;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Configuration model for WebSocket Ingress (Server) nodes.
 * Manages server-side handshake paths and pipeline configurations.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class WebSocketIngressValues extends TwowayIngressValues {

    @JsonUnwrapped
    private NettyValues ingress;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @ZefioNotBlank
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Server port for WebSocket Ingress. Critical protocol entry point.",
            nullable = false, example = "9000")
    protected Integer port = 0;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @JsonDeserialize(using = DefaultStringDeserializer.class)
    @Schema(description = "Context path for the WebSocket upgrade handshake (e.g., /ws). Must match client expectations.",
            nullable = true, example = "/ws", defaultValue = "/ws")
    protected String contextPath = "/ws";

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonDeserialize(using = HandlerDefinitionListDeserializer.class)
    @Schema(description = "Ordered list of custom Netty handlers for the WebSocket server pipeline. Immutable at runtime.",
            nullable = true, type = "array", implementation = HandlerDefinition.class)
    protected List<HandlerDefinition> handlers = Lists.newArrayList();

}
