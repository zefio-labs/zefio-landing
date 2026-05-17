package io.zefio.gateway.http.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.deserializer.MediaTypeDeserializer;
import io.zefio.core.schema.dto.UpstreamValues;
import io.zefio.gateway.http.deserializer.HttpMethodDeserializer;
import io.zefio.gateway.http.serializer.HttpMethodSerializer;
import io.zefio.jdk.annotation.ZefioNotBlank;
import io.zefio.gateway.netty.deserializer.HandlerDefinitionListDeserializer;
import io.zefio.gateway.netty.dto.HandlerDefinition;
import io.zefio.gateway.netty.dto.NettyValues;
import io.zefio.gateway.netty.dto.PoolConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Configuration model for HTTP Upstream (Client) connections.
 * Orchestrates Netty settings, connection pooling, and HTTP routing parameters.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class HttpUpstreamValues extends UpstreamValues {

    @JsonUnwrapped
    private NettyValues upstream;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @ZefioNotBlank
    @Schema(description = "Destination host IP or domain. Critical network contract.",
            nullable = false, example = "api.zefio.io")
    protected String host;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonDeserialize(using = HandlerDefinitionListDeserializer.class)
    @Schema(description = "Netty handlers for the client pipeline.",
            nullable = true, type = "array", implementation = HandlerDefinition.class)
    protected List<HandlerDefinition> handlers = Lists.newArrayList();

    @JsonUnwrapped
    protected PoolConfig poolConfig;

    // -----------------------------------------------------------------------------------
    // HTTP Request/Response Formatting Configurations
    // -----------------------------------------------------------------------------------

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @JsonDeserialize(using = MediaTypeDeserializer.class)
    @Schema(description = "Content-Type for the request sent to target.",
            nullable = true, example = "application/json", defaultValue = "application/json")
    protected MediaType requestContentType = MediaType.APPLICATION_JSON;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @JsonDeserialize(using = MediaTypeDeserializer.class)
    @Schema(description = "Accept header for the upstream request. Defines expected response formats.",
            nullable = true, example = "application/json", defaultValue = "*/*")
    protected MediaType requestAccept = MediaType.ALL;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @JsonDeserialize(using = HttpMethodDeserializer.class)
    @JsonSerialize(using = HttpMethodSerializer.class)
    @Schema(description = "HTTP Verb used for the upstream request.",
            nullable = true, example = "POST", defaultValue = "GET")
    protected HttpMethod requestHttpMethod = HttpMethod.GET;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "SpEL-enabled query parameters for URI assembly.",
            nullable = true, example = "{\"svc\": \"auth\", \"ts\": \"#{T(java.lang.System).currentTimeMillis()}\"}")
    protected Map<String, String> requestQueryParams = Collections.emptyMap();

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "SpEL-enabled custom HTTP headers for the client request.",
            nullable = true, example = "{\"X-TID\": \"#{payload.trxID}\"}")
    protected Map<String, String> requestHeaderKeyValues = Collections.emptyMap();

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Target URI path with SpEL support for dynamic resource addressing.",
            nullable = true, example = "/api/v1/resource/#{body.id}", defaultValue = "/")
    protected String requestPath = "/";

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.GENERAL)
    @JsonSetter(nulls = Nulls.SKIP)
    @JsonDeserialize(using = MediaTypeDeserializer.class)
    @Schema(description = "Expected Content-Type of the response for internal mapping.",
            nullable = true, example = "application/json", defaultValue = "*/*")
    protected MediaType responseContentType = MediaType.ALL;
}
