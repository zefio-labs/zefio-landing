package io.zefio.core.payload.builder;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.CorrelationIdType;
import io.zefio.core.payload.builder.config.FixedValues;
import io.zefio.core.payload.builder.config.Telegram;
import org.apache.commons.lang3.ObjectUtils;

import java.nio.charset.Charset;
import java.util.*;

/**
 * Handles parsing and assembly of fixed-length byte arrays.
 * It uses a metadata-driven layout to map bytes to fields and vice-versa,
 * with specific protections against character corruption during byte-truncation.
 */
public class FixedPayloadBuilder extends BasePayloadBuilder implements PayloadBuilder {

    private final FixedValues values;

    public FixedPayloadBuilder(Telegram telegram){
        super(telegram);
        this.values = (FixedValues) telegram.getValues();
    }

    @Override
    public String extractCorrelationId(Object original, Object obj, Charset encoding) throws FlowException {
        String trxID = "";
        try {
            // Extract CorrelationID using defined Offset/Length if configured
            if (this.telegram.getValues().getCorrelation().getType() == CorrelationIdType.Offset) {
                if (ObjectUtils.allNotNull(values.getCorrelation().getStart(), values.getCorrelation().getLength())) {
                    byte[] rawData = (byte[]) obj;
                    int start = values.getCorrelation().getStart();
                    int length = values.getCorrelation().getLength();

                    // 1. Boundary check to prevent IndexOutOfBounds
                    if (rawData.length < start + length) {
                        log.error("Data length({}) is shorter than defined offset({}+{})", rawData.length, start, length);
                        throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "Message body is too short for ID extraction");
                    }

                    byte[] correlationIdBytes = new byte[length];
                    System.arraycopy(rawData, start, correlationIdBytes, 0, length);
                    trxID = new String(correlationIdBytes, encoding);

                    // 2. Validate extracted ID
                    if (ObjectUtils.isEmpty(trxID)) {
                        log.error("Extracted Offset CorrelationID is empty");
                        throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "CorrelationID is empty at defined offset");
                    }
                    return trxID;
                }
            }
        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while extracting Offset CorrelationID", e);
            throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
        }

        // 3. Fallback to JMS standard IDs if offset logic is not applicable
        trxID = super.extractCorrelationIdByJms(original);
        if (ObjectUtils.isEmpty(trxID)) {
            throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "Could not determine Transaction ID from any source");
        }

        return trxID;
    }

    // =========================================================
    // Metadata-driven byte parsing to Map
    // =========================================================
    @Override
    public Map<String, Object> parseToMap(byte[] body, Charset encoding) throws Exception {
        if (body == null || body.length == 0) return Collections.emptyMap();
        List<FixedValues.FixedField> layout = values.getLayout();
        Map<String, Object> map = new LinkedHashMap<>();

        if (layout == null || layout.isEmpty()) {
            map.put("RAW_DATA", new String(body, encoding));
            return map;
        }

        int currentOffset = 0;
        for (FixedValues.FixedField field : layout) {
            if (field.getOffset() != null) currentOffset = field.getOffset();
            int len = field.getLength();

            // Handle variable length (length <= 0): take all remaining bytes
            if (len <= 0) {
                len = body.length - currentOffset;
            }

            if (len <= 0 || currentOffset >= body.length) break;

            byte[] fieldBytes = new byte[len];
            System.arraycopy(body, currentOffset, fieldBytes, 0, len);
            String rawString = new String(fieldBytes, encoding);

            String parsedString = field.isTrim() ?
                    removePadding(rawString, field.getAlign(), field.getPaddingChar()) : rawString;

            map.put(field.getName(), castType(parsedString, field.getType()));
            currentOffset += len;
        }
        return map;
    }

    // =========================================================
    // Write-Back: Reassembling Map to physical bytes
    // =========================================================
    @Override
    public byte[] buildFromMap(Map<String, Object> map, Charset encoding) throws Exception {
        if (map == null || map.isEmpty()) return new byte[0];
        List<FixedValues.FixedField> layout = values.getLayout();
        if (layout == null || layout.isEmpty()) return new byte[0];

        // 1. Calculate total buffer length dynamically
        int totalLen = 0;
        for (FixedValues.FixedField field : layout) {
            if (field.getLength() > 0) {
                totalLen += field.getLength();
            } else {
                Object rawValue = map.get(field.getName());
                if (rawValue != null) {
                    totalLen += String.valueOf(rawValue).getBytes(encoding).length;
                }
            }
        }

        byte[] buffer = new byte[totalLen];
        java.util.Arrays.fill(buffer, (byte) ' ');

        int currentOffset = 0;
        for (FixedValues.FixedField field : layout) {
            if (field.getOffset() != null) currentOffset = field.getOffset();

            Object rawValue = map.get(field.getName());
            String strValue = (rawValue == null) ? "" : String.valueOf(rawValue);

            int targetLen = field.getLength() > 0 ? field.getLength() : strValue.getBytes(encoding).length;
            if (targetLen <= 0) continue;

            // Apply byte-level padding
            byte[] processedBytes = applyPaddingByByte(strValue, targetLen, field.getAlign(), field.getPaddingChar(), encoding);

            int copyLen = Math.min(processedBytes.length, targetLen);
            System.arraycopy(processedBytes, 0, buffer, currentOffset, copyLen);

            currentOffset += targetLen;
        }
        return buffer;
    }

    /**
     * Resilient byte-level padding.
     * Prevents character corruption by ensuring truncation doesn't split multi-byte characters.
     */
    private byte[] applyPaddingByByte(String content, int targetLen, FixedValues.Align align, char padChar, Charset charset) {
        byte[] contentBytes = content.getBytes(charset);
        byte[] result = new byte[targetLen];
        byte padByte = (byte) padChar;

        // Truncation logic with character boundary protection
        if (contentBytes.length > targetLen) {
            String truncatedStr = new String(contentBytes, 0, targetLen, charset);
            byte[] truncatedBytes = truncatedStr.getBytes(charset);

            // If re-extracted bytes are still longer than target (due to partial multi-byte char), reduce length
            if (truncatedBytes.length > targetLen) {
                truncatedStr = truncatedStr.substring(0, truncatedStr.length() - 1);
                truncatedBytes = truncatedStr.getBytes(charset);
            }

            Arrays.fill(result, padByte);
            System.arraycopy(truncatedBytes, 0, result, (align == FixedValues.Align.R ? targetLen - truncatedBytes.length : 0), truncatedBytes.length);
            return result;
        }

        // Standard padding logic
        Arrays.fill(result, padByte);
        int padCount = targetLen - contentBytes.length;
        if (align == FixedValues.Align.R) {
            System.arraycopy(contentBytes, 0, result, padCount, contentBytes.length);
        } else {
            System.arraycopy(contentBytes, 0, result, 0, contentBytes.length);
        }
        return result;
    }

    private String removePadding(String str, FixedValues.Align align, char padChar) {
        if (str == null || str.isEmpty()) return str;
        int start = 0, end = str.length();
        if (align == FixedValues.Align.R) {
            while (start < end && str.charAt(start) == padChar) start++;
        } else {
            while (end > start && str.charAt(end - 1) == padChar) end--;
        }
        return start < end ? str.substring(start, end) : "";
    }

    private Object castType(String str, FixedValues.FieldType type) {
        if (str == null || str.isEmpty()) return null;
        try {
            switch (type) {
                case NUMBER: return Long.parseLong(str.trim());
                case DECIMAL: return Double.parseDouble(str.trim());
                default: return str;
            }
        } catch (Exception e) { return str; }
    }
}
