package io.zefio.core.telemetry.history;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Manager for maintaining a fixed-size historical log in memory.
 * Uses a ring buffer structure with bit-masking for high-performance indexing
 * and thread-safe data access.
 */
@Component
public class InMemoryHistoryManager {

    /** Buffer size must be a power of 2 to enable bitwise AND indexing. */
    private static final int MAX_SIZE = 128;

    private static final int MASK = MAX_SIZE - 1;

    private final AtomicReferenceArray<String> recentErrors = new AtomicReferenceArray<>(MAX_SIZE);
    private final AtomicReferenceArray<String> recentStats = new AtomicReferenceArray<>(MAX_SIZE);
    private final AtomicReferenceArray<String> recentErrStats = new AtomicReferenceArray<>(MAX_SIZE);

    private final AtomicLong errorIndex = new AtomicLong(0);
    private final AtomicLong statIndex = new AtomicLong(0);
    private final AtomicLong errStatIndex = new AtomicLong(0);

    public void addErrorLog(String logLine) {
        if (logLine == null) return;
        int idx = (int) (errorIndex.getAndIncrement() & MASK);
        recentErrors.set(idx, logLine);
    }

    public void addStatLog(String statLine) {
        if (statLine == null) return;
        int idx = (int) (statIndex.getAndIncrement() & MASK);
        recentStats.set(idx, statLine);
    }

    public void addErrStatLog(String statLine) {
        if (statLine == null) return;
        int idx = (int) (errStatIndex.getAndIncrement() & MASK);
        recentErrStats.set(idx, statLine);
    }

    public String getRecentErrors(int limit) {
        return getFromBuffer(errorIndex.get(), recentErrors, limit);
    }

    public String getRecentStats(int limit) {
        return getFromBuffer(statIndex.get(), recentStats, limit);
    }

    public String getRecentErrStats(int limit) {
        return getFromBuffer(errStatIndex.get(), recentErrStats, limit);
    }

    /**
     * Retrieves recent logs from the specified atomic buffer.
     */
    private String getFromBuffer(long currentTotal, AtomicReferenceArray<String> buffer, int limit) {
        if (currentTotal == 0) return "";

        int totalStored = (int) Math.min(currentTotal, MAX_SIZE);
        int count = Math.min(limit, totalStored);

        List<String> result = new ArrayList<>(count);
        long startSeq = currentTotal - count;

        for (long i = startSeq; i < currentTotal; i++) {
            int idx = (int) (i & MASK);
            String val = buffer.get(idx);
            if (val != null) {
                result.add(val);
            }
        }

        return String.join("\n", result);
    }
}
