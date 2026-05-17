package io.zefio.core.factory;

import dev.failsafe.RetryPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import io.zefio.core.GatewayInterceptor;
import io.zefio.core.Ingress;
import io.zefio.core.PipelineService;
import io.zefio.core.beans.FlowSyncBridge;
import io.zefio.core.common.base.ScopeType;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.util.DeepMergeUtils;
import io.zefio.core.engine.flow.FlowInitContext;
import io.zefio.core.engine.flow.FlowService;
import io.zefio.core.engine.policy.ExceptionPolicyManager;
import io.zefio.core.engine.pool.SharedPools;
import io.zefio.core.engine.processor.*;
import io.zefio.core.engine.processor.dto.SwitchBranch;
import io.zefio.core.payload.ExchangePattern;
import io.zefio.core.payload.Payload;
import io.zefio.core.config.flow.*;
import io.zefio.core.config.global.GlobalOptionsProperties;
import io.zefio.core.config.monitor.MonitorProperties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A factory component responsible for assembling PipelineService instances.
 * It parses flow configurations, merges global profiles, constructs recursive
 * processing pipelines (including resilient scopes, scatter-gather routers,
 * and conditional selectors), and initializes ingress modules and error handling pipelines.
 */
@Component
public class FlowServiceFactory {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private PluginFactory pluginFactory;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private GlobalOptionsProperties globalOptionsProperties;
    @Autowired
    private MonitorProperties monitorProperties;
    @Autowired
    private ExceptionPolicyManager policyManager;
    @Autowired
    private FlowSyncBridge flowSyncBridge;

    public PipelineService build(FlowConfiguration f, SharedPools sharedPools,
                                 Map<String, Object> globalProfiles,
                                 Map<String, StepConfiguration> globalErrors) {
        try {
            String ingressTelegramKey = f.getIngress().getTelegram();

            List<Processor> rootPipeline = buildPipeline(f.getSteps(), f, sharedPools, globalProfiles);
            mergeProfile(f.getIngress(), globalProfiles);

            PluginContext ingressCtx = buildContext(f, ingressTelegramKey, sharedPools)
                    .exchangePattern(f.getIngress().getExchangePattern()).build();

            Ingress ingress = pluginFactory.createIngress(f.getIngress().getType(), f.getIngress().getClazz(), ingressCtx);

            Map<String, List<Processor>> errorPipelines = new java.util.LinkedHashMap<>();

            if (f.getOnError() != null) {
                for (ErrorHandlerConfiguration errConf : f.getOnError()) {
                    String errorType = StringUtils.isNotBlank(errConf.getErrorType()) ? errConf.getErrorType() : "ANY";
                    List<Processor> pipeline = new ArrayList<>();

                    if (StringUtils.isNotBlank(errConf.getRefErrorHandler()) && globalErrors.containsKey(errConf.getRefErrorHandler())) {
                        String refKey = errConf.getRefErrorHandler();
                        StepConfiguration globalStep = globalErrors.get(refKey);
                        if (StringUtils.isBlank(globalStep.getName())) {
                            globalStep.setName(refKey);
                        }

                        pipeline = buildPipeline(Collections.singletonList(globalStep), f, sharedPools, globalProfiles);
                    }
                    else if (errConf.getSteps() != null && !errConf.getSteps().isEmpty()) {
                        pipeline = buildPipeline(errConf.getSteps(), f, sharedPools, globalProfiles);
                    }

                    if (!pipeline.isEmpty()) {
                        errorPipelines.put(errorType, pipeline);
                    }
                }
            }

            FlowInitContext ctx = FlowInitContext.builder()
                    .flowName(f.getName()).flowLabel(f.getLabel()).options(f.getOptions())
                    .ingress(ingress).rootPipeline(rootPipeline)
                    .errorPipelines(errorPipelines)
                    .policyManager(policyManager).sharedPools(sharedPools)
                    .meterRegistry(meterRegistry).monitorProperties(monitorProperties)
                    .build();

            PipelineService service = new FlowService(ctx);
            service.initialise();
            return service;

        } catch (Exception e) {
            log.error("[Factory] Build failed for flow: {}", f.getName(), e);
            return null;
        }
    }

