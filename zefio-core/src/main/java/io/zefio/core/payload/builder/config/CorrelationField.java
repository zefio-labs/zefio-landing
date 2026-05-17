package io.zefio.core.payload.builder.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Defines the configuration for extracting correlation identifiers from a payload.
 * It supports multiple extraction strategies including offsets, keys, paths, and expressions.
 */
@Setter
@Getter
public class CorrelationField {
	// Default to None to prevent NullPointerException and skip extraction if not configured.
	private CorrelationIdType type = CorrelationIdType.None;

	private Integer start;    // Offset start
	private Integer length;   // Offset length
	private String key;       // Map key (1st depth)
	private String path;      // JsonPath or XPath expression

	// Support for the unified SpEL expression engine
	private String expression;

	public CorrelationField() {}

	public CorrelationField(CorrelationIdType type) {
		this.type = type;
	}
}
