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
            log.info("[DP-Seed] Validation passed. Broadcasting hot-reload command to group: {}", targetGroup);

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

        return schemaExtractor.extractSchemaDescriptions(dtoClass, new HashSet<>());
    }
}
