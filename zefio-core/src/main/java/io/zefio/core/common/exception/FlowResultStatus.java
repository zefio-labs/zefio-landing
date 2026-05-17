package io.zefio.core.common.exception;

import lombok.Getter;

/**
 * Defines standard status codes and behaviors for various flow execution outcomes,
 * including error handling, retry policies, and client response routing.
 */
@Getter
public enum FlowResultStatus {

    // =================================================================================
    // [1] Transaction & Session Control
    // =================================================================================
    // (1-1) Business logic failures (Response required)
    NOT_CORRELATION_KEY("ERR_001", "TID cannot be identified", true, false),
    // [Fixed] Business timeout (Retry possible depending on the situation)
    TIMEOUT("ERR_002", "Transaction response timeout", true, true),
    DUPLICATE_REQUEST("ERR_003", "Duplicate request detected", true, false),
    SYNC_BRIDGE_EXPIRED("ERR_004", "Sync bridge waiting time exceeded", true, true),

    // (1-2) Physical connection drop or system shutdown (Response impossible/meaningless)
    // [Fixed] Socket/Channel is already closed
    ALREADY_CLOSED("ERR_005", "Connection is already closed", false, false),
    NOT_COMPLETE_CLOSE("ERR_006", "Abnormal connection termination", false, false),
    ALREADY_COMPLETED("ERR_007", "Transaction already completed", false, false),
    DUPLICATE_RESPONSE("ERR_008", "Duplicate response received (Ignored)", false, false),
    INTERRUPTED("ERR_009", "Interrupt occurred (Shutting down)", false, false),
    SYSTEM_SHUTDOWN("ERR_010", "System forced shutdown", false, false),
    // Orphaned response (Client already left, Dropped)
    ORPHANED_RESPONSE("ERR_011", "Orphaned response with no matching target (Drop)", false, false),

    // =================================================================================
    // [2] System & Infrastructure - Retryable
    // =================================================================================
    // Core Note: Even if it's a retry target (isRetryable=true), the final failure
    // must be reported to the client, hence shouldReply=true.

    // (2-1) Network and connection level retries
    NETWORK_ERROR("RETRY_001", "Temporary network error (IO/Connection)", true, true),
    CONNECT_TIMEOUT("RETRY_002", "Connection timeout", true, true),
    READ_TIMEOUT("RETRY_003", "Read timeout", true, true),
    CONNECTION_REFUSED("RETRY_004", "Connection refused", true, true),

    // (2-2) Resource and DB retries
    DATABASE_ERROR("RETRY_010", "Database processing error", true, true),
    DATABASE_TIMEOUT("RETRY_011", "DB Lock/Query delay", true, true),

    ASYNC_EXECUTION_ERROR("RETRY_012", "Asynchronous execution pool error", true, true),
    SERVICE_HANDLER_NOT_FOUND("RETRY_013", "Handler loading delay/absence", true, true),

    // [System Overload]
    QUEUE_CAPACITY_EXCEEDED("RETRY_997", "Internal queue/bridge capacity exceeded", true, true),
    // Recommended to wait before retrying rather than immediate retry
    SYSTEM_BUSY("RETRY_998", "Internal server processing delay (Thread Pool Exhausted)", true, true),
    CONNECTION_POOL_EXHAUSTED("RETRY_999", "External connection delay (Connection Pool Exhausted)", true, true),

    // =================================================================================
    // [3] Infrastructure Configuration Errors (Non-Retryable)
    // =================================================================================
    // Configuration or certificate issues cannot be resolved by retrying, fail immediately
    UNKNOWN_HOST("ERR_021", "DNS lookup failed (Unknown Host)", true, false),
    SSL_HANDSHAKE_ERROR("ERR_022", "SSL handshake failed", true, false),

    // =================================================================================
    // [4] Client Errors (HTTP 4xx equivalent)
    // =================================================================================
    // Non-retryable. Return error response immediately.
    BAD_REQUEST("ERR_400", "Invalid request format", true, false),
    UNAUTHORIZED("ERR_401", "Authentication failed", true, false),
    FORBIDDEN("ERR_403", "No permission", true, false),
    NOT_FOUND("ERR_404", "Resource not found", true, false),
    METHOD_NOT_ALLOWED("ERR_405", "Method not allowed", true, false),
    GONE("ERR_410", "Resource gone", true, false),

    INGRESS_EDGE_REJECT_PAYLOAD_TOO_LARGE("ERR_413", "Allowed max data length exceeded (Edge Reject)", true, false),
    INGRESS_EDGE_REJECT_PROTOCOL_VIOLATION("ERR_414", "Protocol specification violation (Edge Reject)", true, false),
    INGRESS_EDGE_REJECT_INVALID_DATA("ERR_415", "Ingress data specification error (Edge Reject)", true, false),

    // Used when semantic validation fails inside business/general filters
    INVALID_INPUT("ERR_422", "Data validation failed", true, false),

    VALIDATION_FAILED("ERR_423", "Business security/rule violation blocked (Guardrail Blocked)", true, false),
    TOO_MANY_REQUESTS("ERR_429", "Request limit exceeded (Rate Limit Exceeded)", true, false),

    // =================================================================================
    // [5] Server Internal Errors (HTTP 5xx equivalent)
    // =================================================================================
    // Internal logic bug or explicit error response from upstream server
    INTERNAL_SERVER_ERROR("ERR_500", "Internal server processing error", true, false),
    REMOTE_SERVER_ERROR("ERR_501", "Upstream server error (Remote 5xx)", true, false),
    REMOTE_CLIENT_ERROR("ERR_502", "Upstream server rejected request (Remote 4xx)", true, false),
    REMOTE_NOT_FOUND("ERR_503", "Upstream server path not found", true, false),
    PIPELINE_EXECUTION_ERROR("ERR_504", "Pipeline/Scope control and async execution error", true, false),
    SPEL_EVALUATION_ERROR("ERR_505", "Server-side SpEL expression evaluation and parsing error", true, false),
    DYNAMIC_ROUTE_NOT_FOUND("ERR_506", "Dynamic routing target flow absent", true, false),
    EXTERNAL_API_ERROR("ERR_507", "Communication failure with external API service", true, false),

    // =================================================================================
    // [6] Other Customs
    // =================================================================================
    CUSTOM_INGRESS_ERROR("ERR_801", "Individual ingress error", true, false),
    CUSTOM_FILTER_ERROR("ERR_802", "Individual filter error", true, false),
    CRYPTO_ERROR("ERR_803", "Payload encryption/decryption failed (Key mismatch or data corruption)", true, false),
    MESSAGE_FORMAT_ERROR("ERR_804", "Message parsing and format conversion failed (Offset/Encoding error)", true, false),

    UNKNOWN("ERR_999", "Undefined system error", true, false);

    private final String code;
    private final String message;
    private final boolean shouldReply; // Whether to send a failure response to the client
    private final boolean isRetryable; // Whether to attempt a retry internally or in the infrastructure

    FlowResultStatus(String code, String message, boolean shouldReply, boolean isRetryable) {
        this.code = code;
        this.message = message;
        this.shouldReply = shouldReply;
        this.isRetryable = isRetryable;
    }

    @Override
    public String toString() {
        return "[" + code + "] " + message;
    }
}
