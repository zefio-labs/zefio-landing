package io.zefio.core.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Orchestration pipeline for automated documentation generation.
 * Introspects internal DTO metadata to generate Markdown-based component references,
 * including synchronized YAML configuration samples.
 */
public class DocPipeline {

    private static final String API_BASE_URL = "http://localhost:52001/api/config";
    private static final String MANIFEST_RESOURCE_NAME = "doc-manifest.yml";
    private static final String DEFAULT_PARENT_DIR = "./docs";

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper;

    public DocPipeline() {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        this.yamlMapper = new ObjectMapper(yamlFactory);
    }

    /**
     * Executes the full documentation assembly pipeline.
     * Performs directory cleanup, metadata fetching, and file generation.
     */
    public void executePipeline() {
        System.out.println("[DocPipeline] Starting automated documentation assembly...");
        long startTime = System.currentTimeMillis();

        try {
            JsonNode manifest = loadManifest();
            String baseDir = manifest.path("base").path("base_dir").asText("03-component-reference");
            Path outputDirPath = Paths.get(DEFAULT_PARENT_DIR, baseDir);

            // Clean build environment
            if (Files.exists(outputDirPath)) {
                System.out.println("[DocPipeline] Cleaning existing directory: " + outputDirPath);
                Files.walk(outputDirPath)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }

            Files.createDirectories(outputDirPath);
            System.out.println("[DocPipeline] Output directory prepared: " + outputDirPath.toAbsolutePath());

            Map<String, String> moduleTypeMap = fetchAllModuleTypes();

            generateIndexMarkdown(manifest, outputDirPath);
            generateIndividualComponentFiles(manifest, moduleTypeMap, outputDirPath);

            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("[DocPipeline] Documentation generation completed in " + elapsed + "ms.");

        } catch (Exception e) {
            System.err.println("[DocPipeline] Pipeline execution failed.");
            e.printStackTrace();
        }
    }

