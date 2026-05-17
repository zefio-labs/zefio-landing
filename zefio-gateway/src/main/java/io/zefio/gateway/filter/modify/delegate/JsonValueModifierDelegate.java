package io.zefio.gateway.filter.modify.delegate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.util.TransferUtils;
import io.zefio.gateway.filter.modify.dto.JsonValueModifierChild;
import io.zefio.gateway.filter.modify.dto.JsonValueModifierDelegateValues;
import io.zefio.gateway.filter.modify.dto.ValueModifierDirection;
import io.zefio.gateway.filter.modify.ValueModifierDirectional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Handles modifications for JSON payloads using JsonPath for precise navigation and mutation.
 */
public class JsonValueModifierDelegate implements ValueModifierDelegate {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final JsonValueModifierDelegateValues config;
    private final ObjectMapper mapper = new ObjectMapper();

    public JsonValueModifierDelegate(JsonValueModifierDelegateValues config) {
        this.config = config;
    }

    @Override
    public void modify(Payload payload, List<?> children, Charset encoding, ValueModifierDirectional parent) throws Exception {
        String json = new String(payload.getBody(), encoding);

        log.info("Starting JSON modification. Direction: {}, Children count: {}",
                config.getDirection(), children.size());

        DocumentContext doc = JsonPath.parse(json, Configuration.defaultConfiguration());

        for (Object obj : children) {
            JsonValueModifierChild child = (JsonValueModifierChild) obj;
            String jsonPath = child.getJsonPath();
            log.debug("Processing child jsonPath [{}], valueOrProperty [{}]", jsonPath, child.getValueOrProperty());

            switch (config.getDirection()) {
                case MODIFY_BODY:
                    String newValue = TransferUtils.formConvertor(child.getValueOrProperty(), payload);
                    doc.set(jsonPath, newValue);
                    log.info("MODIFY_BODY: JSON path [{}] updated to [{}]", jsonPath, newValue);
                    break;

                case PROPERTY_TO_BODY:
                    Object value = payload.getHeader(child.getValueOrProperty());
                    if (value == null) {
                        throw new IllegalArgumentException("Property for JSON insert key [" + child.getValueOrProperty() + "] is null");
                    }
                    if (value instanceof byte[]) {
                        value = new String((byte[]) value, encoding);
                    }
                    doc.set(jsonPath, value);
                    log.info("PROPERTY_TO_BODY: Injected value into JSON path [{}]", jsonPath);
                    break;

                case BODY_TO_PROPERTY:
                    Object bodyValue = doc.read(jsonPath);
                    if (bodyValue != null) {
                        if (bodyValue instanceof String) {
                            payload.setHeader(child.getValueOrProperty(), bodyValue);
                        } else {
                            payload.setHeader(child.getValueOrProperty(), mapper.writeValueAsString(bodyValue));
                        }
                        log.info("BODY_TO_PROPERTY: Extracted value from JSON path [{}] to property [{}]",
                                jsonPath, child.getValueOrProperty());

                        if (config.getRemoveExtracted()) {
                            doc.delete(jsonPath);
                            log.info("BODY_TO_PROPERTY: Deleted JSON path [{}] after extraction", jsonPath);
                        }
                    } else {
                        log.warn("BODY_TO_PROPERTY: JSON path [{}] not found", jsonPath);
                    }
                    break;
            }
        }

        if (config.getDirection() != ValueModifierDirection.BODY_TO_PROPERTY || config.getRemoveExtracted()) {
            String newJson = doc.jsonString();
            payload.setBody(newJson.getBytes(encoding));
            log.info("JSON body update complete");
        }
    }
}
