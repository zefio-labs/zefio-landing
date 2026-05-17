package io.zefio.core.topology;

import io.zefio.core.beans.FlowSettingsBean;
import io.zefio.core.config.flow.FlowConfiguration;
import io.zefio.core.config.flow.StepConfiguration;
import io.zefio.core.config.flow.TelegramsConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Central repository providing the current runtime topology state of the Zefio engine.
 * It ensures 100% accurate runtime state by directly referencing the internal DTO of
 * FlowSettingsBean parsed directly by DslConfigurationLoader, rather than relying on
 * Spring's auto-injected configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowTopologyRegistry {

    // 🚀 Core: Directly inject the Bean that manages the engine lifecycle
    private final FlowSettingsBean flowSettingsBean;

    public List<FlowConfiguration> getAllFlows() {
        if (flowSettingsBean.getSettings() == null || flowSettingsBean.getSettings().getFlows() == null) {
            log.warn("[Topology Registry] Flows list is currently empty or not initialized yet.");
            return Collections.emptyList();
        }
        return flowSettingsBean.getSettings().getFlows();
    }

    public Optional<FlowConfiguration> getFlowByName(String flowName) {
        return getAllFlows().stream()
                .filter(flow -> flow.getName().equals(flowName))
                .findFirst();
    }

    public Map<String, Object> getGlobalProfiles() {
        if (flowSettingsBean.getSettings() == null || flowSettingsBean.getSettings().getProfiles() == null) {
            return Collections.emptyMap();
        }
        return flowSettingsBean.getSettings().getProfiles();
    }

    public List<StepConfiguration> getGlobalEndpoints() {
        if (flowSettingsBean.getSettings() == null || flowSettingsBean.getSettings().getEndpoints() == null) {
            return Collections.emptyList();
        }
        return flowSettingsBean.getSettings().getEndpoints();
    }

    public Map<String, TelegramsConfiguration> getGlobalTelegrams() {
        if (flowSettingsBean.getSettings() == null || flowSettingsBean.getSettings().getTelegrams() == null) {
            return Collections.emptyMap();
        }
        return flowSettingsBean.getSettings().getTelegrams();
    }
}
