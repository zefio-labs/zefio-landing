package io.zefio.gateway.netty.chunked.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.schema.deserializer.DefaultStringDeserializer;
import io.zefio.gateway.netty.chunked.ChunkedResponseEncoder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration for splitting large internal messages into multiple upstream chunks.
 * Defines header offsets and fragmentation limits for TCP delivery.
 */
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder(alphabetic = true)
@Data
public class ChunkSplitterConfig extends ChunkedMessageConfig {

    @JsonSetter(nulls = Nulls.SKIP)
    @JsonDeserialize(using = DefaultStringDeserializer.class)
    @Schema(description = "Fully qualified name of the custom class used to format upstream chunks.",
            nullable = true, example = "io.zefio.site.CustomChunkedResponseEncoder",
            defaultValue = "io.zefio.gateway.netty.chunked.ChunkedResponseEncoder")
    private String customClass = ChunkedResponseEncoder.class.getName();

    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "The byte offset where the header starts in the original message.",
            nullable = true, example = "0")
    private Integer headerOffset;

    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "The length of the header to be preserved or replicated in each chunk.",
            nullable = true, example = "20")
    private Integer headerLength;

    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "The maximum allowable size for an individual TCP chunk.",
            nullable = true, example = "4000")
    private Integer maxChunkSize;

    /**
     * Evaluates whether the current message requires chunked processing.
     *
     * @return true if the status offset and max chunk size are defined and valid.
     */
    public boolean isBigMessage() {
        return statusOffset != null && maxChunkSize != null && maxChunkSize > 0;
    }
}
