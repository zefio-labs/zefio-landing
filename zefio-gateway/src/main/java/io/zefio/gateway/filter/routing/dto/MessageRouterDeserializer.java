package io.zefio.gateway.filter.routing.dto;

import io.zefio.core.schema.deserializer.MapToListDeserializer;

/**
 * Custom deserializer for MessageRoutingRule to handle map-to-list conversions
 * within the configuration loader.
 */
public class MessageRouterDeserializer extends MapToListDeserializer<MessageRoutingRule> {
    public MessageRouterDeserializer() {
        super(MessageRoutingRule.class);
    }
}
