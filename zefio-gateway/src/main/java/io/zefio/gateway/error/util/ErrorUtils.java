package io.zefio.gateway.error.util;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.zefio.core.common.exception.FlowException;
import io.zefio.gateway.error.dto.common.KeyedErrorCodeReplacementRule;
import io.zefio.gateway.error.dto.common.KeyedMessageCompositionRule;
import io.zefio.gateway.error.dto.common.OffsetErrorCodeReplacementRule;
import io.zefio.gateway.error.dto.common.OffsetMessageCompositionRule;
import io.zefio.gateway.error.base.ErrorMessage;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.util.BytesUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for managing error code mapping and message composition across different formats.
 * Supports both fixed-length (offset-based) and structured (keyed) message mutation.
 */
public class ErrorUtils {
    private static final Logger log = LoggerFactory.getLogger(ErrorUtils.class);

    /**
     * resizes the byte array if the required length for the error code replacement exceeds the current message size.
     */
    public static byte[] extendedOffsetErrorCode(byte[] fixed, Charset encoding, List<OffsetErrorCodeReplacementRule> errorCodeRules) {
        if (errorCodeRules != null && !errorCodeRules.isEmpty()) {
            // Determine the maximum required length based on offset and new code size
            int requiredLength = errorCodeRules.stream()
                    .mapToInt(rule -> rule.getOffset() + rule.getNewCode().getBytes(encoding).length)
                    .max()
                    .orElse(0);

            if (fixed.length < requiredLength) {
                // Expand the message array to ensure sufficient space
                byte[] extended = new byte[requiredLength];
                System.arraycopy(fixed, 0, extended, 0, fixed.length);
                return extended;
            }
        }
        return fixed;
    }

    /**
     * Performs offset-based error code replacement in byte arrays.
     * Supports exact matches and wildcard matching (e.g., "5xx").
     */
    public static void mappingOffsetErrorCode(byte[] message, Charset encoding, List<OffsetErrorCodeReplacementRule> errorCodeRules, Throwable throwable) {
        if (ObjectUtils.isEmpty(errorCodeRules)) return;

        String errorCode = extractErrorCode(throwable);
        if (errorCode != null) {
            Pair<Integer, byte[]> codeBytes = findOffsetReplacement(errorCodeRules, errorCode, encoding);

            if (codeBytes != null) {
                System.arraycopy(codeBytes.getValue1(), 0, message, codeBytes.getValue0(), codeBytes.getValue1().length);
            }
        }
    }

    /**
     * Performs key-based error code replacement for structured formats like Map or ObjectNode (JSON).
     * Supports exact matches and wildcard matching.
     */
    public static void mappingKeyedErrorCode(Object message, List<KeyedErrorCodeReplacementRule> errorCodeRules, Throwable throwable) {
        if (ObjectUtils.isEmpty(errorCodeRules)) return;

        String errorCode = extractErrorCode(throwable);
        if (errorCode != null) {
            Pair<String, String> replacement = findKeyedReplacement(errorCodeRules, errorCode);

            if (replacement != null) {
                if (message instanceof Map) {
                    ((Map<String, Object>) message).put(replacement.getValue0(), replacement.getValue1());
                } else if (message instanceof ObjectNode) {
                    ((ObjectNode) message).put(replacement.getValue0(), replacement.getValue1());
                }
            }
        }
    }

