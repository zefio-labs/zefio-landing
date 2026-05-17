package io.zefio.gateway.error.deserializer;

import io.zefio.core.schema.deserializer.MapToListDeserializer;
import io.zefio.gateway.error.dto.common.OffsetValueOverride;

public class OffsetValueDeserializer extends MapToListDeserializer<OffsetValueOverride> {
    public OffsetValueDeserializer() {
        super(OffsetValueOverride.class);
    }
}
