package io.zefio.core.config.global;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines policy overrides for specific error types.
 */
@Data
public class ExceptionPolicyProperties {
    /**
     * Key: FlowResultStatus name (e.g., "NETWORK_ERROR").
     * Value: Policy details for that specific error.
     */
    private Map<String, PolicyDetail> overrides = new HashMap<>();

    @Data
    public static class PolicyDetail {
        /** Overrides the default retryable flag if not null. */
        private Boolean retryable;
        /** Overrides the default reply requirement if not null. */
        private Boolean shouldReply;
    }
}
