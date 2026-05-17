package io.zefio.core.common.exception;

import lombok.Getter;

/**
 * Custom runtime exception used throughout the flow engine to encapsulate both
 * internal processing statuses and external error details received from remote systems.
 */
@Getter
public class FlowException extends RuntimeException {

    private final FlowResultStatus status;

    private final String externalCode;

    private final String errorBody;

    public FlowException(FlowResultStatus status, String message) {
        super(message);
        this.status = status;
        this.externalCode = null;
        this.errorBody = message;
    }

    public FlowException(Throwable cause, FlowResultStatus status) {
        super(cause.getMessage() != null ? cause.getMessage() : status.getMessage(), cause);
        this.status = status;
        this.externalCode = null;
        this.errorBody = cause.getMessage() != null ? cause.getMessage() : status.getMessage();
    }

    public FlowException(Throwable cause, FlowResultStatus status, String externalCode, String errorBody) {
        super(buildMessage(externalCode, errorBody), cause);
        this.status = status;
        this.externalCode = externalCode;
        this.errorBody = errorBody;
    }

    public String getErrorCode() {
        return (externalCode != null) ? externalCode : status.getCode();
    }

    @Override
    public String toString() {
        if (externalCode != null) {
            return "[" + status.getCode() + "/" + externalCode + "] " + getMessage();
        }
        return "[" + status.getCode() + "] " + getMessage();
    }

    private static String buildMessage(String code, String body) {
        if (code == null || code.isEmpty()) return body;
        return String.format("[%s] %s", code, body);
    }
}
