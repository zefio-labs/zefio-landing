package io.zefio.gateway.filter.routing;

import io.zefio.core.common.base.PluginType;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.payload.Payload;
import io.zefio.core.config.flow.StepConfiguration;
import io.zefio.core.BaseGatewayPlugin;
import io.zefio.core.GatewayInterceptor;
import io.zefio.core.beans.ApplicationContextProvider;
import io.zefio.core.beans.DynamicSchemaLoader;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.util.FlowConfigUtils;
import io.zefio.core.util.MDCUtils;
import io.zefio.core.payload.ExchangePattern;
import io.zefio.core.payload.spel.PayloadExpressionEvaluator;
import io.zefio.gateway.filter.routing.dto.SpELRouterInterceptorValues;
import io.zefio.gateway.filter.routing.dto.SpELRoutingRule;
import io.zefio.core.telemetry.module.ModuleMetricsAggregator;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Intelligent router that evaluates SpEL expressions to dynamically
 * select target modules for payload delegation.
 */
public class SpELRouterInterceptor extends BaseGatewayPlugin implements GatewayInterceptor {
    private final SpELRouterInterceptorValues values;
    private final Map<String, GatewayInterceptor> toolModuleMap = new HashMap<>();

    public SpELRouterInterceptor(PluginContext context) {
        super(context, new ModuleMetricsAggregator(PluginType.interceptor, context.getFlowName() + "-" + context.getPluginName()));
        this.values = yamlMapper.convertValue(context.getContext(), SpELRouterInterceptorValues.class);
    }

    @Override
    public String getDescription() {
        return "Evaluates SpEL expressions to dynamically select target modules.";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initialise() throws Exception {
        super.initialise();
        if (values.getRoutingRules() == null) return;

        DynamicSchemaLoader loader = ApplicationContextProvider.getApplicationContext().getBean(DynamicSchemaLoader.class);

        // Pre-initialize and cache target module instances
        for (SpELRoutingRule rule : values.getRoutingRules()) {
            String refModuleName = rule.getRefModuleName();
            if (toolModuleMap.containsKey(refModuleName)) continue;

            StepConfiguration stepConfig = FlowConfigUtils.getStepConfigByName(refModuleName);
            if (stepConfig == null) {
                log.warn("[{}] Routing Target '{}' not found in registry.", pluginName, refModuleName);
                continue;
            }

            PluginContext.PluginContextBuilder contextBuilder = PluginContext.builder()
                    .flowName(flowName)
                    .flowLabel(flowLabel)
                    .pluginName(stepConfig.getName())
                    .pluginLabel(stepConfig.getLabel())
                    .telegramName(stepConfig.getTelegram())
                    .context(stepConfig.getConfig())
                    .sharedScheduledPool(context.getSharedScheduledPool())
                    .sharedIoPool(context.getSharedIoPool())
                    .flowSyncBridge(syncBridge)
                    .meterRegistry(meterRegistry);

            // Pattern resolution priority: 1. Rule override / 2. Endpoint default
            ExchangePattern targetPattern = rule.getExchangePattern() != null ?
                    rule.getExchangePattern() : stepConfig.getExchangePattern();

            PluginContext ctx = (targetPattern == null) ?
                    contextBuilder.build() :
                    contextBuilder.exchangePattern(targetPattern).build();

            Class<GatewayInterceptor> filterClazz = (Class<GatewayInterceptor>) Class.forName(loader.get(stepConfig.getType()).getClassName());
            GatewayInterceptor filter = filterClazz.getConstructor(PluginContext.class).newInstance(ctx);
            filter.initialise();
            toolModuleMap.put(refModuleName, filter);
        }
    }

    @Override
    public CompletableFuture<Payload> executeAsync(Payload payload, Executor flowExecutor) {
        this.metricsAggregator.incrementPayloadReceivedCount();
        long start = System.currentTimeMillis();

        try {
            MDCUtils.restoreMdc(payload);
            GatewayInterceptor selectedFilter = null;

            // Evaluate conditions using PayloadExpressionEvaluator (Format parsing is handled internally)
            for (SpELRoutingRule rule : values.getRoutingRules()) {
                Boolean isMatched = PayloadExpressionEvaluator.evaluate(rule.getCondition(), payload, Boolean.class);

                if (Boolean.TRUE.equals(isMatched)) {
                    selectedFilter = toolModuleMap.get(rule.getRefModuleName());
                    if (selectedFilter != null) {
                        log.info("Routing rule matched: [{}] -> Target: [{}]", rule.getName(), selectedFilter.getPluginName());
                        break;
                    }
                }
            }

            // Fail-Fast: Return error if no rules are satisfied
            if (selectedFilter == null) {
                FlowException ex = new FlowException(FlowResultStatus.DYNAMIC_ROUTE_NOT_FOUND, "No routing rule matched for the incoming transaction.");
                handleMetrics(payload, ex, start);
                CompletableFuture<Payload> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(ex);
                return failedFuture;
            }

            // Delegation: Hand over control and the current flowExecutor to the selected filter
            return selectedFilter.executeAsync(payload, flowExecutor)
                    .whenComplete((result, ex) -> {
                        handleMetrics(result, ex, start); // Collect router metrics
                    });

        } catch (Exception e) {
            log.error("Routing evaluation failed: {}", e.getMessage(), e);
            FlowException ex = new FlowException(e, FlowResultStatus.SPEL_EVALUATION_ERROR);
            handleMetrics(payload, ex, start);

            CompletableFuture<Payload> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(ex);
            return failedFuture;
        }
    }
}
