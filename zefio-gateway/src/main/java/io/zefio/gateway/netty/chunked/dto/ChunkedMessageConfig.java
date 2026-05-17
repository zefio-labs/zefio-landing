package io.zefio.gateway.netty.chunked.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.schema.deserializer.DefaultStringDeserializer;
import lombok.Data;

/**
 * Base configuration for multi-part message boundary identification.
 * Uses specific status flags to recognize the start, middle, and end of a message sequence.
 */
@JsonPropertyOrder(alphabetic = true)
@Data
public abstract class ChunkedMessageConfig {

    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "The byte offset of the status field used for multi-part identification.",
            nullable = true, example = "102")
    protected Integer statusOffset;

    @JsonSetter(nulls = Nulls.SKIP)
    @JsonDeserialize(using = DefaultStringDeserializer.class)
    @Schema(description = "Flag value indicating the initial chunk of a message.",
            nullable = true, example = "S", defaultValue = "S")
    protected String statusStart = "S";

    @JsonSetter(nulls = Nulls.SKIP)
    @JsonDeserialize(using = DefaultStringDeserializer.class)
    @Schema(description = "Flag value indicating an intermediate chunk.",
            nullable = true, example = "P", defaultValue = "P")
    protected String statusMiddle = "P";

    @JsonSetter(nulls = Nulls.SKIP)
    @JsonDeserialize(using = DefaultStringDeserializer.class)
    @Schema(description = "Flag value indicating the final chunk of a message.",
            nullable = true, example = "F", defaultValue = "F")
    protected String statusEnd = "F";

    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "The byte offset where the actual message data begins within each chunk.",
            nullable = true, example = "120", defaultValue = "0")
    protected Integer longMessageOffset = 0;
}
