package io.zefio.core.payload.builder.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration data specific to XML telegram formats.
 * Similar to JSON values, it defines properties for character encoding,
 * correlation ID extraction, and stream framing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XmlValues implements TelegramValues {

	// Defaults to false to allow standard encoding conversion by default.
	@Builder.Default
	protected boolean encodingIgnore = false;

	@Builder.Default
	protected CorrelationField correlation = new CorrelationField(CorrelationIdType.None);

	@Builder.Default
	protected FramingField framing = new FramingField();

	@Override
	public boolean getEncodingIgnore() {
		return this.encodingIgnore;
	}
}
