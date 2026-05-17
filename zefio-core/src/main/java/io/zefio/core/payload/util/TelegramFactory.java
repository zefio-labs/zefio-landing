package io.zefio.core.payload.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry and factory for PayloadBuilder instances.
 * It manages a thread-safe cache to reuse builders across different flows
 * and handles the conversion of raw configuration maps into typed Telegram objects.
 */
public class TelegramFactory {
	private static final Logger log = LoggerFactory.getLogger(TelegramFactory.class);

	private static final ObjectMapper mapper = new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private static final Map<String, PayloadBuilder> builderCache = new ConcurrentHashMap<>();

	/**
	 * Clears the builder cache. Invoked during system refresh or reload.
	 */
	public static void clear() {
		builderCache.clear();
		log.info("[TelegramFactory] Cache cleared.");
	}

	/**
	 * Registers a new Telegram Builder using raw configuration parameters.
	 */
	public static void register(String name, Telegram.Type type, Map<String, Object> values) throws Exception {
		if (name == null || type == null) {
			log.warn("[TelegramFactory] Invalid registration attempt. Name or Type is null.");
			return;
		}

		PayloadBuilder builder = createEventBuilder(name, type, values);
		builderCache.put(name, builder);

		log.info("[TelegramFactory] Registered Telegram Builder: [{}] (Type: {})", name, type);
	}

	/**
	 * Retrieves a cached PayloadBuilder by its telegram name.
	 */
	public static PayloadBuilder getBuilder(String name) {
		if (name == null) return null;
		return builderCache.get(name);
	}

	/**
	 * Directly injects a PayloadBuilder into the cache.
	 * Primarily used for mocking in tests or programmatic registrations.
	 */
	public static void register(String name, PayloadBuilder builder) {
		if (name != null && builder != null) {
			builderCache.put(name, builder);
		}
	}

	private static PayloadBuilder createEventBuilder(String name, Telegram.Type type, Map<String, Object> values) throws Exception {
		TelegramValues parsedValues;

		if (Telegram.Type.Fixed == type) {
			parsedValues = mapper.convertValue(values, FixedValues.class);
		} else if (Telegram.Type.JSON == type) {
			parsedValues = mapper.convertValue(values, JsonValues.class);
		} else if (Telegram.Type.XML == type) {
			parsedValues = mapper.convertValue(values, XmlValues.class);
		} else {
			throw new IllegalArgumentException("Unsupported Telegram Type: " + type);
		}

		return new Telegram.Builder()
				.name(name)
				.type(type)
				.values(parsedValues)
				.build();
	}
}
