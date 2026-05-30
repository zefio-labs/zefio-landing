package io.zefio.core.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.zefio.core.beans.DynamicSchemaLoader;
import io.zefio.core.common.base.PluginMeta;
import io.zefio.core.common.base.PluginType;
import io.zefio.core.config.flow.FlowSettings;
import io.zefio.core.schema.PluginSchemaExtractor;
import io.zefio.core.telemetry.cp.ZefioCpRedisPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller responsible for providing endpoints to retrieve configuration metadata.
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
    private PluginSchemaExtractor schemaExtractor;

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
            log.info("[ConfigApi] Structural validation successful. Broadcasting cluster update payload to: {}", targetGroup);

            Map<String, Object> command = new HashMap<>();
            command.put("type", "command");
            command.put("action", "hot-reload");
            command.put("targetGroup", targetGroup);
            command.put("payload", Collections.singletonMap("yaml", yamlContent));

            redisPublisher.sendCommand(command);

            Map<String, String> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "Hot-reload update successfully broadcasted to cluster.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[ConfigApi] Cluster configuration hot-deployment aborted due to structural violation", e);
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
    public ResponseEntity<?> getDTO(@PathVariable("parameter") String parameter) {
        try {
            String targetClassName = null;
            PluginMeta filter = yamlFilterLoader.get(parameter);

            if (filter != null && filter.getDtoClassName() != null) {
                targetClassName = filter.getDtoClassName();
            } else {
                // Fallback: If no custom configuration mapping exists, assume parameter represents an absolute class path
                targetClassName = parameter;
            }

            Class<?> dtoClass = Class.forName(targetClassName);
            Map<String, Object> structuralSchema = schemaExtractor.extractSchemaDescriptions(dtoClass, new HashSet<>());
            return ResponseEntity.ok(structuralSchema);

        } catch (ClassNotFoundException e) {
            log.error("[ConfigApi] Target class mapping resolution failed for query parameter token: {}", parameter);
            return ResponseEntity.badRequest().body(Collections.singletonMap("error",
                    "Target metadata DTO reflection model path could not be resolved: " + e.getMessage()));
        }
    }
}
