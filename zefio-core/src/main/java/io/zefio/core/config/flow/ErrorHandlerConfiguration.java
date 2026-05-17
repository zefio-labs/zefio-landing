package io.zefio.core.config.flow;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines conditional error handling logic based on the exception type.
 * When a matching error occurs, the engine executes the sequence of steps defined here.
 */
@Data
public class ErrorHandlerConfiguration {

    /** The specific error type to handle (e.g., "TIMEOUT", "VALIDATION_FAILED", or "ANY"). */
    @JsonProperty("error-type")
    private String errorType = "ANY";

    /**
     * The sequence of steps to execute during error recovery.
     * Can include tasks like building error payloads or calling fallback Upstreams.
     */
    private List<StepConfiguration> steps = new ArrayList<>();

    /** Reference key for a globally defined error handler in FlowSettings. */
    private String refErrorHandler;
}
