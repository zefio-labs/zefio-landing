package io.zefio.core.common.util;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.WriteTimeoutException;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import org.springframework.core.task.TaskRejectedException;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Utility class for managing application errors. It provides logic to classify
 * exceptions into standardized FlowResultStatus, determine severity levels,
 * and unwrap asynchronous wrapper exceptions for accurate debugging.
 */
public class FlowErrorUtils {

    private static final Set<FlowResultStatus> FATAL_STATUS_NAMES = new HashSet<>(Arrays.asList(
            // =========================================================
            // 1. Zefio Resource Exhaustion (Internal system overload - scaling signal)
            // =========================================================
            FlowResultStatus.SYSTEM_BUSY,               // Thread pool full
            FlowResultStatus.QUEUE_CAPACITY_EXCEEDED,   // Main/Bridge queue full

            // =========================================================
            // 2. External Connection Exhaustion (Session leak or insufficient capacity)
            // =========================================================
            FlowResultStatus.CONNECTION_POOL_EXHAUSTED, // WebT/Jolt session exhaustion

            // =========================================================
            // 3. Critical Configuration & Deployment Errors (Operator or environment issues)
            // =========================================================
            FlowResultStatus.DATABASE_TIMEOUT,          // DB Lock (Leads to total system paralysis)
            FlowResultStatus.SSL_HANDSHAKE_ERROR        // Certificate expired (Security breach)
    ));

    private static final Set<String> FATAL_JVM_ERRORS = new HashSet<>(Arrays.asList(
            // =========================================================
            // 4. JVM/OS Level Fatal Errors (Threatens process survival)
            // =========================================================
            "OutOfMemoryError",        // Heap/Metaspace/Direct memory exhaustion
            "StackOverflowError",      // Stack collapse due to infinite loops, etc.
            "NoClassDefFoundError",    // Runtime collapse due to library conflicts/missing deployment
            "NoSuchMethodError",       // Version incompatibility (common on Hot-deploy failure)
            "BindException"            // Server startup failure due to port conflict
    ));

    public static String decideSeverity(FlowResultStatus status, Throwable rootCause) {

        // 1. True FATAL (Critical collapse requiring immediate emergency alerts)
        if (rootCause != null && FATAL_JVM_ERRORS.contains(rootCause.getClass().getSimpleName())) {
            return "FATAL";
        }
        if (status != null && FATAL_STATUS_NAMES.contains(status)) {
            return "FATAL";
        }

        // 2. ERROR (Transaction failure -> No immediate system alert)
        // Criteria: "The engine is operational, but a specific transaction failed due to network or logic issues"
        return "ERROR";
    }

    /**
     * Unwraps all exceptions and converts them into the optimal FlowException.
     */
    public static FlowException convert(Throwable th) {
        // 1. Unwrapping (remove Completion/ExecutionException)
        Throwable root = unwrap(th);

        if (root == null) {
            return new FlowException(FlowResultStatus.UNKNOWN, "Unknown Error (Null)");
        }

        // 2. If already a FlowException, return as is
        if (root instanceof FlowException) {
            return (FlowException) root;
        }

        // 3. Detailed mapping by exception type
        FlowResultStatus status = classify(root);

        // Key Improvement: Pass the original exception (root) to preserve the stack trace.
        return new FlowException(root, status);
    }

