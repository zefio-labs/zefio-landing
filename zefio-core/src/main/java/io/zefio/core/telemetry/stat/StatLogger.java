package io.zefio.core.telemetry.stat;

import io.zefio.core.common.base.MDCKey;
import io.zefio.core.common.util.TimeUtils;
import io.zefio.core.payload.Payload;
import io.zefio.core.telemetry.history.InMemoryHistoryManager;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Logger responsible for generating transaction statistics.
 * Records execution time breakdown (Queue, Process, Remote) and
 * manages historical data for dashboard visualization.
 */
public class StatLogger {
    private static final Logger statLog = LoggerFactory.getLogger("sys.stat");

    @Setter
    private static InMemoryHistoryManager historyManager;

    /**
     * Calculates processing metrics and records the statistical log.
     */
    public static void log(Payload payload) {
        if (payload == null || payload.isSuppressStatLog()) return;

        long total = System.currentTimeMillis() - payload.getStartTime();
        long q = payload.getQueueWaitTime();
        long r = payload.getRemoteTime();

        // Pure internal logic processing time
        long p = total - q - r;
        if (p < 0) p = 0;

        String tradeTime = TimeUtils.formatLogTime(payload.getStartTime());
        String tid = (payload.getTrxID() != null) ? payload.getTrxID() : "UNKNOWN_TID";

        // Extract flow information from Payload MDC context or current thread MDC
        String flow = "UNKNOWN_FLOW";
        Map<String, String> mdcContext = payload.getMdcContext();
        if (mdcContext != null && mdcContext.containsKey(MDCKey.FLOW.getKey())) {
            flow = mdcContext.get(MDCKey.FLOW.getKey());
        } else if (MDC.get(MDCKey.FLOW.getKey()) != null) {
            flow = MDC.get(MDCKey.FLOW.getKey());
        }

        String result = (!payload.hasException()) ? "OK" : "ERR";

        // Format: TradeTime | TID | FLOW | Result | Total | Queue | Process | Remote
        String statLine = String.format("%s|%s|%s|%s|%d|%d|%d|%d",
                tradeTime, tid, flow, result, total, q, p, r);

        // 1. File-based statistical logging
        statLog.info(statLine);

        // 2. Memory-based history management
        if (historyManager != null) {
            String fullLine = "[STAT]|" + statLine;

            // Push to general statistics buffer
            historyManager.addStatLog(fullLine);

            // Copy terminal errors to a dedicated error buffer to prevent data loss in high-traffic scenarios
            if ("ERR".equals(result)) {
                historyManager.addErrStatLog(fullLine);
            }
        }
    }
}
