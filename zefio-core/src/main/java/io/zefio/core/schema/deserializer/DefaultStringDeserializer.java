package io.zefio.core.schema.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.IOException;

/**
 * Custom Jackson contextual deserializer that provides a fallback default string value, utilizing the OpenAPI @Schema annotation's default value if available.
 */
public class DefaultStringDeserializer extends StdDeserializer<String> implements ContextualDeserializer {

    private final String defaultValue;

    public DefaultStringDeserializer() {
        this(null);
    }

    public DefaultStringDeserializer(String defaultValue) {
        super(String.class);
        this.defaultValue = defaultValue;
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        return (value == null || value.trim().isEmpty()) ? defaultValue : value;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        Schema schema = property.getAnnotation(Schema.class);
        if (schema != null && !schema.defaultValue().isEmpty()) {
            return new DefaultStringDeserializer(schema.defaultValue());
        }
        return this;
    }
}
