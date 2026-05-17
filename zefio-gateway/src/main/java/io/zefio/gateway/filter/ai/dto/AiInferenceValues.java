package io.zefio.gateway.filter.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Configuration parameters for AI Inference (LLM) processing within the gateway pipeline.")
public class AiInferenceValues {

    @Schema(
            description = "The AI provider being used (e.g., OPENAI, ANTHROPIC, LOCAL_VLLM).",
            example = "OPENAI",
            defaultValue = "OPENAI",
            nullable = false
    )
    @AIOpsTuning(riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.GENERAL)
    private String provider = "OPENAI";

    @Schema(
            description = "The API endpoint URL for the selected LLM provider.",
            example = "https://api.openai.com/v1/chat/completions",
            defaultValue = "https://api.openai.com/v1/chat/completions",
            nullable = false
    )
    @AIOpsTuning(riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.GENERAL)
    private String endpoint = "https://api.openai.com/v1/chat/completions";

    @Schema(
            description = "API Authentication Key. Handle securely.",
            example = "sk-proj-...",
            nullable = false
    )
    @AIOpsTuning(hotDeployable = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.GENERAL)
    private String apiKey;

    @Schema(
            description = "The specific model to be used for inference.",
            example = "gpt-4o-mini",
            defaultValue = "gpt-4o-mini",
            nullable = false
    )
    @AIOpsTuning(riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.GENERAL)
    private String model = "gpt-4o-mini";

    @Schema(
            description = "SpEL-enabled prompt template. Defines the instruction sent to the AI.",
            example = "Analyze the following user message: #{payload.body['USER_MSG']}",
            nullable = false
    )
    @AIOpsTuning(riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    private String promptTemplate;

    @Schema(
            description = "SpEL expression indicating where to store the AI's response within the Payload.",
            example = "payload.headers['AI_RESULT']",
            nullable = true
    )
    @AIOpsTuning(riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    private String targetKey;

    @Schema(
            description = "Maximum number of tokens to generate in the AI response.",
            example = "1000",
            defaultValue = "1000",
            nullable = true
    )
    @AIOpsTuning(min = "1", max = "4096", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.RESOURCE_SCALE)
    private int maxTokens = 1000;

    @Schema(
            description = "Sampling temperature. Lower values make the output more focused and deterministic.",
            example = "0.3",
            defaultValue = "0.3",
            nullable = true
    )
    @AIOpsTuning(min = "0.0", max = "2.0", riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    private double temperature = 0.3;

    // =================================================================================
    // 🚀 Added Timeout Configurations based on your suggestion
    // =================================================================================

    @Schema(
            description = "Connection timeout in milliseconds. Sets the maximum time allowed to establish a connection to the AI provider.",
            example = "5000",
            defaultValue = "5000",
            nullable = true
    )
    @AIOpsTuning(min = "1000", max = "30000", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.NETWORK_TIMEOUT)
    private int connectTimeout = 5000;

    @Schema(
            description = "Read timeout in milliseconds. Sets the maximum time allowed to wait for the LLM response. Use 0 for no timeout (infinite).",
            example = "30000",
            defaultValue = "0 (No timeout)",
            nullable = true
    )
    @AIOpsTuning(min = "0", max = "120000", riskLevel = AIOpsTuning.RiskLevel.HIGH, category = AIOpsTuning.Category.NETWORK_TIMEOUT)
    private int readTimeout = 30000;
}
