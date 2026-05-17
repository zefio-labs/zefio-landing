package io.zefio.core.telemetry.appender;

/**
 * Interface for providing dynamic file naming logic.
 * Implementations should generate a unique file name based on transaction attributes
 * (e.g., Timestamp, TrxID).
 */
public interface DynamicFileNaming {
    /**
     * Generates a file name for the log record.
     * @return The target file name (e.g., 202605020253_UPSTREAM_ERR_1234.json)
     */
    String generateFileName();
}
