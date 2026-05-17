package io.zefio.core.config.flow;

import lombok.Data;
import java.util.List;

/**
 * Configuration for a single conditional branch within a SWITCH scope.
 */
@Data
public class SwitchCaseConfig {
    /** SpEL expression to evaluate (e.g., "#{body['STATUS'] == 'FAIL'}"). */
    private String condition;

    /** Pipeline to execute if the condition evaluates to true. */
    private List<StepConfiguration> steps;
}
