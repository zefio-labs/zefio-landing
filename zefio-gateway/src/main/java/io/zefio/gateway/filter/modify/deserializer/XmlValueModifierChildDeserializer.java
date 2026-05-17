package io.zefio.gateway.filter.modify.deserializer;

import io.zefio.core.schema.deserializer.MapToListDeserializer;
import io.zefio.gateway.filter.modify.dto.XmlValueModifierChild;

/**
 * Custom deserializer for XML message value modification rules.
 * Maps raw configuration data into a list of XmlValueModifierChild objects.
 */
public class XmlValueModifierChildDeserializer extends MapToListDeserializer<XmlValueModifierChild> {
    public XmlValueModifierChildDeserializer() {
        super(XmlValueModifierChild.class);
    }
}
