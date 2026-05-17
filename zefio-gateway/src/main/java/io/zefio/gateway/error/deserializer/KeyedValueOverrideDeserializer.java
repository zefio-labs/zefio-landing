package io.zefio.gateway.error.deserializer;

import io.zefio.core.schema.deserializer.MapToListDeserializer;
import io.zefio.gateway.error.dto.common.KeyedValueOverride;

public class KeyedValueOverrideDeserializer extends MapToListDeserializer<KeyedValueOverride> {
    public KeyedValueOverrideDeserializer() {
        super(KeyedValueOverride.class);
    }
}
