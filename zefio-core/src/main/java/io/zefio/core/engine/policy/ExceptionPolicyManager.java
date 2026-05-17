package io.zefio.core.engine.policy;

import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.config.global.ExceptionPolicyProperties;
import io.zefio.core.config.global.GlobalOptionsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Evaluates exception policies to determine engine behavior during error states.
 * It checks for global configuration overrides to decide if a specific status
 * should trigger a retry or a client response, falling back to default enum definitions if necessary.
 */
@Component
@RequiredArgsConstructor
public class ExceptionPolicyManager {

    private final GlobalOptionsProperties globalOptionsProperties;

    /**
     * Determines whether the given status code is eligible for a 'retry'.
     */
    public boolean isRetryable(FlowResultStatus status) {
        ExceptionPolicyProperties properties = globalOptionsProperties.getExceptionPolicy();

        // 1. Check YAML configuration for overrides
        if (properties.getOverrides() != null && properties.getOverrides().containsKey(status.name())) {
            Boolean override = properties.getOverrides().get(status.name()).getRetryable();
            if (override != null) {
                return override;
            }
        }
        // 2. Fall back to the default Enum value
        return status.isRetryable();
    }

    /**
     * Determines whether a 'reply' should be sent to the client for the given status code.
     */
    public boolean shouldReply(FlowResultStatus status) {
        ExceptionPolicyProperties properties = globalOptionsProperties.getExceptionPolicy();

        // 1. Check YAML configuration for overrides
        if (properties.getOverrides() != null && properties.getOverrides().containsKey(status.name())) {
            Boolean override = properties.getOverrides().get(status.name()).getShouldReply();
            if (override != null) {
                return override;
            }
        }
        // 2. Fall back to the default Enum value
        return status.isShouldReply();
    }
}
