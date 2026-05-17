package io.zefio.gateway.netty.chunked.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration for aggregating multi-part (chunked) messages.
 * Defines resource limits and session persistence rules.
 */
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder(alphabetic = true)
@Data
public class ChunkAggregatorConfig extends ChunkedMessageConfig {

    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Maximum allowable message size in bytes.",
            example = "1048576", defaultValue = "1048576")
    protected Integer maxMessageSize = 1024 * 1024; // Default: 1MB

    @JsonSetter(nulls = Nulls.SKIP)
    @Schema(description = "Timeout for chunk session assembly in milliseconds.",
            example = "5000", defaultValue = "5000")
    protected Integer chunkTimeout = 5000; // Default: 5 seconds
}
