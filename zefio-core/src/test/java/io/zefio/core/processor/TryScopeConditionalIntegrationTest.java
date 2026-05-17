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
 * Atomic Test: SpEL Conditional Fallback Test
 * Targets the Ingress port (51007) to validate conditional fallback logic
 * using SpEL after upstream retries.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TryScopeConditionalIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(TryScopeConditionalIntegrationTest.class);

    private final String TARGET_URL = "http://127.0.0.1:51007/";
    private OkHttpClient okHttpClient;

    @BeforeEach
    void setUp() throws Exception {
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
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
    @DisplayName("Verify intelligent fallback (CND) response via SpEL error code matching after 3 retries (600ms+)")
    void testConditionalFallbackSuccess() throws Exception {
        runInSandbox(() -> {
            String trxId = "COND-TEST-000000000000000000001";
            String payload = buildPayload("REQ", "ABC", trxId);

            log.info("Initiating conditional fallback test...");
            long startTime = System.currentTimeMillis();

            TestResponse response = sendHttpRequest(payload);
            long elapsed = System.currentTimeMillis() - startTime;

            log.info("Total processing time: {}ms", elapsed);

            assertEquals(200, response.statusCode(), "Expected 200 OK due to successful conditional fallback");
            String res = response.body();
            log.info("Final response body: [{}]", res);

            assertTrue(res.contains("RES"), "PREFIX restoration failed");
            assertTrue(res.contains("CND"), "Marking 'CND' via SpEL condition match failed");
            assertTrue(res.contains(trxId), "Transaction ID was lost");
        });
    }

    // -------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------

    private String buildPayload(String prefix, String targetVal, String trxId) {
        return String.format("%-3s%-3s%-58s%-32s",
                prefix.substring(0, Math.min(3, prefix.length())),
                targetVal.substring(0, Math.min(3, targetVal.length())),
                " ",
                trxId.substring(0, Math.min(32, trxId.length())));
    }

    private void runInSandbox(TestTask task) throws Exception {
        assumeTrue(isServerUp(), "Zefio engine (51007) is not running. Ignoring test.");
        task.run();
    }

    private boolean isServerUp() {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", 51007), 300);
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

    private static class TestResponse {
        private final int code;
        private final String body;
        public TestResponse(int code, String body) { this.code = code; this.body = body; }
        public int statusCode() { return code; }
        public String body() { return body; }
    }

    @FunctionalInterface interface TestTask { void run() throws Exception; }
}
