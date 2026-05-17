package io.zefio.gateway.filter.modify.deserializer;

import io.zefio.core.schema.deserializer.MapToListDeserializer;
import io.zefio.gateway.filter.modify.dto.FixedBinaryModifierChild;

/**
 * Custom deserializer for Fixed-length binary message modification rules.
 * Maps raw configuration data into a list of FixedBinaryModifierChild objects.
 */
public class FixedBinaryModifierChildDeserializer extends MapToListDeserializer<FixedBinaryModifierChild> {
    public FixedBinaryModifierChildDeserializer() {
        super(FixedBinaryModifierChild.class);
    }
}
