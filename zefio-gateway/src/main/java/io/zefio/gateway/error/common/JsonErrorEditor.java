package io.zefio.gateway.error.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zefio.gateway.error.dto.JsonFaultValues;
import io.zefio.gateway.error.dto.common.KeyedErrorCodeReplacementRule;
import io.zefio.gateway.error.dto.common.KeyedMessageCompositionRule;
import io.zefio.gateway.error.dto.common.KeyedValueOverride;
import io.zefio.gateway.error.util.ErrorUtils;
import io.zefio.core.payload.Payload;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * Modifies JSON payloads for error reporting.
 * Parses the payload into a Map, applies key-based substitutions, and serializes back to JSON.
 */
public class JsonErrorEditor implements ErrorMessageEditor {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<KeyedValueOverride> valueOverrides;
    private final List<KeyedErrorCodeReplacementRule> errorCodeRules;
    private final KeyedMessageCompositionRule messageRule;

    public JsonErrorEditor(JsonFaultValues config) {
        this.valueOverrides = config.getValueOverrides();
        this.errorCodeRules = config.getErrorCodeRules();
        this.messageRule = config.getMessageRule();
    }

    @Override
    public byte[] edit(Payload payload, Charset encoding, Throwable throwable) throws Exception {
        // 1. Parse original body into JSON map
        Map<String, Object> jsonMap = mapper.readValue(payload.getBody(), new TypeReference<Map<String, Object>>() {});

        // 2. Generate message composition prefix map and merge using putAll
        Map<String, Object> prefixMap = ErrorUtils.mappingMessageComposition(messageRule, payload, encoding, throwable);
        jsonMap.putAll(prefixMap);

        // 3. Apply error code replacements
        ErrorUtils.mappingKeyedErrorCode(jsonMap, errorCodeRules, throwable);

        // 4. Apply fixed key value overrides
        for (KeyedValueOverride kv : valueOverrides) {
            jsonMap.put(kv.getKey(), kv.getValue());
        }

        // 5. Serialize back to JSON bytes
        return mapper.writeValueAsBytes(jsonMap);
    }
}
