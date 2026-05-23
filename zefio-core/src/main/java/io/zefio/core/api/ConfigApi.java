package io.zefio.core.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.annotations.media.Schema;
import io.zefio.core.annotation.AIOpsTuning;
import io.zefio.core.beans.DynamicSchemaLoader;
import io.zefio.core.common.base.PluginMeta;
import io.zefio.core.common.base.PluginType;
import io.zefio.core.config.flow.FlowSettings;
import io.zefio.core.telemetry.cp.ZefioCpRedisPublisher;
import io.zefio.jdk.annotation.ZefioNotBlank;
import io.zefio.jdk.annotation.ZefioNotEmpty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * REST Controller responsible for providing endpoints to retrieve configuration metadata,
 * plugin details, and dynamically extracted schema information from DTO classes using reflection.
 */
@Slf4j
@RestController
@RequestMapping("/base/config")
public class ConfigApi {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Autowired
    private DynamicSchemaLoader yamlFilterLoader;

    @Autowired
    private ZefioCpRedisPublisher redisPublisher;

    @PostMapping(value = "/reload", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reloadConfig(@RequestBody Map<String, String> payload) {
        try {
            String yamlContent = payload.get("yaml");
            String targetGroup = payload.getOrDefault("targetGroup", "main");

            // 1. Fail-Fast Validation: Ensure the YAML conforms to FlowSettings DTO
            yamlMapper.readValue(yamlContent, FlowSettings.class);

            // 2. Broadcast to all DP nodes via Redis
            log.info("[DP-Seed] Validation passed. Broadcasting command to group: {}", targetGroup);

            Map<String, Object> command = new HashMap<>();
            command.put("type", "command");
            command.put("action", "hot-reload");
            command.put("targetGroup", targetGroup);
            command.put("payload", Collections.singletonMap("yaml", yamlContent));

            redisPublisher.sendCommand(command);

            Map<String, String> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "Broadcasted to cluster");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[DP-Seed] Validation Failed", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("status", "FAIL");
            errorResponse.put("reason", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping(value = "")
    public Object getConfig(@RequestParam(name = "moduleName", required = false) String moduleName,
                            @RequestParam(name = "type", required = false) PluginType type) {
        if (moduleName == null && type == null) {
            return yamlFilterLoader.getAllFilters().values();
        } else if (moduleName == null && type != null) {
            return yamlFilterLoader.getByType(type);
        } else {
            return yamlFilterLoader.get(moduleName);
        }
    }

    @GetMapping(value = "/dto/{parameter}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object getDTO(@PathVariable("parameter") String parameter) throws ClassNotFoundException {
        PluginMeta filter = yamlFilterLoader.get(parameter);
        if (filter == null || filter.getDtoClassName() == null) {
            return Collections.singletonMap("error", "Schema not found for parameter: " + parameter);
        }
        Class<?> dtoClass = Class.forName(filter.getDtoClassName());

        // 🚀 Start invocation by passing a Set to prevent circular references
        return extractSchemaDescriptions(dtoClass, new HashSet<>());
    }

    private Map<String, Object> extractSchemaDescriptions(Class<?> clazz, Set<Class<?>> visited) {
        // 🛡️ 1st Defense: If the class is already being scanned, return an empty map to prevent circular references (StackOverflow)
        if (visited.contains(clazz)) {
            return new TreeMap<>();
        }
        visited.add(clazz);

        Map<String, Object> metadata = new TreeMap<>();
        Class<?> current = clazz;

        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {

                // 🛡️ 2nd Defense: Ignore static or transient fields as they are not configuration properties (defends against MediaType.ALL, etc.)
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

                AIOpsTuning tuning = field.getAnnotation(AIOpsTuning.class);
                if (tuning != null) {
                    Map<String, Object> aiopsMeta = new LinkedHashMap<>();
                    aiopsMeta.put("hotDeployable", tuning.hotDeployable());
                    aiopsMeta.put("restartRequired", tuning.restartRequired());
                    aiopsMeta.put("riskLevel", tuning.riskLevel().name());
                    aiopsMeta.put("category", tuning.category().name());

                    if (!tuning.min().isEmpty()) aiopsMeta.put("min", tuning.min());
                    if (!tuning.max().isEmpty()) aiopsMeta.put("max", tuning.max());

                    // Embed in the final metadata as a separate '_aiops' block.
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

                // 🛡️ 3rd Defense: Block primitives and 3rd party libraries that shouldn't be deep-scanned
                boolean isSimpleType = targetType.isPrimitive()
                        || targetType.getName().startsWith("java.")
                        || targetType.getName().startsWith("javax.")
                        || targetType.getName().startsWith("org.springframework.") // Core: Block scanning of Spring objects
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

                    // Recursive scan only if the generic class in the array is a complex domain object
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
