package io.zefio.gateway.filter.modify.deserializer;

import io.zefio.core.schema.deserializer.MapToListDeserializer;
import io.zefio.gateway.filter.modify.dto.JsonValueModifierChild;

/**
 * Custom deserializer for JSON message value modification rules.
 * Maps raw configuration data into a list of JsonValueModifierChild objects.
 */
public class JsonValueModifierChildDeserializer extends MapToListDeserializer<JsonValueModifierChild> {
    public JsonValueModifierChildDeserializer() {
        super(JsonValueModifierChild.class);
    }
}
