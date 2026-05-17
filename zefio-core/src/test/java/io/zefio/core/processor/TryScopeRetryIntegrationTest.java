package io.zefio.core.processor;

import io.zefio.core.payload.builder.config.CorrelationField;
import io.zefio.core.payload.builder.config.CorrelationIdType;
import io.zefio.core.payload.builder.config.FixedValues;
import io.zefio.core.payload.builder.config.Telegram;
import io.zefio.core.payload.util.TelegramFactory;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Atomic Test: TRY_SCOPE Retry Mechanism Test (JDK 1.8 Compatible)
 * - Target: flow-tryscope-retry.yaml
 * - Objective: Prove that 3 retries with a 500ms delay actually occur using 'response time' and 'final result'.
 * - Tool: OkHttp3 (Test Scope)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TryScopeRetryIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(TryScopeRetryIntegrationTest.class);

    private final String TARGET_URL = "http://127.0.0.1:51005/";
    private OkHttpClient okHttpClient;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize JDK 1.8 compatible OkHttpClient
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS) // Consider retry delays (500ms * 3) + processing time
                .build();

        // 96-byte layout setup (server specification synchronization)
        FixedValues values = new FixedValues();
        values.setEncodingIgnore(true);
        CorrelationField correlation = new CorrelationField(CorrelationIdType.SpEL);
        correlation.setExpression("#{body['GLOBAL_TRX_ID']}");
        values.setCorrelation(correlation);
        values.setLayout(Arrays.asList(
                new FixedValues.FixedField("PREFIX", 3, null, FixedValues.FieldType.STRING, FixedValues.Align.L, ' ', true),
                new FixedValues.FixedField("TARGET_VAL", 3, null, FixedValues.FieldType.STRING, FixedValues.Align.L, ' ', true),
                new FixedValues.FixedField("DUMMY_HEADER", 58, null, FixedValues.FieldType.STRING, FixedValues.Align.L, ' ', true),
                new FixedValues.FixedField("GLOBAL_TRX_ID", 32, null, FixedValues.FieldType.STRING, FixedValues.Align.L, ' ', true)
        ));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> valuesMap = mapper.convertValue(values, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        TelegramFactory.register("fixed-http-req", Telegram.Type.Fixed, valuesMap);
    }

    @Test
    @DisplayName("When executing 3 RETRY attempts (500ms each), total response time should exceed 1.5s and receive final fallback response")
    void testRetryDelayAndFallback() throws Exception {
        runInSandbox(() -> {
            String trxId = "RETRY-TEST-0000000000000000001";
            String payload = buildPayload("REQ", "ABC", trxId);

            log.info("Starting retry test (Expected duration: 1.5s+)...");
            long startTime = System.currentTimeMillis();

            // [When] Fire HTTP request
            TestResponse response = sendHttpRequest(payload);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Total elapsed time: {}ms", elapsed);

            // [Then] 1. Status code validation
            assertEquals(200, response.statusCode());

            // 2. Retry delay validation (500ms * 3 times = should take at least 1500ms)
            assertTrue(elapsed >= 1500, "Retry delay did not occur properly. (Actual elapsed: " + elapsed + "ms)");

            // 3. Data validation (check final fallback marking)
            String res = response.body();
            assertTrue(res.contains("RES"), "PREFIX restoration failed");
            assertTrue(res.contains("RTY"), "Fallback marking (RTY) missing after retries");
            assertTrue(res.contains(trxId), "TrxID lost");
        });
    }

    // -------------------------------------------------------------
    // Helper Methods (JDK 1.8 Compatible)
    // -------------------------------------------------------------

    private String buildPayload(String prefix, String targetVal, String trxId) {
        return String.format("%-3s%-3s%-58s%-32s", prefix, targetVal, " ", trxId);
    }

    private void runInSandbox(TestTask task) throws Exception {
        assumeTrue(isServerUp(), "Engine (51005) is not up. Ignoring test.");
        task.run();
    }

    private boolean isServerUp() {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", 51005), 300);
            return true;
        } catch (Exception e) { return false; }
    }

    private TestResponse sendHttpRequest(String payload) throws Exception {
        RequestBody body = RequestBody.create(payload, MediaType.parse("text/plain; charset=utf-8"));
        Request request = new Request.Builder().url(TARGET_URL).post(body).build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            String bodyString = (response.body() != null) ? response.body().string() : "";
            return new TestResponse(response.code(), bodyString);
        } catch (IOException e) {
            return new TestResponse(500, e.getMessage());
        }
    }

    // Wrapper class for Java 11 HttpResponse compatibility
    private static class TestResponse {
        private final int code;
        private final String body;
        public TestResponse(int code, String body) { this.code = code; this.body = body; }
        public int statusCode() { return code; }
        public String body() { return body; }
    }

    @FunctionalInterface interface TestTask { void run() throws Exception; }
}
