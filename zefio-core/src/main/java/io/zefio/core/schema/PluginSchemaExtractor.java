package io.zefio.core.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.jdk.annotation.ZefioNotBlank;
import io.zefio.jdk.annotation.ZefioNotEmpty;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Utility component responsible for dynamically extracting schema metadata
 * from DTO classes using reflection. It resolves AIOps metadata, Swagger schemas,
 * and recursively traverses complex nested structures.
 */
@Component
public class PluginSchemaExtractor {

    public Map<String, Object> extractSchemaDescriptions(Class<?> clazz, Set<Class<?>> visited) {
        // 1st Defense: If the class is already being scanned, return an empty map to prevent circular references (StackOverflow)
        if (visited.contains(clazz)) {
            return new TreeMap<>();
        }
        visited.add(clazz);

        Map<String, Object> metadata = new TreeMap<>();
        Class<?> current = clazz;

        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {

                // 2nd Defense: Ignore static or transient fields as they are not configuration properties
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }

                String name;
                JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
                name = (jsonProperty != null && !jsonProperty.value().isEmpty()) ? jsonProperty.value() : field.getName();

                if (metadata.containsKey(name)) continue;

                Schema schema = field.getAnnotation(Schema.class);
                Class<?> targetType = field.getType();
                boolean isJsonUnwrapped = field.isAnnotationPresent(JsonUnwrapped.class);

                Map<String, Object> fieldInfo = new LinkedHashMap<>();

                // Extract AIOps configuration tuning constraints
                AIOpsTuning tuning = field.getAnnotation(AIOpsTuning.class);
                if (tuning != null) {
                    Map<String, Object> aiopsMeta = new LinkedHashMap<>();
                    aiopsMeta.put("hotDeployable", tuning.hotDeployable());
                    aiopsMeta.put("restartRequired", tuning.restartRequired());
                    aiopsMeta.put("riskLevel", tuning.riskLevel().name());
                    aiopsMeta.put("category", tuning.category().name());

                    if (!tuning.min().isEmpty()) aiopsMeta.put("min", tuning.min());
                    if (!tuning.max().isEmpty()) aiopsMeta.put("max", tuning.max());

                    fieldInfo.put("_aiops", aiopsMeta);
                }

                String description = (schema != null && !schema.description().isEmpty()) ? schema.description() : "No description available";
                fieldInfo.put("_description", description);
                fieldInfo.put("_required", (field.isAnnotationPresent(ZefioNotBlank.class) || field.isAnnotationPresent(ZefioNotEmpty.class)) ? "Required" : "Optional");

                if (schema != null) {
                    fieldInfo.put("_nullable", schema.nullable() ? "Nullable" : "Not Nullable");
                    if (!schema.example().isEmpty()) fieldInfo.put("_example", schema.example());
                    if (!schema.defaultValue().isEmpty()) fieldInfo.put("_default", schema.defaultValue());
                }

                boolean isCollection = Collection.class.isAssignableFrom(targetType) || targetType.isArray();

                // 3rd Defense: Block primitives and 3rd party frameworks (e.g., Spring) from being deeply scanned
                boolean isSimpleType = targetType.isPrimitive()
                        || targetType.getName().startsWith("java.")
                        || targetType.getName().startsWith("javax.")
                        || targetType.getName().startsWith("org.springframework.")
                        || targetType.isEnum();

                if (isCollection) {
                    fieldInfo.put("_type", "Array");
                    Class<?> itemClass = null;

                    if (schema != null && !schema.implementation().equals(Void.class)) {
                        itemClass = schema.implementation();
                    } else if (Collection.class.isAssignableFrom(targetType)) {
                        java.lang.reflect.Type genericType = field.getGenericType();
                        if (genericType instanceof java.lang.reflect.ParameterizedType) {
                            java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) genericType;
                            itemClass = (Class<?>) pt.getActualTypeArguments()[0];
                        }
                    } else if (targetType.isArray()) {
                        itemClass = targetType.getComponentType();
                    }

                    // Recursive scan only if the generic class in the array is a user-defined domain object
                    if (itemClass != null && !itemClass.getName().startsWith("java.") && !itemClass.getName().startsWith("org.springframework.")) {
                        fieldInfo.put("_fields", extractSchemaDescriptions(itemClass, new HashSet<>(visited)));
                    }
                    metadata.put(name, fieldInfo);

                } else if (!isSimpleType) {
                    // Recursive entry only for user-defined DTOs (complex objects)
                    Map<String, Object> nested = extractSchemaDescriptions(targetType, new HashSet<>(visited));
                    if (isJsonUnwrapped) {
                        metadata.putAll(nested);
                    } else {
                        fieldInfo.put("_fields", nested);
                        metadata.put(name, fieldInfo);
                    }
                } else {
                    metadata.put(name, fieldInfo);
                }
            }
            current = current.getSuperclass();
        }
        visited.remove(clazz); // Clear visit record after scanning is complete
        return metadata;
    }
}