    /**
     * Status classification logic based on exception type (order is important)
     */
    private static FlowResultStatus classify(Throwable root) {
        String msg = root.getMessage() != null ? root.getMessage() : "";
        String className = root.getClass().getName();

        // ---------------------------------------------------------
        // [1] Detailed Timeouts - for retry determination
        // ---------------------------------------------------------
        if (root instanceof ConnectTimeoutException) {         // Netty connection timeout
            return FlowResultStatus.CONNECT_TIMEOUT;
        }
        // Netty's AnnotatedConnectException can throw both timeout and refused, so distinguish by message
        if (className.contains("ConnectException")) {
            if (msg.contains("timed out") || msg.contains("timeout")) {
                return FlowResultStatus.CONNECT_TIMEOUT;
            }
            return FlowResultStatus.CONNECTION_REFUSED;
        }

        if (root instanceof ReadTimeoutException ||            // Netty read timeout
                root instanceof SocketTimeoutException) {          // Java socket read timeout
            return FlowResultStatus.READ_TIMEOUT;
        }
        if (root instanceof TimeoutException ||                // Future timeout
                root instanceof WriteTimeoutException) {           // Write delay
            return FlowResultStatus.TIMEOUT;
        }
        if (root instanceof ClosedChannelException ||
                className.contains("StacklessClosedChannelException")) {
            return FlowResultStatus.ALREADY_CLOSED;
        }

        // ---------------------------------------------------------
        // [2] Detailed Network Connection
        // ---------------------------------------------------------
        // classify Netty special exceptions (AnnotatedConnectException, etc.) as CONNECTION_REFUSED
        if (root instanceof ConnectException ||
                msg.contains("Connection refused")) {     // Safety net
            return FlowResultStatus.CONNECTION_REFUSED;
        }
        if (root instanceof UnknownHostException) {            // DNS lookup failed (Non-retryable)
            return FlowResultStatus.UNKNOWN_HOST;
        }
        if (root instanceof SSLException) {                    // SSL Certificate error (Non-retryable)
            return FlowResultStatus.SSL_HANDSHAKE_ERROR;
        }
        if (root instanceof NoRouteToHostException) {          // No network route
            return FlowResultStatus.NETWORK_ERROR;
        }

        // Handle general IO errors
        if (root instanceof IOException) {
            if (msg.contains("Connection reset") || msg.contains("Broken pipe")) {
                return FlowResultStatus.ALREADY_CLOSED;
            }
            return FlowResultStatus.NETWORK_ERROR;
        }

        // ---------------------------------------------------------
        // [3] Message Specification (Bad Request)
        // ---------------------------------------------------------
        if (root instanceof TooLongFrameException || root instanceof DecoderException) {
            return FlowResultStatus.BAD_REQUEST;
        }
        if (root instanceof IllegalArgumentException || root instanceof NumberFormatException) {
            return FlowResultStatus.INVALID_INPUT;
        }

        // ---------------------------------------------------------
        // [4] System Resources (System Busy)
        // ---------------------------------------------------------
        if (root instanceof java.util.NoSuchElementException && msg.contains("Timeout waiting for idle object")) {
            // Typical error occurring during Apache Commons Pool exhaustion
            return FlowResultStatus.CONNECTION_POOL_EXHAUSTED;
        }
        if (root instanceof RejectedExecutionException ||
                root instanceof TaskRejectedException ||
                root instanceof OutOfMemoryError) {
            return FlowResultStatus.SYSTEM_BUSY;
        }

        if (root instanceof InterruptedException) {
            return FlowResultStatus.INTERRUPTED;
        }

        // ---------------------------------------------------------
        // [5] Netty-based
        // ---------------------------------------------------------
        if (root instanceof WebSocketHandshakeException) {
            return FlowResultStatus.BAD_REQUEST;
        }

        // ---------------------------------------------------------
        // [6] Others (Unknown)
        // ---------------------------------------------------------
        // JSch / SFTP specific classification
        if (msg.contains("Auth fail") || msg.contains("Permission denied")) {
            return FlowResultStatus.UNAUTHORIZED;
        }

        return FlowResultStatus.UNKNOWN;
    }

    /**
     * Recursively unwraps asynchronous wrapper exceptions.
     */
    public static Throwable unwrap(Throwable th) {
        Throwable root = th;
        while (root instanceof CompletionException || root instanceof ExecutionException) {
            if (root.getCause() == null) break;
            root = root.getCause();
        }
        return root;
    }

    /**
     * Formatting error message for logging
     */
    public static String getErrorMessage(Throwable th) {
        if (th == null) return "";

        FlowException root = convert(th);
        return root.getStatus().toString() + ", " + root.getMessage();
    }
}
