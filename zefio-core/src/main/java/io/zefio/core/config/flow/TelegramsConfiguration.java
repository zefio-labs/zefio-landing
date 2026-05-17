package io.zefio.core.config.flow;

import io.zefio.core.payload.builder.config.Telegram;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Global configuration for data format definitions (Fixed, JSON, XML).
 * Maps a telegram identifier to its specific parser implementation and properties.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TelegramsConfiguration {
	private Telegram.Type type;
	private String clazz;
	private Map<String, Object> config;
}
