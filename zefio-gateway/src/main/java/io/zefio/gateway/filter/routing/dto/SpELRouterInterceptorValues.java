package io.zefio.gateway.filter.routing.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.dto.InterceptorValues;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Configuration model for SpEL-based Content-Based Routing (CBR).
 * Evaluates conditions sequentially. Once a condition is met (First-Hit-Win),
 * control is delegated to the corresponding module.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "Configuration for SpEL-based Intelligent Routing. " +
        "Rules are evaluated sequentially, delegating to the target module of the first matching rule.")
public class SpELRouterInterceptorValues extends InterceptorValues {

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(
            description = "A prioritized list of routing sequences. Order is critical (First-Hit-Win). Immutable at runtime.",
            nullable = false,
            type = "array",
            implementation = SpELRoutingRule.class,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private List<SpELRoutingRule> routingRules;
}
