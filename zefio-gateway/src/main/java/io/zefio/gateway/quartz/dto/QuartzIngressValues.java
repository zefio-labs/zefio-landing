package io.zefio.gateway.quartz.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.schema.dto.OnewayIngressValues;
import io.zefio.jdk.annotation.ZefioNotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * Configuration DTO for the Quartz Scheduled Ingress Core.
 * Maps open-source pipeline specifications and exports AIOps engine tuning metadata.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class QuartzIngressValues extends OnewayIngressValues {

    public enum MisfireInstructionPolicy {
        SMART_POLICY,
        FIRE_NOW,
        DO_NOTHING,
        IGNORE_MISFIRES
    }

    @ZefioNotBlank
    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.RESOURCE_SCALE)
    @Schema(description = "The path to the external Quartz properties file. Fallback to embedded config if empty.", nullable = true, example = "properties/quartz.properties", defaultValue = "")
    protected String config = "";

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "Legacy compatibility field for targeted Job routing class path. Marked as deprecated in SEDA offloading specs.", nullable = true, example = "io.zefio.data.quartz.job.GenericQuartzJob")
    @Deprecated
    protected String clazz;

    @ZefioNotBlank
    @AIOpsTuning(hotDeployable = true, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "Standard Unix-like Cron Expression used to drive the pipeline trigger schedule interval.", nullable = false, example = "0 0/5 * * * ?")
    @JsonProperty("cronExpression")
    protected String cronExpression;

    @AIOpsTuning(hotDeployable = true, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.CRITICAL, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "Alias field for cron expression mapping, reserved to preserve legacy integration properties compatibility.", nullable = true, example = "0/1 * * * * ?")
    protected String cron;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.MEDIUM, category = AIOpsTuning.Category.RESOURCE_SCALE)
    @Schema(description = "Misfire handling policy used when scheduler thread pool capacity is completely starved.", defaultValue = "SMART_POLICY")
    protected MisfireInstructionPolicy misfirePolicy = MisfireInstructionPolicy.SMART_POLICY;

    @AIOpsTuning(hotDeployable = false, restartRequired = true, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "Forces an instantaneous data harvesting execution trigger invocation precisely upon framework startup contexts warm-up.", defaultValue = "false")
    protected boolean fireOnStartup = false;

    @JsonSetter(nulls = Nulls.SKIP)
    @AIOpsTuning(hotDeployable = true, restartRequired = false, riskLevel = AIOpsTuning.RiskLevel.LOW, category = AIOpsTuning.Category.BUSINESS_LOGIC)
    @Schema(description = "Runtime static key-value parameters injected directly into the framework generated Payload context map.", nullable = true, example = "{\"PROGRESS\": 100, \"target_location\": \"Icheon\"}")
    protected Map<String, Object> value = Maps.newHashMap();

    /**
     * Extracts an active cron token mapping regardless of expression alias usage.
     */
    public String getActiveCron() {
        return this.cronExpression != null ? this.cronExpression : this.cron;
    }
}
