package io.zefio.core.processor;

import io.zefio.core.payload.builder.config.CorrelationField;
import io.zefio.core.payload.builder.config.CorrelationIdType;
import io.zefio.core.payload.builder.config.FixedValues;
import io.zefio.core.payload.builder.config.Telegram;
import io.zefio.core.payload.util.TelegramFactory;
import okhttp3.*;
import org.apache.commons.io.IOUtils;
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
 * Switch-Orchestrator Integration Test Suite.
 * Objective: End-to-end validation of Validator -> Modifier -> Switch -> Local Upstream pipeline.
 * Tools: OkHttp3, Commons-IO (Test Scope)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SwitchOrchestrationTestCase {
    private static final Logger log = LoggerFactory.getLogger(SwitchOrchestrationTestCase.class);

    private final String TARGET_URL = "http://127.0.0.1:51003/";
    private final int MOCK_SERVER_PORT = 51101;
    private OkHttpClient okHttpClient;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize OkHttp client
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        // Register 96-byte Fixed layout for payload parsing
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
    @DisplayName("Scenario 1: [VIP Route] Validate payload modification (XYZ/RES) and routing")
    void testVipGoldRouteWithModification() throws Exception {
        runSwitchTest(() -> {
            String payload = buildPayload("REQ", "ABC", "VIP-9999-GOLD-TRX-ID-00000000001 ");

            TestResponse response = sendHttpRequest(payload);

            assertEquals(200, response.getCode());
            String resBody = response.getBody();
            log.info("[VIP OUTPUT]: {}", resBody);

            // The engine returns the final response as a serialized Map (JSON).
            assertTrue(resBody.contains("\"PREFIX\":\"RES\"") || resBody.contains("RES"), "PREFIX modification failed");
            assertTrue(resBody.contains("\"TARGET_VAL\":\"XYZ\"") || resBody.contains("XYZ"), "TARGET_VAL modification failed");
            assertTrue(resBody.contains("VIP-9999-GOLD-TRX"), "Transaction ID was lost");
        });
    }

    @Test
    @DisplayName("Scenario 2: [Mobile Route] Validate regex matching and JSON path routing")
    void testMobileRegexRouteWithModification() throws Exception {
        runSwitchTest(() -> {
            String payload = buildPayload("M01", "ABC", "STD-MOBILE-TRX-ID-0000000000001 ");

            TestResponse response = sendHttpRequest(payload);

            assertEquals(200, response.getCode());
            String resBody = response.getBody();
            log.info("[MOBILE OUTPUT]: {}", resBody);

            assertTrue(resBody.contains("\"PREFIX\":\"ERR\""), "PREFIX modification failed");
            assertTrue(resBody.contains("\"TARGET_VAL\":\"XYZ\""), "TARGET_VAL modification failed");
            assertTrue(resBody.startsWith("{"), "Failed to enter JSON routing path");
        });
    }

    @Test
    @DisplayName("Scenario 3: [Validator] Validate guardrail blocking on invalid target")
    void testValidatorBlock() throws Exception {
        runSwitchTest(() -> {
            String payload = buildPayload("REQ", "BAD", "BLOCK-TRX-ID-000000000000000001 ");

            TestResponse response = sendHttpRequest(payload);

            log.info("[BLOCK OUTPUT]: {}", response.getBody());
            assertTrue(response.getBody().contains("Guardrail Blocked"), "Guardrail block failed");
        });
    }

    // -------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------

    private String buildPayload(String prefix, String targetVal, String trxId) {
        // Fallback for String.repeat() in JDK 1.8
        char[] spaces = new char[58];
        java.util.Arrays.fill(spaces, ' ');
        String dummyHeader = new String(spaces);

        return String.format("%-3s%-3s%s%-32s",
                prefix.substring(0, Math.min(3, prefix.length())),
                targetVal.substring(0, Math.min(3, targetVal.length())),
                dummyHeader,
                trxId.substring(0, Math.min(32, trxId.length())));
    }

    private void runSwitchTest(TestTask task) throws Exception {
        com.sun.net.httpserver.HttpServer mockTargetServer =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(MOCK_SERVER_PORT), 0);

        mockTargetServer.createContext("/", ex -> {
            // Use Commons-IO for readAllBytes compatibility in JDK 1.8
            byte[] b = IOUtils.toByteArray(ex.getRequestBody());
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        mockTargetServer.setExecutor(null);
        mockTargetServer.start();

        try {
            assumeTrue(isServerUp(), "Zefio engine (51003) is not running.");
            task.run();
        } finally {
            mockTargetServer.stop(0);
        }
    }

    private boolean isServerUp() {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", 51003), 500);
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

    // Wrapper class to emulate Java 11 HttpResponse
    private static class TestResponse {
        private final int code;
        private final String body;
        public TestResponse(int code, String body) { this.code = code; this.body = body; }
        public int getCode() { return code; }
        public int statusCode() { return code; }
        public String body() { return body; }
        public String getBody() { return body; }
    }

    @FunctionalInterface interface TestTask { void run() throws Exception; }
}
