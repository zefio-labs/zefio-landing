package io.zefio.gateway.http.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.http.HttpMethod;

import java.io.IOException;

/**
 * Custom Jackson deserializer for HTTP Methods.
 * Safely converts string values from configurations into HttpMethod enums,
 * defaulting to GET if the input is missing or invalid.
 */
public class HttpMethodDeserializer extends JsonDeserializer<HttpMethod> {

	@Override
	public HttpMethod deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		String value = p.getValueAsString();

		if (value == null || value.trim().isEmpty()) {
			return HttpMethod.GET; // Fallback to default HTTP method
		}

		try {
			return HttpMethod.valueOf(value.trim().toUpperCase()); // Case-insensitive parsing
		} catch (IllegalArgumentException e) {
			return HttpMethod.GET; // Fallback for invalid HTTP methods
		}
	}
}
