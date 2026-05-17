package io.zefio.gateway.netty.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.gateway.netty.chunked.dto.ChunkAggregatorConfig;
import io.zefio.gateway.netty.chunked.dto.ChunkPaginationConfig;
import io.zefio.gateway.netty.chunked.dto.ChunkSplitterConfig;
import lombok.Data;

import java.util.Map;

/**
 * Metadata definition for a Netty pipeline handler.
 * Configures the class path and optional strategies for handling large or chunked messages.
 */
@JsonPropertyOrder(alphabetic = true)
@Data
public class HandlerDefinition {

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Full class path of the handler implementation. Immutable at runtime.",
            nullable = false, example = "io.zefio.gateway.tcp.handler.TcpMessageAggregatorHandler")
    protected String clazz;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Options for assembling large/chunked messages", nullable = true, implementation = ChunkAggregatorConfig.class)
    protected ChunkAggregatorConfig aggregator;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Options for splitting response chunks", nullable = true, implementation = ChunkSplitterConfig.class)
    protected ChunkSplitterConfig splitter;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Options for business-level paging and merging", nullable = true, implementation = ChunkPaginationConfig.class)
    protected ChunkPaginationConfig pagination;

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.GENERAL)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Extensible custom configurations for user-defined handlers", nullable = true)
    protected Map<String, Object> custom = Maps.newTreeMap();
}
