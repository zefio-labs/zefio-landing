package io.zefio.testsupport.filter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.zefio.core.config.flow.StepConfiguration;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility for loading filter configurations from YAML resources during testing.
 * Maps resource definitions to StepConfiguration models for plugin context initialization.
 */
public class FilterConfigLoader {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public FilterConfigLoader() {
        yamlMapper.findAndRegisterModules();
        yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        yamlMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
        yamlMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
    }

    /**
     * Loads a list of filter configurations from the specified resource path.
     *
     * @param resourcePath The path to the YAML configuration file.
     * @return A map of filter names to their respective StepConfiguration.
     */
    public Map<String, StepConfiguration> load(String resourcePath) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }

            List<StepConfiguration> filterList = yamlMapper.readValue(is,
                    yamlMapper.getTypeFactory().constructCollectionType(List.class, StepConfiguration.class));

            return filterList.stream()
                    .collect(Collectors.toMap(StepConfiguration::getName, t -> t));
        }
    }
}
