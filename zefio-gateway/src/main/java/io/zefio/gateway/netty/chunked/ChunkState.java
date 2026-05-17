package io.zefio.gateway.netty.chunked;

/**
 * Represents the sequence state of a multi-part message chunk.
 */
public enum ChunkState {
    /** The initial chunk of a sequence. */
    START,
    /** An intermediate chunk of a sequence. */
    MIDDLE,
    /** The terminal chunk of a sequence. */
    END,
    /** A standalone message that does not require chunking. */
    SINGLE,
    /** State could not be determined. */
    UNKNOWN
}
