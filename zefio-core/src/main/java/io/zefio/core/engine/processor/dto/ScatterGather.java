package io.zefio.core.engine.processor.dto;

import lombok.Data;

/**
 * Data Transfer Object representing configuration for the Scatter-Gather pattern.
 * It defines how to handle parallel execution across multiple nodes,
 * including timeout settings, aggregation strategies, and error handling policies.
 */
@Data
public class ScatterGather {

    private long timeout = 5000L;

    private AggregationType aggregationType = AggregationType.MAP_MERGE;
    private ErrorPolicy errorPolicy = ErrorPolicy.FAIL_FAST;

    public enum AggregationType {
        MAP_MERGE,  // Merge results into a JSON Map using node names as keys
        OVERRIDE    // Overwrite the payload with the last received response bytes
    }

    public enum ErrorPolicy {
        FAIL_FAST,  // Immediately propagate error if any node fails
        BEST_EFFORT // Ignore or log failed nodes and continue with successful data
    }
}
