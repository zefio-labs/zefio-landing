package io.zefio.core.common.base;

import lombok.Getter;

/**
 * Enumeration defining standard Mapped Diagnostic Context (MDC) keys used for logging and request tracing across the application.
 */
@Getter
public enum MDCKey {
    CID("CID"),
    TID("TID"),
    FLOW("FLOW");

    private final String key;

    MDCKey(String key) {
        this.key = key;
    }

    @Override
    public String toString() {
        return key;
    }
}