    private RetryPolicy<Payload> buildNodeRetryPolicy(GlobalOptionsProperties.DefaultRetry stepRetry) {
        GlobalOptionsProperties.DefaultRetry global = globalOptionsProperties.getDefaultRetry();

        boolean enabled = (stepRetry != null && stepRetry.getEnabled() != null) ? stepRetry.getEnabled() : global.getEnabled();
        int maxRetries = (stepRetry != null && stepRetry.getMaxRetries() != null) ? stepRetry.getMaxRetries() : global.getMaxRetries();
        int delay = (stepRetry != null && stepRetry.getDelay() != null) ? stepRetry.getDelay() : global.getDelay();

        if (!enabled || maxRetries <= 0) {
            return RetryPolicy.<Payload>builder().withMaxRetries(0).build();
        }

        return RetryPolicy.<Payload>builder()
                .handleIf(ex -> {
                    if (ex instanceof FlowException) {
                        return policyManager.isRetryable(((FlowException) ex).getStatus());
                    }
                    return false;
                })
                .withDelay(java.time.Duration.ofMillis(delay))
                .withMaxRetries(maxRetries)
                .onRetry(retries -> log.info("Node Retry: Attempt {}", retries.getAttemptCount()))
                .build();
    }

    private List<Processor> buildPipeline(List<StepConfiguration> steps, FlowConfiguration f,
                                          SharedPools sharedPools,
                                          Map<String, Object> globalProfiles) {
        List<Processor> pipeline = new ArrayList<>();

        for (StepConfiguration stepConfig : steps) {
            mergeProfile(stepConfig, globalProfiles);

            String stepTelegramKey = stepConfig.getTelegram();
            Processor processor;
            ScopeType scopeType = stepConfig.getScopeType();

            switch (scopeType) {
                case TRY_SCOPE:
                    List<Processor> tryChild = buildPipeline(stepConfig.getSteps(), f, sharedPools, globalProfiles);
                    List<Processor> fallback = new ArrayList<>();
                    if (stepConfig.getOnError() == OnErrorPolicy.FALLBACK && stepConfig.getFallbackSteps() != null && !stepConfig.getFallbackSteps().isEmpty()) {
                        fallback = buildPipeline(stepConfig.getFallbackSteps(), f, sharedPools, globalProfiles);
                    }

                    RetryPolicy<Payload> scopeRetryPolicy = buildNodeRetryPolicy(stepConfig.getRetry());

                    processor = new ResilientScopeHandler(
                            stepConfig.getName(),
                            tryChild,
                            fallback,
                            stepConfig.getOnError(),
                            scopeRetryPolicy,
                            sharedPools.getFailsafe()
                    );
                    break;

                case SCATTER_GATHER:
                    List<Processor> sgChildren = buildPipeline(stepConfig.getSteps(), f, sharedPools, globalProfiles);
                    processor = new ParallelScatterGatherRouter(
                            stepConfig.getName(),
                            sgChildren,
                            stepConfig.getConfig(),
                            sharedPools.getSharedScheduledPool()
                    );
                    break;

                case SWITCH:
                    List<SwitchBranch> branches = new ArrayList<>();
                    if (stepConfig.getCases() != null) {
                        for (SwitchCaseConfig cc : stepConfig.getCases()) {
                            branches.add(new SwitchBranch(cc.getCondition(),
                                    buildPipeline(cc.getSteps(), f, sharedPools, globalProfiles)));
                        }
                    }
                    List<Processor> defBranch = (stepConfig.getDefaultSteps() != null) ?
                            buildPipeline(stepConfig.getDefaultSteps(), f, sharedPools, globalProfiles) : null;
                    processor = new ConditionalRouteSelector(stepConfig.getName(), branches, defBranch);
                    break;

                case UNKNOWN:
                default:
                    PluginContext ctx = buildContext(f, stepConfig, stepTelegramKey, sharedPools)
                            .exchangePattern(stepConfig.getExchangePattern())
                            .build();
                    GatewayInterceptor interceptor = pluginFactory.createByAutoDetection(stepConfig.getType(), stepConfig.getClazz(), ctx);
                    interceptor.setRetryPolicy(buildNodeRetryPolicy(stepConfig.getRetry()));
                    processor = new LeafFilterProcessor(interceptor, sharedPools.getFailsafe());
                    break;
            }
            pipeline.add(processor);
        }
        return pipeline;
    }

