package io.zefio.core.telemetry.api;

import io.zefio.core.telemetry.history.InMemoryHistoryManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for retrieving real-time telemetry history.
 * Provides endpoints for recent errors and statistics in plain text format.
 */
@RestController
@RequestMapping("/base/monitor")
public class MonitorApiController {

    private final InMemoryHistoryManager historyManager;

    public MonitorApiController(InMemoryHistoryManager historyManager) {
        this.historyManager = historyManager;
    }

    /**
     * Retrieves recent error logs from the in-memory buffer.
     */
    @GetMapping(value = "/recent-errors", produces = "text/plain;charset=UTF-8")
    public String getRecentErrors(@RequestParam(name = "limit", defaultValue = "5") int limit) {
        return historyManager.getRecentErrors(limit);
    }

    /**
     * Retrieves recent transaction statistics.
     */
    @GetMapping(value = "/recent-stats", produces = "text/plain;charset=UTF-8")
    public String getRecentStats(@RequestParam(name = "limit", defaultValue = "5") int limit) {
        return historyManager.getRecentStats(limit);
    }

    /**
     * Retrieves recent error-specific transaction statistics.
     */
    @GetMapping(value = "/recent-err-stats", produces = "text/plain;charset=UTF-8")
    public String getRecentErrStats(@RequestParam(name = "limit", defaultValue = "5") int limit) {
        return historyManager.getRecentErrStats(limit);
    }
}
