package io.zefio.core.util;

import io.zefio.core.payload.Payload;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Utility for managing Mapped Diagnostic Context (MDC) across asynchronous threads.
 * Provides mechanisms to preserve and restore log context within the SEDA pipeline.
 */
public class MDCUtils {

    /**
     * Restores the MDC context stored in the Payload object to the current thread.
     * Typically invoked at the start of asynchronous callbacks (e.g., thenComposeAsync)
     * where the original thread context is lost.
     */
    public static void restoreMdc(Payload payload) {
        Map<String, String> mdcContext = payload.getMdcContext();

        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        }
    }
}
