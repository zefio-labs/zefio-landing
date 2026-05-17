package io.zefio.core.config.flow;

/**
 * Defines the recovery or propagation strategy when an exception occurs in a step.
 */
public enum OnErrorPolicy {
    /** Immediately propagate the error upward and halt the transaction. (Default) */
    THROW,

    /** Ignore the error and skip to the next sequential step in the pipeline. */
    CONTINUE,

    /** Stop further processing of the current pipeline and return successfully. */
    STOP,

    /** Redirect the execution flow to the defined fallback-steps. */
    FALLBACK
}
