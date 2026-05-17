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
import io.zefio.core.schema.dto.UpstreamValues;
import io.zefio.jdk.annotation.ZefioNotBlank;
import io.zefio.gateway.netty.deserializer.HandlerDefinitionListDeserializer;
import io.zefio.gateway.netty.dto.HandlerDefinition;
import io.zefio.gateway.netty.dto.NettyValues;
import io.zefio.gateway.netty.dto.PoolConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Configuration model for WebSocket Upstream (Client) nodes.
 * Manages target destination connectivity and client-side handshake logic.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class WebSocketUpstreamValues extends UpstreamValues {

    @JsonUnwrapped
    private NettyValues upstream;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @ZefioNotBlank
    @Schema(description = "Destination host IP or domain for the client connection.",
            nullable = false, example = "api.zefio.io")
    protected String host;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @ZefioNotBlank
    @Schema(description = "Destination port for the client connection.",
            nullable = false, example = "9000")
    protected Integer port;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @JsonDeserialize(using = DefaultStringDeserializer.class)
    @Schema(description = "Target URI path for the client-side WebSocket handshake.",
            nullable = true, example = "/ws", defaultValue = "/ws")
    protected String uri = "/ws";

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonDeserialize(using = HandlerDefinitionListDeserializer.class)
    @Schema(description = "Ordered list of custom handlers for the WebSocket client pipeline.",
            nullable = true, type = "array", implementation = HandlerDefinition.class)
    protected List<HandlerDefinition> handlers = Lists.newArrayList();

    @JsonUnwrapped
    protected PoolConfig poolConfig;

}
