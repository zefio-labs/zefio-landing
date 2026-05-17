package io.zefio.core.payload.builder.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for fixed-length telegram layouts, commonly used in standard ESB architectures.
 * Defines field alignment, padding, and data types for precise byte-level manipulation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixedValues implements TelegramValues {
    // If true, the engine skips encoding conversion for this telegram.
    @Builder.Default
    protected boolean encodingIgnore = false;

    @Builder.Default
    protected CorrelationField correlation = new CorrelationField(CorrelationIdType.None);

    @Builder.Default
    protected FramingField framing = new FramingField();

    // The sequential field layout definition
    @Builder.Default
    protected List<FixedField> layout = new ArrayList<>();

    @Override
    public boolean getEncodingIgnore() {
        return this.encodingIgnore;
    }

    public enum Align { L, R }
    public enum FieldType { STRING, NUMBER, DECIMAL }

    /**
     * Metadata for an individual field within a fixed-length telegram.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixedField {
        private String name;                // Field identifier (SpEL key)
        private int length;                 // Byte length of the field

        // If null, the field starts immediately after the previous one.
        // If set, it jumps to a specific absolute index.
        private Integer offset;

        private FieldType type = FieldType.STRING;
        private Align align = Align.L;
        private char paddingChar = ' ';
        private boolean trim = true;
    }
}
