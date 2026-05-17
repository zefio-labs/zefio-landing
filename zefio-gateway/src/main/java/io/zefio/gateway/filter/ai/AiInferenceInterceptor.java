package io.zefio.gateway.filter.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zefio.core.BaseComputeInterceptor;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.spel.PayloadExpressionEvaluator;
import io.zefio.gateway.filter.ai.dto.AiInferenceValues;
import okhttp3.*;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class AiInferenceInterceptor extends BaseComputeInterceptor {

    private final AiInferenceValues values;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final ExpressionParser parser = new SpelExpressionParser();

    // 🚀 Uses OkHttpClient, ensuring compatibility across JDK 1.8 to JDK 21
    private final OkHttpClient httpClient;

    public AiInferenceInterceptor(PluginContext context) {
        super(context);
        this.values = yamlMapper.convertValue(context.getContext(), AiInferenceValues.class);

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                // Set a generous read timeout to accommodate potentially slow LLM responses
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getDescription() {
        return "[Non-Blocking I/O] Integrates dynamic SpEL-based prompts and real-time LLM inference.";
    }

    @Override
    public boolean isBlockingType() {
        // Returns true to signal to the router that this filter performs external I/O,
        // allowing it to allocate appropriate thread pool resources.
        return true;
    }

    /**
     * 🚀 [Core logic] Overrides executeAsync directly to ensure Zefio worker threads are not blocked.
     */
    @Override
    public CompletableFuture<Payload> executeAsync(Payload payload, Executor flowExecutor) {
        CompletableFuture<Payload> future = new CompletableFuture<>();
        long start = System.currentTimeMillis();

        try {
            // 1. Assemble the prompt (Compute task - extremely fast)
            String injectedPrompt = PayloadExpressionEvaluator.evaluate(
                    values.getPromptTemplate(), payload, String.class
            );

            // 2. Construct the HTTP Request for the LLM API
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", values.getModel());
            requestBody.put("temperature", values.getTemperature());
            requestBody.put("max_tokens", values.getMaxTokens());

            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", injectedPrompt);
            requestBody.put("messages", new Object[]{message});

            String jsonPayload = mapper.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                    .url(values.getEndpoint())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + values.getApiKey())
                    .post(RequestBody.create(jsonPayload, MediaType.parse("application/json")))
                    .build();

            // 3. 🚀 [True Asynchronous I/O] The Zefio thread returns immediately and is freed!
            // Network waiting is handled entirely by OkHttp's internal background threads.
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    // Hand off error processing back to the Zefio thread pool (flowExecutor)
                    flowExecutor.execute(() -> {
                        log.error("[{}] LLM Inference Network Failed: {}", getPluginName(), e.getMessage());
                        future.completeExceptionally(new FlowException(e, FlowResultStatus.EXTERNAL_API_ERROR));
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    // 🚀 Upon receiving a response, submit post-processing tasks to the Zefio thread pool.
                    flowExecutor.execute(() -> {
                        try (ResponseBody body = response.body()) {
                            // Record the time spent waiting for the external API
                            payload.addRemoteTime(System.currentTimeMillis() - start);

                            if (!response.isSuccessful()) {
                                throw new RuntimeException("LLM API Error: " + (body != null ? body.string() : "Unknown"));
                            }

                            // 4. Parse the LLM result and write it back to the payload
                            JsonNode rootNode = mapper.readTree(body.string());
                            String aiResult = rootNode.path("choices").get(0).path("message").path("content").asText();

                            if (values.getTargetKey() != null) {
                                injectResultToPayload(payload, values.getTargetKey(), aiResult);
                            }

                            // Safely pass the modified payload to the next stage in the pipeline
                            future.complete(payload);

                        } catch (Exception e) {
                            log.error("[{}] LLM Result Parsing Failed: {}", getPluginName(), e.getMessage());
                            future.completeExceptionally(new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR));
                        }
                    });
                }
            });

        } catch (Exception e) {
            future.completeExceptionally(new FlowException(e, FlowResultStatus.PIPELINE_EXECUTION_ERROR));
        }

        // Return the pending Future to the Zefio pipeline immediately.
        return future;
    }

    /**
     * Since executeAsync is overridden directly, this method should not be invoked.
     */
    @Override
    public Payload process(Payload payload) throws FlowException {
        throw new UnsupportedOperationException("This filter uses true asynchronous I/O and overrides executeAsync directly.");
    }

    /**
     * Helper method to safely write the AI inference result into the specified target location within the Payload.
     */
    private void injectResultToPayload(Payload payload, String targetExpr, String aiResult) {
        Map<String, Object> proxy = new HashMap<>();
        proxy.put("payload", payload);

        StandardEvaluationContext context = new StandardEvaluationContext(proxy);
        Expression expression = parser.parseExpression(targetExpr);

        expression.setValue(context, aiResult);
        payload.setBodyModified(true);
    }
}
