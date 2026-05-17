package io.zefio.core.payload.util;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.FramingField;
import io.zefio.core.payload.builder.config.FramingType;
import io.zefio.core.payload.builder.config.Telegram;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Utility for constructing error payloads at the Ingress Edge.
 * It generates a format-specific error body based on the telegram type and
 * automatically applies protocol framing (e.g., Length headers).
 */
public class IngressErrorUtils {

    /**
     * Assembles an error payload to be returned to the Ingress client.
     */
    public static byte[] buildEdgeErrorPayload(PayloadBuilder builder, FlowException flowEx, Charset encoding) {
        String statusName = flowEx.getStatus().name();
        String message = flowEx.getMessage() != null ? flowEx.getMessage() : "Unknown Error";

        Telegram telegram = builder != null ? builder.getTelegram() : null;
        String errorBody = "";

        // 1. Generate default body based on Telegram Type
        if (telegram != null && telegram.getType() != null) {
            switch (telegram.getType()) {
                case JSON:
                    errorBody = String.format("{\"rtnCode\":\"%s\", \"rtnMsg\":\"%s\"}",
                            statusName, message.replace("\"", "\\\""));
                    break;
                case XML:
                    errorBody = String.format("<error><code>%s</code><msg>%s</msg></error>",
                            statusName, message);
                    break;
                case Fixed:
                default:
                    errorBody = String.format("ERROR:%s:%s", statusName, message);
                    break;
            }
        } else {
            errorBody = "ERROR:" + statusName + " - " + message;
        }

        Charset targetEncoding = encoding != null ? encoding : StandardCharsets.UTF_8;
        byte[] payload = errorBody.getBytes(targetEncoding);

        // 2. Handle Framing (Length Header) independently of the body format
        if (telegram != null && telegram.getValues() != null) {
            FramingField framing = telegram.getValues().getFraming();

            // If the framing strategy is 'Length', prepend the length header regardless of format
            if (framing != null && framing.getType() == FramingType.Length) {
                int lengthSize = framing.getLengthDataSize() != null ? framing.getLengthDataSize() : 0;
                if (lengthSize > 0) {
                    return BytesUtils.appendLength(payload, lengthSize, Boolean.TRUE.equals(framing.getLengthDataInclude()));
                }
            }
        }

        return payload;
    }
}
