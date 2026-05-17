package io.zefio.gateway.error.common;

import io.zefio.core.payload.Payload;
import io.zefio.core.payload.util.TelegramFactory;
import io.zefio.gateway.error.dto.FixedFaultValues;
import io.zefio.gateway.error.dto.common.OffsetErrorCodeReplacementRule;
import io.zefio.gateway.error.dto.common.OffsetMessageCompositionRule;
import io.zefio.gateway.error.dto.common.OffsetValueOverride;
import io.zefio.gateway.error.util.ErrorUtils;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.FramingField;
import io.zefio.core.payload.builder.config.FramingType;
import io.zefio.core.payload.util.BytesUtils;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Modifies fixed-length byte payloads for error reporting.
 * Applies offset-based substitutions and dynamically recalculates the framing length.
 */
public class FixedErrorEditor implements ErrorMessageEditor {
    private final List<OffsetValueOverride> valueOverrides;
    private final List<OffsetErrorCodeReplacementRule> errorCodeRules;
    private final OffsetMessageCompositionRule messageRule;

    public FixedErrorEditor(FixedFaultValues config) {
        this.valueOverrides = config.getValueOverrides();
        this.errorCodeRules = config.getErrorCodeRules();
        this.messageRule = config.getMessageRule();
    }

    @Override
    public byte[] edit(Payload payload, Charset encoding, Throwable throwable) throws Exception {
        // 1. Apply message composition rules
        byte[] fixed = ErrorUtils.mappingMessageComposition(messageRule, payload, encoding, throwable);
        fixed = ErrorUtils.extendedOffsetErrorCode(fixed, encoding, errorCodeRules);

        // 2. Apply error code replacements
        ErrorUtils.mappingOffsetErrorCode(fixed, encoding, this.errorCodeRules, throwable);

        // 3. Apply fixed offset value overrides
        for (OffsetValueOverride ov : valueOverrides) {
            int index = ov.getOffset();
            byte[] replace = ov.getValue().getBytes(encoding);
            System.arraycopy(replace, 0, fixed, index, replace.length);
        }

        // 4. Update the length header using BytesUtils to prevent TCP stream corruption
        fixed = safeUpdateFixedLength(payload, fixed);

        return fixed;
    }

    /**
     * Safely recalculates and updates the payload length header to protect the TCP stream boundary.
     */
    protected byte[] safeUpdateFixedLength(Payload payload, byte[] body) {
        String telegramName = payload.getTelegramName();
        if (telegramName == null) return body;

        // Retrieve the builder/metadata corresponding to the telegram name from the global factory
        PayloadBuilder builder = TelegramFactory.getBuilder(telegramName);

        if (builder != null && builder.getTelegram() != null) {
            FramingField framing = builder.getTelegram().getValues().getFraming();

            if (framing != null && framing.getType() == FramingType.Length) {
                int lengthSize = framing.getLengthDataSize() != null ? framing.getLengthDataSize() : 0;
                if (lengthSize > 0) {
                    return BytesUtils.updateLength(body, lengthSize, Boolean.TRUE.equals(framing.getLengthDataInclude()));
                }
            }
        }
        return body;
    }
}
