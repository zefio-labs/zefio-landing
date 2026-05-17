package io.zefio.core.payload.builder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.CorrelationIdType;
import io.zefio.core.payload.builder.config.JsonValues;
import io.zefio.core.payload.builder.config.Telegram;
import org.apache.commons.lang3.ObjectUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * Handles JSON payload construction and parsing.
 * Supports correlation ID extraction via 1-depth keys or JSON paths (JsonPointer),
 * and provides Map-based serialization for SpEL evaluation.
 */
public class JsonPayloadBuilder extends BasePayloadBuilder implements PayloadBuilder {
    private final JsonValues values;
    private final ObjectMapper mapper = new JsonMapper();

    public JsonPayloadBuilder(Telegram telegram){
        super(telegram);
        this.values = (JsonValues) telegram.getValues();
    }

    @Override
    public String extractCorrelationId(Object original, Object obj, Charset encoding) throws FlowException {
        String trxID = "";
        try {
            CorrelationIdType type = this.telegram.getValues().getCorrelation().getType();

            if (type == CorrelationIdType.Key || type == CorrelationIdType.JsonPath) {
                JsonNode rootNode;
                if (obj instanceof byte[]) {
                    rootNode = mapper.readTree(new String((byte[]) obj, encoding));
                } else {
                    rootNode = mapper.valueToTree(obj);
                }

                // Branching logic for extraction strategy
                if (type == CorrelationIdType.Key) {
                    // Match 1-depth Key
                    String key = values.getCorrelation().getKey();
                    JsonNode valNode = rootNode.path(key);
                    if (!valNode.isMissingNode()) trxID = valNode.asText();

                } else {
                    // Match via JsonPath (Translated to Jackson JsonPointer)
                    // Converts "$.header.trxId" format to "/header/trxId"
                    String path = values.getCorrelation().getPath();
                    String pointer = path.replace("$.", "/").replace(".", "/");

                    JsonNode valNode = rootNode.at(pointer);
                    if (!valNode.isMissingNode()) trxID = valNode.asText();
                }

                if (ObjectUtils.isEmpty(trxID)) {
                    log.error("JSON Correlation [{}] not found or empty",
                            type == CorrelationIdType.Key ? values.getCorrelation().getKey() : values.getCorrelation().getPath());
                    throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "Required JSON Correlation ID not found");
                }
                return trxID;
            }
        } catch (FlowException e) {
            throw e;
        } catch (IOException e) {
            log.error("JSON parsing failed during TrxID extraction", e);
            throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
        } catch (Exception e) {
            log.error("Unexpected error in JsonPayloadBuilder", e);
            throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
        }

        // Fallback to JMS standard IDs if no specific JSON key is defined
        trxID = super.extractCorrelationIdByJms(original);
        if (ObjectUtils.isEmpty(trxID)) {
            throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "Could not determine JSON Transaction ID");
        }
        return trxID;
    }

    /**
     * Serializes JSON string into a Map for SpEL expression evaluation.
     */
    @Override
    public Map<String, Object> parseToMap(byte[] body, Charset encoding) throws Exception {
        if (body == null || body.length == 0) return java.util.Collections.emptyMap();
        return mapper.readValue(new String(body, encoding), new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Reassembles a logical Map back into JSON bytes (Write-Back).
     */
    @Override
    public byte[] buildFromMap(Map<String, Object> map, Charset encoding) throws Exception {
        if (map == null || map.isEmpty()) return new byte[0];
        return mapper.writeValueAsString(map).getBytes(encoding);
    }
}
