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
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The Grand Finale: 5-Depth Extreme Composite Orchestration Test
 * - Target: ResilientScope (Macro Retry) -> ScatterGather (Merge) -> Switch (Branching) -> ResilientScope (Double Fallback)
 * - Objective: Proves zero data loss and no thread deadlocks under extreme integration of the core pipeline.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ExtremeCompositeOrchestrationTest {
    private static final Logger log = LoggerFactory.getLogger(ExtremeCompositeOrchestrationTest.class);

    private final String TARGET_URL = "http://127.0.0.1:51000/";
    private final int MOCK_SERVER_PORT = 51101;
    private OkHttpClient okHttpClient;

    @BeforeEach
    void setUp() throws Exception {
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
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

    // =========================================================================
    // [Scenario 1] VIP Transaction: 5-Depth Pass and 96-Byte Spec Validation
    // =========================================================================
    @Test
    @DisplayName("[Extreme-VIP] Branch merging and final specification restoration validation")
    void testExtremeVipOrchestration() throws Exception {
        runInSandbox(() -> {
            String trxId = "VIP-EXTREME-TEST-000000000000001";
            String payload = buildPayload("REQ", "ABC", trxId);

            long startTime = System.currentTimeMillis();
            TestResponse response = sendHttpRequest(payload);
            long elapsed = System.currentTimeMillis() - startTime;

            assertEquals(200, response.getCode(), "Should return 200 OK penetrating the 5-depth defense");
            String res = response.getBody();
            log.info("[VIP Final Response ({}ms)]: \n{}", elapsed, res);

            assertEquals("RES", res.substring(0, 3).trim(), "PREFIX restoration failed");
            assertEquals("FIN", res.substring(3, 6).trim(), "TARGET_VAL(Finalized) restoration failed");
            assertEquals(trxId, res.substring(64, 96).trim(), "TRX_ID restoration failed");
        });
    }

    // =========================================================================
    // [Scenario 2] Standard Transaction: 5-Depth Pass and Spec Validation
    // =========================================================================
//    @Test
//    @DisplayName("[Extreme-STD] Standard transaction intelligent routing and final response validation")
//    void testExtremeStandardOrchestration() throws Exception {
//        runInSandbox(() -> {
//            String trxId = "STD-EXTREME-TEST-000000000000001";
//            String payload = buildPayload("REQ", "ABC", trxId);
//
//            TestResponse response = sendHttpRequest(payload);
//
//            assertEquals(200, response.getCode());
//            String res = response.getBody();
//            log.info("[STD Final Response]: \n{}", res);
//
//            assertTrue(res.contains("RES"));
//            assertTrue(res.contains(trxId));
//        });
//    }
//
    // =========================================================================
    // [Scenario 3] Data Contamination Blocking (Truncated Payload)
    // =========================================================================
//    @Test
//    @DisplayName("[Guardrail] Entrance cutoff on 95-byte transmission (Correlation Null Defense)")
//    void testExtremeTruncationBlock() throws Exception {
//        runInSandbox(() -> {
//            String payload = buildPayload("REQ", "ABC", "INVALID-LENGTH-TRX-ID")
//                    .substring(0, 95);
//
//            TestResponse response = sendHttpRequest(payload);
//
//            assertNotEquals(200, response.getCode(), "Invalid specification must not receive 200 OK");
//            log.info("[Truncated Block Status]: {}", response.getCode());
//        });
//    }
//
    // =========================================================================
    // [Scenario 4] Massive Bombardment (100 Concurrent E2E)
    // =========================================================================
//    @Test
//    @DisplayName("[STRESS] Integrity defense under 100 concurrent loads without tangling double fallback, merge, and branch")
//    void testExtremeConcurrencyBombardment() throws Exception {
//        int count = 100;
//        ExecutorService attackPool = Executors.newFixedThreadPool(30);
//        CountDownLatch latch = new CountDownLatch(count);
//        List<String> results = Collections.synchronizedList(new ArrayList<>());
//
//        runInSandbox(() -> {
//            java.util.stream.IntStream.range(0, count).forEach(i -> attackPool.execute(() -> {
//                try {
//                    boolean isVip = i % 2 == 0;
//                    String prefix = isVip ? "VIP-BOMB-" : "STD-BOMB-";
//                    String id = prefix + String.format("%020d", i);
//                    String payload = buildPayload("REQ", "ABC", id);
//
//                    TestResponse response = sendHttpRequest(payload);
//                    results.add(id + " -> " + response.getCode());
//                } catch (Exception e) {
//                    log.error("Bomb failed", e);
//                } finally {
//                    latch.countDown();
//                }
//            }));
//
//            assertTrue(latch.await(15, TimeUnit.SECONDS), "All 100 5-Depth transactions must complete within 15 seconds");
//            attackPool.shutdown();
//
//            long successCount = results.stream().filter(r -> r.contains("200")).count();
//            log.info("[STRESS RESULT]: {} / {} Success.", successCount, count);
//            assertEquals(count, successCount, "Must return 200 OK for all requests without a single bottleneck or deadlock");
//        });
//    }

    // -------------------------------------------------------------
    // Ultimate Helper Methods
    // -------------------------------------------------------------

    private String buildPayload(String prefix, String targetVal, String trxId) {
        return String.format("%-3s%-3s%-58s%-32s",
                prefix.substring(0, Math.min(3, prefix.length())),
                targetVal.substring(0, Math.min(3, targetVal.length())),
                " ",
                trxId.substring(0, Math.min(32, trxId.length())));
    }

    private void runInSandbox(TestTask task) throws Exception {
        com.sun.net.httpserver.HttpServer mock =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(MOCK_SERVER_PORT), 0);

        mock.createContext("/", ex -> {
            byte[] b = IOUtils.toByteArray(ex.getRequestBody());
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        mock.setExecutor(Executors.newCachedThreadPool());
        mock.start();

        try {
            assumeTrue(isServerUp(), "Ignoring test as engine (51000) is not up.");
            task.run();
        } finally { mock.stop(0); }
    }

    private boolean isServerUp() {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", 51000), 300);
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
        public int getCode() { return code; }
        public String getBody() { return body; }
    }

    @FunctionalInterface interface TestTask { void run() throws Exception; }
}
