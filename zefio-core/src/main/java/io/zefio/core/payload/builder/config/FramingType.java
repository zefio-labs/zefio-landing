package io.zefio.core.payload.builder.config;

/**
 * Defines the method used to identify message boundaries in transport protocols.
 */
public enum FramingType {
	// Packet boundaries are determined by a length field in the header (e.g., TCP Fixed)
	Length,

	// Packet boundaries are determined by specific characters (e.g., TCP JSON/XML)
	Delimiter,

	// The entire incoming stream is treated as a single packet (e.g., MQ, HTTP, Kafka)
	EOF
}
