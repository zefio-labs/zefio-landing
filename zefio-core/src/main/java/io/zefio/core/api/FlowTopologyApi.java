package io.zefio.core.api;

import io.zefio.core.config.flow.FlowConfiguration;
import io.zefio.core.topology.FlowTopologyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/base/topology")
@RequiredArgsConstructor
public class FlowTopologyApi {

    private final FlowTopologyRegistry registry;

    /**
     * [For AI Analysis] Detailed tree of all running flows (YAML converted to JSON)
     */
    @GetMapping("/flows")
    public ResponseEntity<List<FlowConfiguration>> getAllFlowsDetail() {
        return ResponseEntity.ok(registry.getAllFlows());
    }

    /**
     * [For AI Precision Scan] Extract the detailed tree of a specific flow
     */
    @GetMapping("/flows/{flowName}")
    public ResponseEntity<FlowConfiguration> getSpecificFlow(@PathVariable String flowName) {
        return registry.getFlowByName(flowName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * [Global Environment Lookup] Profiles, Endpoints, Telegrams
     */
    @GetMapping("/globals")
    public ResponseEntity<Map<String, Object>> getGlobals() {
        Map<String, Object> globals = new LinkedHashMap<>();
        globals.put("profiles", registry.getGlobalProfiles());
        globals.put("endpoints", registry.getGlobalEndpoints());
        globals.put("telegrams", registry.getGlobalTelegrams());
        return ResponseEntity.ok(globals);
    }
}
