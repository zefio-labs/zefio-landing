package io.zefio.gateway.filter.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.payload.ExchangePattern;
import lombok.Data;

/**
 * Configuration model for an individual SpEL routing rule.
 * Allows for dynamic branching based on payload content, infrastructure properties, or complex logic.
 */
@Data
@Schema(description = "Configuration for SpEL-based intelligent routing rules.")
public class SpELRoutingRule {

    @Schema(description = "Rule identifier for audit logs and debugging visibility.",
            example = "Bank_A_Routing_Rule")
    private String name;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "The SpEL expression used to determine the branch.\n" +
            "1. **Content-based**: #{body.bankCode == '004'}\n" +
            "2. **Infrastructure-based**: #{payload.headers['http.req.path'].startsWith('/api/v1')}\n" +
            "3. **Complex Logic**: #{body.amount > 1000000 and payload.headers.vipStatus == 'Y'}",
            example = "#{body.TARGET_VAL == 'BANK_A'}",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String condition;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "The ID of the target module to call if the condition is met. " +
            "It is highly recommended to reference module names declared in endpoints.yaml.",
            example = "upstream-bank-a",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String refModuleName;

    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "Overrides the communication pattern for the target module.\n" +
            "* **twoway**: Wait for a response (Default)\n" +
            "* **oneway**: Fire-and-Forget pattern",
            nullable = true,
            example = "twoway")
    private ExchangePattern exchangePattern;
}
