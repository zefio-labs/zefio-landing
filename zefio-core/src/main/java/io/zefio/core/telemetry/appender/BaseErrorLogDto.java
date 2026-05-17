package io.zefio.core.telemetry.appender;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Standard Data Transfer Object for JSON-formatted error logs.
 * Captures core transaction and error details for external telemetry analysis.
 */
@Getter
@Setter
public class BaseErrorLogDto {
    @JsonProperty("LOG_TIME")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private long logTime;

    @JsonProperty("SEVERITY")
    private String severity;

    @JsonProperty("TRX_ID")
    private String trxId;

    @JsonProperty("FLOW_NAME")
    private String flowName;

    @JsonProperty("ERR_CODE")
    private String errCode;

    @JsonProperty("MESSAGE")
    private String message;
}
