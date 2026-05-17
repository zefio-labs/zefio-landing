package io.zefio.gateway.tcp.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.dto.TwowayIngressValues;
import io.zefio.gateway.netty.deserializer.HandlerDefinitionListDeserializer;
import io.zefio.gateway.netty.dto.HandlerDefinition;
import io.zefio.gateway.netty.dto.NettyValues;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Configuration model for TCP Ingress.
 * Combines standard two-way ingress properties with Netty network settings
 * and low-level pipeline handler definitions.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class TcpIngressValues extends TwowayIngressValues {

    @JsonUnwrapped
    @Schema(description = "Netty-based Ingress network settings (ports, threads, timeouts)", nullable = true)
    protected NettyValues ingress;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonDeserialize(using = HandlerDefinitionListDeserializer.class)
    @Schema(description = "Ordered list of Netty handlers (decoders/encoders) to be registered in the pipeline. Immutable at runtime.",
            nullable = true, type = "array", implementation = HandlerDefinition.class)
    protected List<HandlerDefinition> handlers = Lists.newArrayList();
}
