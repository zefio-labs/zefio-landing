package io.zefio.core.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.zefio.core.beans.ApplicationContextProvider;
import io.zefio.core.beans.FlowSettingsBean;
import io.zefio.core.schema.DslConfigurationLoader;
import io.zefio.core.config.flow.ErrorHandlerConfiguration;
import io.zefio.core.config.flow.FlowConfiguration;
import io.zefio.core.config.flow.FlowSettings;
import io.zefio.core.config.flow.StepConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for handling flow and step configurations.
 * Provides functions to find specific step definitions and merge profile templates.
 */
public class FlowConfigUtils {
    private static final Logger log = LoggerFactory.getLogger(FlowConfigUtils.class);
    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    static {
        mapper.findAndRegisterModules();
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Dynamically reads the main configuration path from JVM options (-Dspring.config.location).
     */
    public static String getMainConfigPath() {
        String path = System.getProperty("spring.config.location");
        if (StringUtils.isNotBlank(path)) {
            // Safely converts Spring Boot format (classpath:/) to Resource Resolver format (classpath:)
            return path.replace("classpath:/", "classpath:");
        }
        return "classpath:zefio.yml";
    }

    /**
     * Returns the StepConfiguration object based on the given name.
     * Used for dynamic filter creation in routers or other control modules.
     */
    public static StepConfiguration getStepConfigByName(String name) throws Exception {
        if (StringUtils.isBlank(name)) return null;

        try {
            FlowSettingsBean bean = ApplicationContextProvider.getApplicationContext().getBean(FlowSettingsBean.class);
            FlowSettings settings = bean.getSettings();
            StepConfiguration step = searchStepInSettings(settings, name);
            if (step != null) {
                // Apply profile settings dynamically when retrieving the configuration
                applyProfile(settings, step);
                return step;
            }
        } catch (BeansException | NullPointerException e) {
            log.debug("Spring Context not ready. Attempting DSL fallback load for name: {}", name);
            return findStepFromDslFallback(name);
        }
        return null;
    }

    /**
     * Dynamically merges Profile templates into the step configuration.
     */
    @SuppressWarnings("unchecked")
    private static void applyProfile(FlowSettings settings, StepConfiguration step) {
        if (step == null || StringUtils.isBlank(step.getProfile()) || settings.getProfiles() == null) {
            return;
        }

        Object rawProfile = settings.getProfiles().get(step.getProfile());
        if (!(rawProfile instanceof Map)) return;

        Map<String, Object> profileMap = (Map<String, Object>) rawProfile;
        if (step.getConfig() == null) {
            step.setConfig(new HashMap<>());
        }
        final Map<String, Object> finalStepConfig = step.getConfig();

        // 1. Merge the 'config' block
        if (profileMap.containsKey("config") && profileMap.get("config") instanceof Map) {
            Map<String, Object> profileConfig = (Map<String, Object>) profileMap.get("config");
            profileConfig.forEach(finalStepConfig::putIfAbsent);
        }

        // 2. Merge flat structure properties
        profileMap.forEach((k, v) -> {
            // Prevent pollution of core configuration fields
            if (!Arrays.asList("config", "retry", "exchangePattern", "type", "on-error", "fallback-steps").contains(k)) {
                finalStepConfig.putIfAbsent(k, v);
            }
        });

        log.debug("🎯 Applied profile [{}] to step [{}]", step.getProfile(), step.getName());
    }

    /**
     * Internal logic to search for a step definition within FlowSettings.
     */
    private static StepConfiguration searchStepInSettings(FlowSettings settings, String name) {
        // 0. Search Global Endpoints (High priority)
        if (settings.getEndpoints() != null) {
            for (StepConfiguration endpoint : settings.getEndpoints()) {
                if (name.equals(endpoint.getName())) {
                    return endpoint;
                }
            }
        }

        // 1. Search Ingress and Pipeline Steps
        if (settings.getFlows() != null) {
            for (FlowConfiguration flow : settings.getFlows()) {
                if (flow.getIngress() != null && name.equals(flow.getIngress().getName())) {
                    return convertToStepConfig(flow.getIngress().getName(), flow.getIngress().getType(), flow.getIngress().getConfig());
                }

                // Search main pipeline
                StepConfiguration stepConfig = searchInStepsRecursive(flow.getSteps(), name);
                if (stepConfig != null) return stepConfig;

                // Search local error handling pipelines
                if (flow.getOnError() != null) {
                    for (ErrorHandlerConfiguration err : flow.getOnError()) {
                        StepConfiguration foundInErr = searchInStepsRecursive(err.getSteps(), name);
                        if (foundInErr != null) return foundInErr;
                    }
                }
            }
        }

        // 2. Search Global Errors
        if (settings.getGlobalErrors() != null) {
            for (Map.Entry<String, StepConfiguration> entry : settings.getGlobalErrors().entrySet()) {
                String globalKey = entry.getKey();
                StepConfiguration globalStep = entry.getValue();

                if (name.equals(globalKey)) return globalStep;
                if (name.equals(globalStep.getName())) return globalStep;

                // Search recursively within global composite steps
                StepConfiguration child = searchInStepsRecursive(java.util.Collections.singletonList(globalStep), name);
                if (child != null) return child;
            }
        }
        return null;
    }

    /**
     * Recursively searches for a step within nested step hierarchies.
     */
    private static StepConfiguration searchInStepsRecursive(List<StepConfiguration> steps, String name) {
        if (steps == null || steps.isEmpty()) return null;
        for (StepConfiguration step : steps) {
            if (name.equals(step.getName())) return step;

            // Search in primary steps
            StepConfiguration child = searchInStepsRecursive(step.getSteps(), name);
            if (child != null) return child;

            // Search in fallback steps
            StepConfiguration fallbackChild = searchInStepsRecursive(step.getFallbackSteps(), name);
            if (fallbackChild != null) return fallbackChild;
        }
        return null;
    }

    /**
     * Fallback logic to find steps via DSL Configuration Loader if Spring Context is unavailable.
     */
    private static StepConfiguration findStepFromDslFallback(String name) {
        try {
            DslConfigurationLoader loader = new DslConfigurationLoader();
            Map<String, Object> mergedYamlMap = loader.loadAndMerge(getMainConfigPath());
            FlowSettings tempSettings = mapper.convertValue(mergedYamlMap, FlowSettings.class);
            StepConfiguration step = searchStepInSettings(tempSettings, name);
            if (step != null) {
                applyProfile(tempSettings, step);
            }
            return step;
        } catch (Exception ex) {
            log.error("Failed to load YAML via DslConfigurationLoader fallback.", ex);
            return null;
        }
    }

    private static StepConfiguration convertToStepConfig(String name, String type, Map<String, Object> config) {
        StepConfiguration step = new StepConfiguration();
        step.setName(name);
        step.setType(type);
        step.setConfig(config != null ? config : new HashMap<>());
        return step;
    }
}
