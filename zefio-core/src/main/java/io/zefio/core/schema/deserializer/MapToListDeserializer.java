package io.zefio.core.schema.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.*;

/**
 * Abstract generic Jackson deserializer designed to parse varying JSON structures
 * (arrays, single objects, or map-like objects with numeric index keys) into a List of a specified type.
 */
public abstract class MapToListDeserializer<T> extends StdDeserializer<List<T>> {

    private final Class<T> valueClass;

    public MapToListDeserializer(Class<T> valueClass) {
        super(List.class);
        this.valueClass = valueClass;
    }

    private boolean isIgnorable(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ||
                (node.isTextual() && node.asText().trim().isEmpty());
    }

    @Override
    public List<T> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectCodec codec = p.getCodec();
        JsonNode node = codec.readTree(p);
        List<T> result = new ArrayList<>();

        if (isIgnorable(node)) {
            return result;
        }

        if (node.isArray()) {
            JsonParser arrayParser = node.traverse(codec);
            arrayParser.nextToken();
            result.addAll(ctxt.readValue(arrayParser, ctxt.getTypeFactory().constructCollectionType(List.class, valueClass)));
        } else if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.properties().iterator();

            Map<Integer, JsonNode> tempMap = new TreeMap<>();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                try {
                    int idx = Integer.parseInt(entry.getKey());
                    tempMap.put(idx, entry.getValue());
                } catch (NumberFormatException e) {
                }
            }

            for (JsonNode element : tempMap.values()) {
                if (!isIgnorable(element)) {
                    T item = codec.treeToValue(element, this.valueClass);
                    result.add(item);
                }
            }
        } else {
            if (!isIgnorable(node)) {
                T item = codec.treeToValue(node, this.valueClass);
                result.add(item);
            }
        }

        return result;
    }
}
