package io.zefio.core.system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.dto.UpstreamValues;
import io.zefio.jdk.annotation.ZefioNotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration DTO for Static Local Upstream.
 * Facilitates internal memory-based direct invocation from one flow to another,
 * effectively enabling modular sub-flow architectures.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Configuration values for direct static Flow-to-Flow invocation (LocalUpstream)")
public class LocalUpstreamValues extends UpstreamValues {

    @AIOpsTuning(hotDeployable = false, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @ZefioNotBlank(message = "targetFlow is a required field.")
    @Schema(description = "The exact name of the target Flow to be invoked synchronously or asynchronously.",
            nullable = false, example = "batchFlow")
    private String targetFlow;

    @AIOpsTuning(hotDeployable = true, min = "1000", max = "120000", riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.NETWORK_TIMEOUT)
    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Maximum execution time allowed for the invoked sub-flow (ms). Valid only for TwoWay (RequestReply) exchange patterns.",
            nullable = true, defaultValue = "30000", example = "30000")
    private Long timeout = 30000L;

    /**
     * Convenience method to safely retrieve the timeout value.
     */
    public long getTimeoutOrDefault() {
        return (timeout != null && timeout > 0) ? timeout : 30000L;
    }

}
