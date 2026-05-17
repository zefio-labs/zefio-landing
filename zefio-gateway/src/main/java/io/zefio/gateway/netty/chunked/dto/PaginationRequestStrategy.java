package io.zefio.gateway.netty.chunked.dto;

/**
 * Strategy for constructing subsequent requests during a pagination loop.
 */
public enum PaginationRequestStrategy {
    /** Resends the exact same request body. */
    REPLAY,
    /** Increments the page number field within the request payload. */
    INCREMENT_PAGE,
    /** Extracts a key from the previous response to be used as a token in the next request. */
    NEXT_KEY_EXCHANGE
}
