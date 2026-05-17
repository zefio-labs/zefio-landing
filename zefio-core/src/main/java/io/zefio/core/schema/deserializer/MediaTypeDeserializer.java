package io.zefio.core.schema.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * Custom Jackson deserializer to parse a string representation of a media type into a
 * Spring Framework MediaType object, falling back to MediaType.ALL if the string is invalid or empty.
 */
public class MediaTypeDeserializer extends JsonDeserializer<MediaType> {
	@Override
	public MediaType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		String value = p.getText();
		if (value == null || value.trim().isEmpty()) {
			return MediaType.ALL;
		}
		try {
			return MediaType.parseMediaType(value.trim());
		} catch (IllegalArgumentException e) {
			return MediaType.ALL;
		}
	}
}
