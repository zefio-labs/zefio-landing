package io.zefio.core.telemetry.cp;

import io.zefio.core.beans.DynamicSchemaLoader;
import io.zefio.core.common.base.PluginMeta;
import io.zefio.core.config.ZefioProperties;
import io.zefio.core.schema.PluginSchemaExtractor;
import io.zefio.core.topology.FlowTopologyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bootstrap service that ensures a resilient handshake with the Control Plane (CP).
 * If the CP is unreachable at startup, it automatically schedules retries until
 * the master templates are successfully registered as the Immutable AI Context.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CpHandshakeService implements DisposableBean {

    private final FlowTopologyRegistry topologyRegistry;
    private final DynamicSchemaLoader schemaLoader;
    private final PluginSchemaExtractor schemaExtractor;
    private final ZefioProperties zefioProperties;

    @Value("${zefio.cp.api-url:http://localhost:3000}")
    private String cpApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @EventListener(ApplicationReadyEvent.class)
    public void executeHandshake() {
        if (!zefioProperties.getCp().isEnabled()) {
            return;
        }

        log.info("[DP-Handshake] Initializing CP handshake with URL: {}", cpApiUrl);
        // Start the retryable handshake loop
        scheduleHandshake(0);
    }

    private void scheduleHandshake(int attempt) {
        log.info("[DP-Handshake] Handshake attempt {} starting...", attempt + 1);

        try {
            Map<String, Object> payload = compilePayload();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            String targetUrl = cpApiUrl + "/api/sync/templates";
            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[DP-Handshake] ✅ Successfully registered Master Templates to CP.");
            } else {
                handleFailure(attempt, "CP returned status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            handleFailure(attempt, e.getMessage());
        }
    }

    /**
     * Handles failures by scheduling a retry after a delay.
     */
    private void handleFailure(int attempt, String reason) {
        int maxAttempts = 50; // High limit to ensure resilience over long periods
        if (attempt < maxAttempts) {
            log.warn("[DP-Handshake] ⚠️ CP unreachable. Retrying in 10s... (Attempt {}, Error: {})", attempt + 1, reason);
            scheduler.schedule(() -> scheduleHandshake(attempt + 1), 10, TimeUnit.SECONDS);
        } else {
            log.error("[DP-Handshake] ❌ Max retries reached. Handshake aborted.");
        }
    }

    /**
     * Compiles the immutable context (Globals + Plugin Schemas) into a single payload.
     */
    private Map<String, Object> compilePayload() throws ClassNotFoundException {
        Map<String, Object> payload = new HashMap<>();

        // 1. Immutable Globals
        Map<String, Object> globals = new HashMap<>();
        globals.put("profiles", topologyRegistry.getGlobalProfiles());
        globals.put("endpoints", topologyRegistry.getGlobalEndpoints());
        globals.put("telegrams", topologyRegistry.getGlobalTelegrams());
        payload.put("globals", globals);

        // 2. Plugin Metadata & Schemas
        List<Map<String, Object>> plugins = new ArrayList<>();
        for (PluginMeta meta : schemaLoader.getAllFilters().values()) {
            Map<String, Object> plugin = new HashMap<>();
            plugin.put("name", meta.getName());
            plugin.put("type", meta.getType());
            plugin.put("className", meta.getClassName());

            if (meta.getDtoClassName() != null) {
                try {
                    Class<?> dtoClass = Class.forName(meta.getDtoClassName());
                    plugin.put("schema", schemaExtractor.extractSchemaDescriptions(dtoClass, new HashSet<>()));
                } catch (Exception e) {
                    log.warn("[DP-Handshake] Schema extraction skipped for: {}", meta.getName());
                }
            }
            plugins.add(plugin);
        }
        payload.put("plugins", plugins);

        return payload;
    }

    @Override
    public void destroy() {
        // Ensure the scheduler is cleaned up on application shutdown
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }
}
