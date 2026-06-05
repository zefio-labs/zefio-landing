package io.zefio.core.beans;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zefio.core.schema.DslConfigurationLoader;
import io.zefio.core.config.flow.FlowSettings;
import io.zefio.core.config.flow.FlowConfiguration;
import io.zefio.core.config.flow.StepConfiguration;
import io.zefio.core.config.global.GlobalOptionsProperties;
import io.zefio.core.PipelineService;
import io.zefio.core.factory.FlowServiceFactory;
import io.zefio.core.engine.pool.SharedPoolManager;
import io.zefio.core.engine.pool.SharedPools;
import io.zefio.core.payload.util.TelegramFactory;
import io.zefio.core.util.FlowConfigUtils;
import io.zefio.jdk.registry.ComponentRegistry;
import io.zefio.core.telemetry.GlobalMonitorManager;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Orchestrates the lifecycle of the core processing pipelines. It is responsible for
 * loading DSL configurations, mapping them to settings objects, initializing shared
 * resource pools, and managing the graceful startup and shutdown of flow services.
 */
@Component
@RefreshScope
public class FlowSettingsBean implements InitializingBean, DisposableBean {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Getter
    @Value("${server.port}")
    private int serverPort;

    @Autowired
    @Getter
    private GlobalOptionsProperties globalOptionsProperties;

    @Getter
    private FlowSettings settings;

    @Autowired
    private SharedPoolManager sharedPoolManager;

    @Autowired
    private GlobalMonitorManager monitorManager;

    @Autowired
    private FlowServiceFactory flowFactory;

    @Autowired
    private FlowSyncBridge flowSyncBridge;

