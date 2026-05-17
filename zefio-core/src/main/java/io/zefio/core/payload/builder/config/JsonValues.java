package io.zefio.core.payload.builder.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration data specific to JSON telegram formats.
 * Manages default behavior for encoding conversion and initializes sub-configurations
 * for correlation and framing to prevent null pointers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JsonValues implements TelegramValues {

	// Defaults to false to ensure encoding conversion is performed unless specified.
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