    private PluginContext.PluginContextBuilder buildContext(FlowConfiguration f, String telegramName, SharedPools sharedPools) {
        IngressConfiguration ingress = f.getIngress();
        String moduleLabel = StringUtils.isNotBlank(ingress.getLabel()) ? ingress.getLabel() : ingress.getName();
        return buildCoreContext(f, ingress.getName(), moduleLabel, ingress.getConfig(), telegramName, sharedPools);
    }

    private PluginContext.PluginContextBuilder buildContext(FlowConfiguration f, StepConfiguration step, String telegramName, SharedPools sharedPools) {
        String moduleLabel = StringUtils.isNotBlank(step.getLabel()) ? step.getLabel() : step.getName();
        return buildCoreContext(f, step.getName(), moduleLabel, step.getConfig(), telegramName, sharedPools);
    }

    private PluginContext.PluginContextBuilder buildCoreContext(
            FlowConfiguration f, String moduleName, String moduleLabel,
            Map<String, Object> configMap, String telegramName, SharedPools sharedPools) {

        return PluginContext.builder()
                .flowName(f.getName()).flowLabel(f.getLabel())
                .pluginName(moduleName).pluginLabel(moduleLabel)
                .telegramName(telegramName)
                .context(configMap)
                .sharedScheduledPool(sharedPools.getSharedScheduledPool())
                .sharedIoPool(sharedPools.getSharedMdcIoExecutor())
                .flowSyncBridge(flowSyncBridge)
                .meterRegistry(meterRegistry);
    }

    private void mergeProfile(IngressConfiguration ingressConfig, Map<String, Object> globalProfiles) {
        if (ingressConfig != null && StringUtils.isNotBlank(ingressConfig.getProfile())
                && globalProfiles != null && globalProfiles.containsKey(ingressConfig.getProfile())) {
            Map<String, Object> profileData = (Map<String, Object>) globalProfiles.get(ingressConfig.getProfile());

            if (profileData.containsKey("config") && profileData.get("config") instanceof Map) {
                Map<String, Object> profileConfig = (Map<String, Object>) profileData.get("config");
                Map<String, Object> mergedConfig = DeepMergeUtils.mergeClone(profileConfig, ingressConfig.getConfig());
                ingressConfig.setConfig(mergedConfig);
            }

            if (profileData.containsKey("exchangePattern") && ingressConfig.getExchangePattern() == null) {
                ingressConfig.setExchangePattern(ExchangePattern.valueOf(profileData.get("exchangePattern").toString()));
            }
        }
    }

    private void mergeProfile(StepConfiguration stepConfig, Map<String, Object> globalProfiles) {
        if (StringUtils.isNotBlank(stepConfig.getProfile()) && globalProfiles != null && globalProfiles.containsKey(stepConfig.getProfile())) {
            Map<String, Object> profileData = (Map<String, Object>) globalProfiles.get(stepConfig.getProfile());

            if (profileData.containsKey("config") && profileData.get("config") instanceof Map) {
                Map<String, Object> profileConfig = (Map<String, Object>) profileData.get("config");
                Map<String, Object> mergedConfig = DeepMergeUtils.mergeClone(profileConfig, stepConfig.getConfig());
                stepConfig.setConfig(mergedConfig);
            }

            if (profileData.containsKey("exchangePattern") && stepConfig.getExchangePattern() == null) {
                stepConfig.setExchangePattern(ExchangePattern.valueOf(profileData.get("exchangePattern").toString()));
            }
        }
    }
}
