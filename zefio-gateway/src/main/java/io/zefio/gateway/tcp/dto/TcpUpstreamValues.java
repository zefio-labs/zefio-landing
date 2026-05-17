package io.zefio.gateway.tcp.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
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
 * Configuration model for TCP Upstream connections.
 * Manages host targeting, connection pooling logic, and protocol-specific pipeline handlers.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class TcpUpstreamValues extends UpstreamValues {

    @JsonUnwrapped
    @Schema(description = "Netty-based Upstream network settings", nullable = true)
    private NettyValues upstream;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @ZefioNotBlank
    @Schema(description = "Destination host IP or domain for the TCP connection.",
            nullable = false, example = "localhost")
    protected String host;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonDeserialize(using = HandlerDefinitionListDeserializer.class)
    @Schema(description = "List of Netty pipeline handlers used to frame and parse raw TCP data.",
            nullable = true, type = "array", implementation = HandlerDefinition.class)
    protected List<HandlerDefinition> handlers = Lists.newArrayList();

    @JsonUnwrapped
    protected PoolConfig poolConfig;
}
