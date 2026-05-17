package io.zefio.gateway.netty.chunked.dto;

/**
 * Strategy for evaluating response data to determine pagination continuity.
 */
public enum PaginationResponseStrategy {
    /** Continues if the value at statusOffset matches the loopContinueValue. */
    FLAG_MATCH,
    /** Continues as long as the response body is not empty. */
    BODY_NOT_EMPTY,
    /** Continues until the specified maxPages limit is reached, regardless of content. */
    MAX_COUNT
}
