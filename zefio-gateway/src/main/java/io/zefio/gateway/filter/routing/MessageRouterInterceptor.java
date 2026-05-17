package io.zefio.gateway.filter.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
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
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.FixedValues;
import io.zefio.core.payload.builder.config.JsonValues;
import io.zefio.core.payload.builder.config.Telegram;
import io.zefio.core.payload.builder.config.XmlValues;
import io.zefio.core.payload.util.BytesUtils;
import io.zefio.core.payload.util.TelegramFactory;
import io.zefio.gateway.filter.routing.dto.MessageRouterInterceptorValues;
import io.zefio.gateway.filter.routing.dto.MessageRoutingRule;
import io.zefio.core.telemetry.module.ModuleMetricsAggregator;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Unified Content-Based Router (CBR) capable of multi-depth data extraction.
 * Supports Fixed-length, JSON (JsonPath), and XML (XPath) formats to determine
 * the downstream execution path.
 */
public class MessageRouterInterceptor extends BaseGatewayPlugin implements GatewayInterceptor {
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper xmlMapper = new XmlMapper();
    private final MessageRouterInterceptorValues values;

    /** Map to cache initialized target module instances for performance. */
    private final Map<String, GatewayInterceptor> toolModuleMap = new HashMap<>();

    public MessageRouterInterceptor(PluginContext context) {
        super(context, new ModuleMetricsAggregator(PluginType.interceptor, context.getFlowName() + "-" + context.getPluginName()));
        this.values = yamlMapper.convertValue(context.getContext(), MessageRouterInterceptorValues.class);
    }

    @Override
    public String getDescription() {
        return "Unified router that selects filters based on configured multi-depth routing rules.";
    }

    @Override
    public void initialise() throws Exception {
        super.initialise();

        if (values.getRoutingRules() == null) return;

        DynamicSchemaLoader loader = ApplicationContextProvider.getApplicationContext().getBean(DynamicSchemaLoader.class);

        for (MessageRoutingRule rule : values.getRoutingRules()) {
            String refModuleName = rule.getRefModuleName();

            // Prevent redundant initialization if multiple rules reference the same filter
            if (toolModuleMap.containsKey(refModuleName)) continue;

            StepConfiguration stepConfig = FlowConfigUtils.getStepConfigByName(refModuleName);

            if (stepConfig == null) {
                log.warn("[{}] Routing Target '{}' not found in configuration.", pluginName, refModuleName);
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

            PluginContext ctx = rule.getExchangePattern() == null ?
                    contextBuilder.build() :
                    contextBuilder.exchangePattern(rule.getExchangePattern()).build();

            @SuppressWarnings("unchecked")
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

            Charset encoding = payload.getCurrentEncoding();
            byte[] body = payload.getBody();

            // Retrieve Telegram metadata from the factory based on the payload label
            String telegramName = payload.getTelegramName();
            if (telegramName == null) {
                throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "Routing failed: Telegram name is missing.");
            }

            PayloadBuilder currentBuilder = TelegramFactory.getBuilder(telegramName);
            if (currentBuilder == null || currentBuilder.getTelegram() == null) {
                throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "Routing failed: Telegram metadata not found for " + telegramName);
            }

            Telegram.Type telegramType = currentBuilder.getTelegram().getType();
            Object telegramValues = currentBuilder.getTelegram().getValues();

            GatewayInterceptor selectedFilter = null;

            // Evaluate rules sequentially (First-Hit-Win)
            for (MessageRoutingRule rule : values.getRoutingRules()) {
                String extractedValue = null;

                // 1. Fixed Format: Offset-based extraction
                if (telegramType == Telegram.Type.Fixed && telegramValues instanceof FixedValues && rule.getOffset() != null) {
                    int requiredLength = rule.getOffset() + rule.getLength();
                    if (body.length >= requiredLength) {
                        extractedValue = new String(BytesUtils.bytesOffsetCopy(body, rule.getOffset(), rule.getLength()), encoding).trim();
                    }
                }
                // 2. JSON Format: JsonPath support for complex navigation
                else if (telegramType == Telegram.Type.JSON && telegramValues instanceof JsonValues) {
                    if (StringUtils.isNotBlank(rule.getPath())) {
                        try {
                            String jsonStr = new String(body, encoding);
                            Object result = JsonPath.read(jsonStr, rule.getPath());
                            if (result != null) extractedValue = String.valueOf(result);
                        } catch (PathNotFoundException e) {
                            // Suppress error and proceed to the next rule if path is missing
                        }
                    } else if (StringUtils.isNotBlank(rule.getKey())) {
                        JsonNode root = jsonMapper.readTree(body);
                        JsonNode valNode = root.path(rule.getKey());
                        if (!valNode.isMissingNode()) extractedValue = valNode.asText();
                    }
                }
                // 3. XML Format: XPath support for attribute and deep-node selection
                else if (telegramType == Telegram.Type.XML && telegramValues instanceof XmlValues) {
                    if (StringUtils.isNotBlank(rule.getPath())) {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(true);
                        DocumentBuilder docBuilder = factory.newDocumentBuilder();
                        Document doc = docBuilder.parse(new ByteArrayInputStream(body));
                        XPath xPath = XPathFactory.newInstance().newXPath();
                        extractedValue = (String) xPath.compile(rule.getPath()).evaluate(doc, XPathConstants.STRING);
                        if (extractedValue != null) extractedValue = extractedValue.trim();
                        if (StringUtils.isEmpty(extractedValue)) extractedValue = null;
                    } else if (StringUtils.isNotBlank(rule.getKey())) {
                        JsonNode root = xmlMapper.readTree(body);
                        JsonNode valNode = root.path(rule.getKey());
                        if (!valNode.isMissingNode()) extractedValue = valNode.asText();
                    }
                }

                if (extractedValue != null && extractedValue.equals(rule.getMatchValue())) {
                    selectedFilter = toolModuleMap.get(rule.getRefModuleName());
                    if (selectedFilter != null) {
                        log.info("Routing matched: [{}] (Value: {}, Target Filter: {})", rule.getName(), extractedValue, selectedFilter.getPluginName());
                        break;
                    }
                }
            }

            if (selectedFilter == null) {
                FlowException ex = new FlowException(FlowResultStatus.BAD_REQUEST, "No routing rule matched for the incoming packet.");
                handleMetrics(payload, ex, start);

                CompletableFuture<Payload> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(ex);
                return failedFuture;
            }

            // Delegation of Control: Pass the execution and the current flowExecutor to the target filter.
            // If the target filter performs I/O, it will manage the transition to the Shared-IO pool.
            return selectedFilter.executeAsync(payload, flowExecutor)
                    .whenComplete((result, ex) -> {
                        // Aggregate routing metrics after target execution completes
                        handleMetrics(result, ex, start);
                    });

        } catch (Exception e) {
            log.error("Routing process failed: {}", e.getMessage());
            // Map formatting and mapping errors to BAD_REQUEST to prevent unnecessary infrastructure alerts
            FlowException ex = new FlowException(e, FlowResultStatus.BAD_REQUEST);
            handleMetrics(payload, ex, start);

            CompletableFuture<Payload> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(ex);
            return failedFuture;
        }
    }
}