    private JsonNode loadManifest() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(MANIFEST_RESOURCE_NAME)) {
            if (is == null) throw new RuntimeException("Manifest resource not found: " + MANIFEST_RESOURCE_NAME);
            return yamlMapper.readTree(is);
        }
    }

    private void generateIndexMarkdown(JsonNode manifest, Path outputDirPath) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(manifest.path("title").asText()).append("\n\n");
        sb.append(manifest.path("description").asText()).append("\n\n---\n\n");

        for (JsonNode category : manifest.path("categories")) {
            sb.append("## ").append(category.path("title").asText()).append("\n\n");
            if (!category.has("headers") || category.path("headers").isEmpty()) continue;

            JsonNode headers = category.path("headers");
            sb.append("|");
            for (JsonNode header : headers) sb.append(" ").append(header.asText()).append(" |");
            sb.append("\n|");
            for (int i = 0; i < headers.size(); i++) sb.append(" :--- |");
            sb.append("\n");

            for (JsonNode row : category.path("rows")) {
                sb.append("|");
                for (JsonNode column : row.path("columns")) {
                    sb.append(" ").append(column.asText()).append(" |");
                }
                sb.append("\n");
            }
            sb.append("\n---\n\n");
        }
        sb.append(manifest.path("footer").asText()).append("\n");

        Files.write(outputDirPath.resolve("index.md"), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void generateIndividualComponentFiles(JsonNode manifest, Map<String, String> typeMap, Path outputDirPath) throws Exception {
        Map<String, StringBuilder> fileBuffers = new LinkedHashMap<>();

        for (JsonNode category : manifest.path("categories")) {
            for (JsonNode row : category.path("rows")) {
                String targetFile = row.path("target_file").asText();

                StringBuilder fileContent = fileBuffers.computeIfAbsent(targetFile, k -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("# ").append(k.replace(".md", "").toUpperCase()).append(" Component Details\n\n");
                    return sb;
                });

                for (JsonNode moduleNode : row.path("modules")) {
                    String moduleName = moduleNode.asText();
                    String moduleType = typeMap.getOrDefault(moduleName, "filter");
                    System.out.println("[DocPipeline] Assembling component reference: " + moduleName);

                    String dtoResponse = fetchUrl(API_BASE_URL + "/dto/" + moduleName);
                    if (dtoResponse == null || dtoResponse.trim().isEmpty()) continue;
                    JsonNode dtoNode = jsonMapper.readTree(dtoResponse);

                    fileContent.append("## ").append(moduleName).append("\n\n");
                    fileContent.append("### YAML Configuration Sample\n```yaml\n");
                    fileContent.append(generateYamlSample(moduleName, moduleType, dtoNode));
                    fileContent.append("```\n\n");

                    fileContent.append("### Configuration Parameters\n\n");
                    fileContent.append("| Parameter | Required | Default | Description |\n| :--- | :---: | :--- | :--- |\n");
                    generateTableRows("", dtoNode, fileContent);
                    fileContent.append("\n---\n\n");
                }
            }
        }

        for (Map.Entry<String, StringBuilder> entry : fileBuffers.entrySet()) {
            Files.write(outputDirPath.resolve(entry.getKey()), entry.getValue().toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private String generateYamlSample(String moduleName, String moduleType, JsonNode fieldsNode) throws Exception {
        Map<String, Object> configMap = buildConfigMap(fieldsNode);
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> item = new LinkedHashMap<>();

        if ("ingress".equalsIgnoreCase(moduleType)) {
            item.put("name", "sample_" + moduleName.toLowerCase());
            item.put("type", moduleName);
            item.put("label", moduleName + " Ingress Adapter");
            item.put("config", configMap);
            root.put("ingress", Collections.singletonList(item));
        } else if ("error".equalsIgnoreCase(moduleType)) {
            item.put("type", moduleName);
            item.put("label", "Common Error Handler");
            item.put("action", "ABORT");
            item.put("config", configMap);
            Map<String, Object> errorsMap = new LinkedHashMap<>();
            errorsMap.put("sample_" + moduleName.toLowerCase(), item);
            root.put("global-errors", errorsMap);
        } else {
            item.put("name", "sample_" + moduleName.toLowerCase());
            item.put("type", moduleName);
            item.put("label", moduleName + " Pipeline Step");
            item.put("config", configMap);
            root.put("steps", Collections.singletonList(item));
        }
        return yamlMapper.writeValueAsString(root);
    }

    private Map<String, Object> buildConfigMap(JsonNode fieldsNode) {
        Map<String, Object> map = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = fieldsNode.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String key = entry.getKey();
            JsonNode val = entry.getValue();

            if (val.has("_fields") && val.path("_fields").size() > 0) {
                Map<String, Object> nestedMap = buildConfigMap(val.path("_fields"));
                String schemaType = val.path("_type").asText("").toLowerCase();
                boolean isArray = schemaType.contains("array") || schemaType.contains("list") || val.has("_implementation");

                if (isArray) {
                    map.put(key, Collections.singletonList(nestedMap));
                } else {
                    map.put(key, nestedMap);
                }
            } else {
                Object sampleValue = extractSampleValue(val);
                if (sampleValue != null) map.put(key, sampleValue);
            }
        }
        return map;
    }

    private Object extractSampleValue(JsonNode valNode) {
        String val = null;
        if (valNode.hasNonNull("_example") && !valNode.path("_example").asText().isEmpty()) val = valNode.path("_example").asText();
        else if (valNode.hasNonNull("_default") && !valNode.path("_default").asText().isEmpty()) val = valNode.path("_default").asText();

        if (val == null || val.equals("없음") || val.equals("null")) return null;
        if (val.equalsIgnoreCase("true")) return true;
        if (val.equalsIgnoreCase("false")) return false;
        if (val.matches("-?\\d+")) { try { return Long.parseLong(val); } catch (Exception e) {} }
        return val;
    }

    private void generateTableRows(String prefix, JsonNode fieldsNode, StringBuilder sb) {
        Iterator<Map.Entry<String, JsonNode>> it = fieldsNode.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String key = entry.getKey();
            JsonNode val = entry.getValue();

            String fieldName = prefix.isEmpty() ? key : prefix + "." + key;
            String desc = val.path("_description").asText("-").replace("\n", "<br>");
            String req = val.path("_required").asText("Optional");
            String defVal = val.hasNonNull("_default") ? val.path("_default").asText() :
                    val.hasNonNull("_example") ? val.path("_example").asText("N/A") : "N/A";

            sb.append(String.format("| %s | %s | %s | %s |\n",
                    prefix.isEmpty() ? "**`" + fieldName + "`**" : "`" + fieldName + "`", req, defVal, desc));

            if (val.has("_fields")) generateTableRows(fieldName, val.path("_fields"), sb);
        }
    }

    private String fetchUrl(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            if (conn.getResponseCode() != 200) return null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder r = new StringBuilder();
                String l;
                while ((l = br.readLine()) != null) r.append(l);
                return r.toString();
            }
        } catch (Exception e) { return null; }
        finally { if (conn != null) conn.disconnect(); }
    }

    private Map<String, String> fetchAllModuleTypes() throws Exception {
        Map<String, String> typeMap = new HashMap<>();
        String response = fetchUrl(API_BASE_URL);
        if (response != null) {
            JsonNode modules = jsonMapper.readTree(response);
            for (JsonNode mod : modules) typeMap.put(mod.path("name").asText(), mod.path("type").asText());
        }
        return typeMap;
    }

    public static void main(String[] args) {
        new DocPipeline().executePipeline();
    }
}
