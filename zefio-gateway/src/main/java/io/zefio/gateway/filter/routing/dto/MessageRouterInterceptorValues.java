package io.zefio.gateway.filter.routing.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.dto.InterceptorValues;
import io.zefio.jdk.annotation.ZefioNotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Configuration model for the Message Router filter.
 * Defines a prioritized list of rules evaluated in the order they are defined.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class MessageRouterInterceptorValues extends InterceptorValues {

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @ZefioNotBlank
    @Schema(description = "List of unified routing rules applied in order of priority. Immutable at runtime.",
            nullable = false, type = "array", implementation = MessageRoutingRule.class)
    @JsonDeserialize(using = MessageRouterDeserializer.class)
    private List<MessageRoutingRule> routingRules;
}
