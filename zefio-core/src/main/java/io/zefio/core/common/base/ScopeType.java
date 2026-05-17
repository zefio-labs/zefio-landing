package io.zefio.core.common.base;

import org.apache.commons.lang3.StringUtils;

/**
 * Enumeration defining various execution scope types for flow control, such as branching, scatter-gather, and error handling boundaries.
 */
public enum ScopeType {
    TRY_SCOPE,
    SCATTER_GATHER,
    SWITCH,
    UNTIL_SUCCESSFUL,
    UNKNOWN;

    public static ScopeType fromString(String typeStr) {
        if (StringUtils.isBlank(typeStr)) return UNKNOWN;

        try {
            return ScopeType.valueOf(typeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    public boolean isScope() {
        return this != UNKNOWN;
    }
}
