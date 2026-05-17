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
 * Intelligent Loopback Integration Test Suite
 * Targets the specific scenario of Ingress to Upstream self-invocation
 * (port 51008 to 52008) to validate SpEL-based correlation and fallback logic.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class HttpSpelLoopbackIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(HttpSpelLoopbackIntegrationTest.class);

    private final String TARGET_URL = "http://127.0.0.1:51008/";
    private OkHttpClient okHttpClient;

    @BeforeEach
    void setUp() throws Exception {
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

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
    @DisplayName("Scenario 1: Normal Transaction (TARGET_VAL='ABC') -> Verify 'ACK' from Upstream loopback")
    void testSpelUpstreamSuccess() throws Exception {
        runInSandbox(() -> {
            String trxId = "MADNESS-OK-000000000000000000001";
            String payload = buildPayload("REQ", "ABC", trxId);

            log.info("[Scenario 1] Initiating standard SpEL assembly test...");
            TestResponse response = sendHttpRequest(payload);

            assertEquals(200, response.getCode());
            String res = response.getBody();
            log.info("Normal response body: [{}]", res);

            assertTrue(res.contains("ACK"), "Expected 'ACK' from the receiving engine is missing.");
            assertTrue(res.contains(trxId));
        });
    }

    @Test
    @DisplayName("Scenario 2: Induce Error (TARGET_VAL='ERR') -> Upstream returns 500 -> Verify Conditional Fallback (CONTINUE) on Ingress side")
    void testSpelConditionalFallback() throws Exception {
        runInSandbox(() -> {
            String trxId = "MADNESS-ERR-000000000000000000002";
            String payload = buildPayload("REQ", "ERR", trxId);

            log.info("[Scenario 2] Initiating error-induced conditional fallback test...");
            TestResponse response = sendHttpRequest(payload);

            assertEquals(200, response.getCode(), "Expected 200 OK due to successful conditional fallback restoration.");

            String res = response.getBody();
            log.info("Fallback response body: [{}]", res);

            assertTrue(res.contains("RES"), "PREFIX restoration failed.");
            assertTrue(res.contains("ERR"), "TARGET_VAL was not marked as 'ERR'.");
            assertTrue(res.contains("Resolved via Conditional Fallback"), "Fallback message injection failed.");
        });
    }

    // -------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------

    private String buildPayload(String prefix, String targetVal, String trxId) {
        return String.format("%-3s%-3s%-58s%-32s", prefix, targetVal, " ", trxId);
    }

    private void runInSandbox(TestTask task) throws Exception {
        assumeTrue(isServerUp(51008), "Ingress engine (51008) is not running.");
        assumeTrue(isServerUp(52008), "Upstream engine (52008) is not running.");
        task.run();
    }

    private boolean isServerUp(int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 300);
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
            log.error("HTTP transmission error", e);
            return new TestResponse(500, e.getMessage());
        }
    }

    private static class TestResponse {
        private final int code;
        private final String body;
        public TestResponse(int code, String body) { this.code = code; this.body = body; }
        public int getCode() { return code; }
        public String getBody() { return body; }
    }

    @FunctionalInterface interface TestTask { void run() throws Exception; }
}
