package io.zefio.gateway.error.deserializer;

import io.zefio.core.schema.deserializer.MapToListDeserializer;
import io.zefio.gateway.error.dto.common.OffsetErrorCodeReplacementRule;

public class OffsetErrorCodeReplacementRuleDeserializer extends MapToListDeserializer<OffsetErrorCodeReplacementRule> {
    public OffsetErrorCodeReplacementRuleDeserializer() {
        super(OffsetErrorCodeReplacementRule.class);
    }
}