    /**
     * Composes a fixed-length error message based on the specified composition rule and offset.
     */
    public static byte[] mappingMessageComposition(OffsetMessageCompositionRule messageRule, Payload payload, Charset encoding, Throwable throwable) {
        byte[] errorMessage = extractErrorBytes(throwable, encoding);

        if (messageRule != null && messageRule.getMode() != null) {
            byte[] prefix = Arrays.copyOfRange(payload.getBody(), 0, messageRule.getOffset());
            ErrorMessage mode = messageRule.getMode();

            switch (mode) {
                case REQ:
                    errorMessage = BytesUtils.bytesMerge(prefix, payload.getRequestBody());
                    break;
                case RES:
                    errorMessage = BytesUtils.bytesMerge(prefix, payload.getBody());
                    break;
                case ERROR:
                    errorMessage = BytesUtils.bytesMerge(prefix, errorMessage);
                    break;
                case EMPTY:
                    errorMessage = prefix;
                    break;
                default:
                    break;
            }
        } else {
            errorMessage = BytesUtils.bytesMerge(payload.getBody(), errorMessage);
        }
        return errorMessage;
    }

    /**
     * Extracts error details into a Map for structured message composition.
     */
    public static Map<String, Object> mappingMessageComposition(KeyedMessageCompositionRule rule, Payload payload, Charset encoding, Throwable throwable) {
        Map<String, Object> prefixMap = new HashMap<>();
        if (rule == null || rule.getMode() == null || rule.getKey() == null) return prefixMap;

        String value;
        switch (rule.getMode()) {
            case REQ:
                value = new String(payload.getRequestBody(), encoding);
                break;
            case ERROR:
                value = extractErrorMessage(throwable, encoding);
                break;
            case EMPTY:
                value = "";
                break;
            default:
                value = null;
                break;
        }

        if (value != null) {
            prefixMap.put(rule.getKey(), value);
        }

        return prefixMap;
    }

    private static String extractErrorCode(Throwable throwable) {
        if (throwable instanceof FlowException) {
            FlowException fe = (FlowException) throwable;
            return fe.getErrorCode() != null ? fe.getErrorCode().toString() : "x";
        }
        return null;
    }

    private static Pair<Integer, byte[]> findOffsetReplacement(List<OffsetErrorCodeReplacementRule> rules, String errorCode, Charset encoding) {
        // Prioritize exact match
        for (OffsetErrorCodeReplacementRule rule : rules) {
            if (rule.getErrorCode().equalsIgnoreCase(errorCode)) {
                return Pair.with(rule.getOffset(), rule.getNewCode().getBytes(encoding));
            }
        }
        // Fallback to wildcard match
        for (OffsetErrorCodeReplacementRule rule : rules) {
            String codePrefix = rule.getErrorCode().replace("x", "");
            if (errorCode.startsWith(codePrefix)) {
                return Pair.with(rule.getOffset(), rule.getNewCode().getBytes(encoding));
            }
        }
        return null;
    }

    private static Pair<String, String> findKeyedReplacement(List<KeyedErrorCodeReplacementRule> rules, String errorCode) {
        for (KeyedErrorCodeReplacementRule rule : rules) {
            if (rule.getErrorCode().equalsIgnoreCase(errorCode)) {
                return Pair.with(rule.getKey(), rule.getNewCode());
            }
        }
        for (KeyedErrorCodeReplacementRule rule : rules) {
            String codePrefix = rule.getErrorCode().replace("x", "");
            if (errorCode.startsWith(codePrefix)) {
                return Pair.with(rule.getKey(), rule.getNewCode());
            }
        }
        return null;
    }

    private static byte[] extractErrorBytes(Throwable throwable, Charset encoding) {
        Throwable cause = throwable.getCause();
        if (cause instanceof NullPointerException) {
            return "null".getBytes(encoding);
        } else if (cause instanceof RestClientResponseException) {
            return ((RestClientResponseException) cause).getResponseBodyAsByteArray();
        } else {
            return throwable.getMessage().getBytes(encoding);
        }
    }

    private static String extractErrorMessage(Throwable throwable, Charset encoding) {
        Throwable cause = throwable.getCause();
        if (cause instanceof NullPointerException) {
            return "null";
        } else if (cause instanceof RestClientResponseException) {
            return new String(((RestClientResponseException) cause).getResponseBodyAsByteArray(), encoding);
        } else {
            return throwable.getMessage();
        }
    }
}
