package io.zefio.gateway.netty.deserializer;

import io.zefio.core.schema.deserializer.MapToListDeserializer;
import io.zefio.gateway.netty.dto.HandlerDefinition;

/**
 * Custom Jackson deserializer to convert a Map-based configuration structure
 * into a List of HandlerDefinition objects.
 */
public class HandlerDefinitionListDeserializer extends MapToListDeserializer<HandlerDefinition> {
    public HandlerDefinitionListDeserializer() {
        super(HandlerDefinition.class);
    }
}