    @Getter
    private final List<PipelineService> flowServiceList = new CopyOnWriteArrayList<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("[DSL Loader] Initializing system...");
        this.settings = loadConfiguration();
        applyConfiguration(this.settings);
    }

    /** 💡 Load and parse current YAML configuration */
    private FlowSettings loadConfiguration() throws Exception {
        String mainConfigPath = FlowConfigUtils.getMainConfigPath();
        log.info("[DSL Loader] Initializing Flow configuration from: {}", mainConfigPath);

        DslConfigurationLoader loader = new DslConfigurationLoader();
        Map<String, Object> mergedYamlMap = loader.loadAndMerge(mainConfigPath);

        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.convertValue(mergedYamlMap, FlowSettings.class);
    }

    /** 💡 Apply configurations to engine components */
    private void applyConfiguration(FlowSettings newSettings) {
        TelegramFactory.clear();
        if (newSettings.getTelegrams() != null) {
            newSettings.getTelegrams().forEach((name, config) -> {
                try { TelegramFactory.register(name, config.getType(), config.getConfig()); }
                catch (Exception e) { log.error("[Telegram Init] Error: " + name, e); }
            });
        }

        SharedPools pools = sharedPoolManager.setupPools();
        monitorManager.startGlobalMonitoring(pools);
        monitorManager.printConfigurationLog();

        if (newSettings.getFlows() != null) {
            newSettings.getFlows().forEach(config -> {
                PipelineService service = flowFactory.build(
                        config, pools, newSettings.getProfiles(), newSettings.getGlobalErrors()
                );
                if (service != null) flowServiceList.add(service);
            });
        }
    }

    /**
     * 💡 Refactored hotSwap process.
     * Fixed factory wiping errors and recursive registry caching memory leaks.
     */
    public synchronized void hotSwap(FlowSettings newSettings) throws Exception {
        log.info("🚀 [Hot-Swap] Initiating non-blocking Blue-Green deployment sequence...");
        try {
            SharedPools pools = sharedPoolManager.setupPools();

            // ⭐️ Fixed Defect 1: TelegramFactory.clear() is removed to preserve running transformations.
            // Map modifications are handled cleanly via implicit upsert replacement loops.
            if (newSettings.getTelegrams() != null) {
                newSettings.getTelegrams().forEach((name, config) -> {
                    try { TelegramFactory.register(name, config.getType(), config.getConfig()); }
                    catch (Exception e) { log.error("[Telegram Hot-Swap Init] Error: " + name, e); }
                });
            }

            if (newSettings.getTelegrams() == null || newSettings.getTelegrams().isEmpty()) {
                log.warn("⚠️ Deployment payload missing 'telegrams'. Retaining previous configuration state.");
                if (this.settings != null && this.settings.getTelegrams() != null) {
                    newSettings.setTelegrams(this.settings.getTelegrams());
                }
            }

            if (newSettings.getFlows() == null || newSettings.getFlows().isEmpty()) {
                log.warn("⚠️ No active flow definitions identified in the deployment payload.");
                return;
            }

            for (FlowConfiguration newFlowConfig : newSettings.getFlows()) {

                PipelineService oldFlow = null;
                for (PipelineService activeService : this.flowServiceList) {
                    if (activeService.getName().equals(newFlowConfig.getName())) {
                        oldFlow = activeService;
                        break;
                    }
                }

                if (oldFlow != null) {
                    log.info("🔄 [Hot-Swap] Existing active flow pipeline [{}] detected. Commencing swap.", oldFlow.getName());

                    // ⭐️ Fixed Defect 2: Clear cached components cleanly before factory compilation.
                    if (newFlowConfig.getIngress() != null) {
                        ComponentRegistry.unregisterIngress(newFlowConfig.getIngress().getName());
                    }
                    if (newFlowConfig.getSteps() != null) {
                        for (StepConfiguration step : newFlowConfig.getSteps()) {
                            recursiveUnregisterComponents(step);
                        }
                    }

                    // Step A: Sever only the listening layer via abstract interface mapping rules
                    oldFlow.stopListening();

                    // Step B: Instantly evict the old service from the logical lookup registry
                    this.flowServiceList.remove(oldFlow);

                    // Step C: Build the completely isolated new version instance (Compiles clean fresh components)
                    PipelineService newFlowService = flowFactory.build(
                            newFlowConfig, pools, newSettings.getProfiles(), newSettings.getGlobalErrors()
                    );

                    if (newFlowService != null) {
                        // Step D: Start the new flow safely
                        newFlowService.start();
                        this.flowServiceList.add(newFlowService);
                        log.info("✨ [Hot-Swap] New flow instance [{}] successfully claimed port and went active.", newFlowService.getName());
                    }

                    // Step E: Asynchronously monitor and drain old transactions via the Shared Scheduler
                    final PipelineService finalOldFlow = oldFlow;
                    pools.getSharedScheduledPool().submit(() -> {
                        try {
                            int checkCount = 0;
                            boolean isDrained = false;

                            log.info("[WatchDog] Monitoring transaction drain for deprecated flow pipeline: {}", finalOldFlow.getName());

                            // ⭐️ Fixed Defect 3: isAllQueueEmpty checks the precise callback execution count state
                            while (checkCount < 120) {
                                if (finalOldFlow.isAllQueueEmpty()) {
                                    isDrained = true;
                                    break;
                                }
                                Thread.sleep(500);
                                checkCount++;
                            }

                            if (!isDrained) {
                                log.warn("[WatchDog] Drain timeout exceeded for old flow [{}]. Forcing cleanup sequence.", finalOldFlow.getName());
                            } else {
                                log.info("[WatchDog] All in-flight payloads drained successfully for old flow [{}].", finalOldFlow.getName());
                            }

                            // Final resource reclamation and thread pools depletion
                            finalOldFlow.shutdown();
                            log.info("✅ [Hot-Swap] Deprecated flow pipeline [{}] resources fully reclaimed.", finalOldFlow.getName());
                        } catch (Exception e) {
                            log.error("[WatchDog] Critical failure during old flow resource reclamation", e);
                        }
                    });

                } else {
                    // Fresh pipeline provision: Instantiate and boot normally
                    PipelineService newService = flowFactory.build(
                            newFlowConfig, pools, newSettings.getProfiles(), newSettings.getGlobalErrors()
                    );
                    if (newService != null) {
                        newService.start();
                        this.flowServiceList.add(newService);
                        log.info("🆕 [Hot-Swap] Provisioned entirely new flow pipeline [{}].", newService.getName());
                    }
                }
            }

            this.settings = newSettings;
            log.info("✅ [Hot-Swap] Total pipeline Blue-Green deployment sequence finished successfully.");

        } catch (Exception e) {
            log.error("❌ [Hot-Swap] Operational failure during non-blocking pipeline reload", e);
            throw e;
        }
    }

    /**
     * Helper routine designed to recursively scour and purge stale plugin templates
     * embedded within multi-layered composite architectures (TRY_SCOPE, SWITCH cases, etc).
     */
    private void recursiveUnregisterComponents(StepConfiguration step) {
        if (step == null) return;

        // Purge memory handles for the given name context
        ComponentRegistry.unregisterUpstream(step.getName());
        ComponentRegistry.unregisterInterceptor(step.getName());
        ComponentRegistry.unregisterError(step.getName());

        // Recurse down inside nested scope steps loops
        if (step.getSteps() != null) {
            for (StepConfiguration subStep : step.getSteps()) {
                recursiveUnregisterComponents(subStep);
            }
        }
        if (step.getFallbackSteps() != null) {
            for (StepConfiguration fallbackStep : step.getFallbackSteps()) {
                recursiveUnregisterComponents(fallbackStep);
            }
        }
        if (step.getCases() != null) {
            for (io.zefio.core.config.flow.SwitchCaseConfig caseConfig : step.getCases()) {
                if (caseConfig.getSteps() != null) {
                    for (StepConfiguration caseStep : caseConfig.getSteps()) {
                        recursiveUnregisterComponents(caseStep);
                    }
                }
            }
        }
        if (step.getDefaultSteps() != null) {
            for (StepConfiguration defStep : step.getDefaultSteps()) {
                recursiveUnregisterComponents(defStep);
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        log.info("Starting graceful shutdown for FlowSettingsBean...");
        if (!this.flowServiceList.isEmpty()) {
            for (PipelineService flow : this.flowServiceList) { flow.shutdown(); }
            this.flowServiceList.clear();
        }
        if (flowSyncBridge != null) flowSyncBridge.destroy();
        if (sharedPoolManager != null) sharedPoolManager.destroy();
        ComponentRegistry.clear();
    }
}
