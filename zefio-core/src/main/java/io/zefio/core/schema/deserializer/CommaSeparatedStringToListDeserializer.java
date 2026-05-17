package io.zefio.core.schema.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom Jackson deserializer to parse a comma-separated string into a List of trimmed, non-empty Strings.
 */
public class CommaSeparatedStringToListDeserializer extends JsonDeserializer<List<String>> {
	@Override
	public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		String raw = p.getValueAsString();
		if (raw == null || raw.trim().isEmpty()) {
			return Collections.emptyList();
		}

		return Arrays.stream(raw.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList());
	}
}
