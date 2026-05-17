package io.zefio.core.config.flow;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.zefio.core.common.base.ScopeType;
import io.zefio.core.config.global.GlobalOptionsProperties;
import io.zefio.core.payload.ExchangePattern;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines a single unit of work or a control scope within the pipeline.
 * Uses a recursive structure (Composite Pattern) to represent nested processing logic,
 * including resilient scopes, parallel routers, and conditional selectors.
 */
@Data
public class StepConfiguration {
    private String name;
    private String label;

    /**
     * Identifier for the component type.
     * Can be a reserved scope (e.g., "TRY_SCOPE", "SWITCH") or a plugin name.
     */
    private String type;
    private String clazz;
    private String profile;
    private String telegram;
    private ExchangePattern exchangePattern = ExchangePattern.RequestReply;

    private Map<String, Object> config = new HashMap<>();

    /** Node-specific retry policy that overrides global defaults. */
    private GlobalOptionsProperties.DefaultRetry retry;

    /** Strategy for handling errors occurring within this step or its children. */
    @JsonProperty("on-error")
    private OnErrorPolicy onError = OnErrorPolicy.THROW;

    /** Child steps for composite scopes (e.g., the 'try' path in TRY_SCOPE). */
    private List<StepConfiguration> steps = new ArrayList<>();

    /** Alternate pipeline to execute when OnErrorPolicy is set to FALLBACK. */
    @JsonProperty("fallback-steps")
    private List<StepConfiguration> fallbackSteps = new ArrayList<>();

    // --- Conditional Routing (SWITCH) fields ---
    private List<SwitchCaseConfig> cases;
    private List<StepConfiguration> defaultSteps;

    /**
     * Identifies if this step is a framework core control block or a terminal module.
     */
    public ScopeType getScopeType() {
        return ScopeType.fromString(this.type);
    }
}
