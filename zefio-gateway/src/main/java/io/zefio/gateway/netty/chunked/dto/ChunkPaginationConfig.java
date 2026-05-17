package io.zefio.gateway.netty.chunked.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Configuration for managing paginated backend communication.
 * Orchestrates repetitive request-response cycles based on specified strategies.
 */
@JsonPropertyOrder(alphabetic = true)
@Data
public class ChunkPaginationConfig {

    @Schema(description = "Strategy for processing subsequent requests.", example = "NEXT_KEY_EXCHANGE")
    private PaginationRequestStrategy requestStrategy = PaginationRequestStrategy.REPLAY;

    @Schema(description = "Strategy for evaluating the response to determine if more pages exist.", example = "FLAG_MATCH")
    private PaginationResponseStrategy responseStrategy = PaginationResponseStrategy.FLAG_MATCH;

    @Schema(description = "Byte offset in the response indicating the pagination status (flag).", example = "14")
    private Integer statusOffset = 14;

    @Schema(description = "The value required in the status field to continue the pagination loop.", example = "1")
    private String loopContinueValue = "1";

    @Schema(description = "The byte offset where the response body (actual data) begins.", example = "120")
    private Integer bodyOffset = 120;

    // --- NEXT_KEY_EXCHANGE Strategy Properties ---

    @Schema(description = "Byte offset for the exchange key in the next request.")
    private Integer reqKeyOffset;

    @Schema(description = "Byte offset for extracting the exchange key from the current response.")
    private Integer resKeyOffset;

    @Schema(description = "Length of the exchange key.")
    private Integer keyLen;

    // --- INCREMENT_PAGE Strategy Properties ---

    @Schema(description = "Byte offset of the page number within the request payload.")
    private Integer pageOffset;

    @Schema(description = "Length of the page number field in the request.", example = "2")
    private Integer pageLen = 1;

    @Schema(description = "Maximum number of allowed pages to prevent infinite loops.", defaultValue = "100")
    private Integer maxPages = 100;
}
