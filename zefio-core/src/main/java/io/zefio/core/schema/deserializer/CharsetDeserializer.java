package io.zefio.core.schema.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Custom Jackson deserializer to convert a string representation of a character set into a java.nio.charset.Charset object.
 */
public class CharsetDeserializer extends JsonDeserializer<Charset> {
	@Override
	public Charset deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		String value = p.getValueAsString();
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		try {
			return Charset.forName(value);
		} catch (Exception e) {
			throw new IOException("Invalid charset: " + value, e);
		}
	}
}
