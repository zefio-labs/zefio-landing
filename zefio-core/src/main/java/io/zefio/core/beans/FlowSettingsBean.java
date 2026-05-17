package io.zefio.core.beans;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zefio.core.schema.DslConfigurationLoader;
import io.zefio.core.config.flow.FlowSettings;
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
        this.flowServiceList.clear();

        String mainConfigPath = FlowConfigUtils.getMainConfigPath();
        log.info("[DSL Loader] Initializing Flow configuration from: {}", mainConfigPath);

        DslConfigurationLoader loader = new DslConfigurationLoader();
        Map<String, Object> mergedYamlMap = loader.loadAndMerge(mainConfigPath);

        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.settings = mapper.convertValue(mergedYamlMap, FlowSettings.class);

        TelegramFactory.clear();
        if (settings.getTelegrams() != null) {
            settings.getTelegrams().forEach((name, config) -> {
                try {
                    TelegramFactory.register(name, config.getType(), config.getConfig());
                } catch (Exception e) {
                    log.error("[Telegram Init] Failed to register telegram: " + name, e);
                }
            });
        }

        SharedPools pools = sharedPoolManager.setupPools();

        monitorManager.startGlobalMonitoring(pools);
        monitorManager.printConfigurationLog();

        if (settings.getFlows() != null) {
            settings.getFlows().forEach(config -> {
                PipelineService service = flowFactory.build(
                        config,
                        pools,
                        settings.getProfiles(),
                        settings.getGlobalErrors()
                );

                if (service != null) {
                    flowServiceList.add(service);
                }
            });
        }
    }

    @Override
    public void destroy() throws Exception {
        log.info("Starting graceful shutdown for FlowSettingsBean...");

        if (!this.flowServiceList.isEmpty()) {
            for (PipelineService flow : this.flowServiceList) {
                flow.shutdown();
            }
            this.flowServiceList.clear();
        }

        if (flowSyncBridge != null) {
            flowSyncBridge.destroy();
        }

        if (sharedPoolManager != null) {
            sharedPoolManager.destroy();
        }

        ComponentRegistry.clear();
    }
}
