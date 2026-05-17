package io.zefio.gateway.filter.modify.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import lombok.Data;

/**
 * Defines a single modification task for XML data using XPath expressions.
 */
@JsonPropertyOrder(alphabetic = true)
@Data
public class XmlValueModifierChild {

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "The XPath expression targeting the specific XML node for modification or extraction.",
            nullable = false, example = "/data/user/name")
    private String xpath;

    @AIOpsTuning(hotDeployable = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "The replacement value or the property key for metadata reference.",
            nullable = false, example = "Kim")
    private String valueOrProperty;

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "The tag name for the new element to be created. Used only in PROPERTY_TO_BODY mode.",
            nullable = true, example = "status")
    private String elementName;

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.PROTOCOL_SPEC)
    @Schema(description = "The XPath of the parent node where the new element will be appended. Used in PROPERTY_TO_BODY mode.",
            nullable = true, example = "/data/user")
    private String parentXpath;
}
