package io.zefio.core.schema.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Custom Jackson deserializer to parse an ISO-8601 formatted date-time string into a java.time.ZonedDateTime object.
 */
public class ZonedDateTimeDeserializer extends JsonDeserializer<ZonedDateTime> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_ZONED_DATE_TIME;

    @Override
    public ZonedDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws java.io.IOException {
        String dateStr = p.getText().trim();
        return ZonedDateTime.parse(dateStr, FORMATTER);
    }
}
