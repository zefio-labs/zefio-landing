package io.zefio.core.config.flow;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the execution pipeline for a specific business interface.
 * It coordinates the Ingress module, the sequence of processing steps,
 * and flow-level error recovery strategies.
 */
@Data
public class FlowConfiguration {
    private String name;
    private String label;

    /** Performance and resource tuning options for this specific flow. */
    private FlowOptions options = new FlowOptions();

    /** The entry point configuration where the request is received. */
    private IngressConfiguration ingress;

    /**
     * The recursive list of processing steps.
     * Supports complex structures like Try-Scopes, Conditional Routes, and Parallel processing.
     */
    private List<StepConfiguration> steps = new ArrayList<>();

    /** Flow-level terminal error handling strategies (e.g., DLQ routing). */
    @JsonProperty("on-error")
    private List<ErrorHandlerConfiguration> onError = new ArrayList<>();
}
