package io.zefio.gateway.error.deserializer;

import io.zefio.core.schema.deserializer.MapToListDeserializer;
import io.zefio.gateway.error.dto.common.KeyedErrorCodeReplacementRule;

public class KeyedErrorCodeReplacementRuleDeserializer extends MapToListDeserializer<KeyedErrorCodeReplacementRule> {
    public KeyedErrorCodeReplacementRuleDeserializer() {
        super(KeyedErrorCodeReplacementRule.class);
    }
}
