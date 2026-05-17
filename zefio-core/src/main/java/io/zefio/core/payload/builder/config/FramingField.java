package io.zefio.core.payload.builder.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Configures how a raw stream is framed into individual packets.
 * Supports length-based, delimiter-based, and stream-end (EOF) strategies.
 */
@Getter
@Setter
public class FramingField {
	private FramingType type = FramingType.EOF;

	// Configuration for Delimiter mode
	private String delimiter = "\n";

	// Configuration for Length mode (TCP Fixed)
	private Integer lengthDataSize = 0;
	private Boolean lengthDataInclude = false;
	private Boolean lengthDataUpdate = true;

	public FramingField() {}
}
