package io.zefio.gateway.filter.routing;

import io.zefio.core.payload.Payload;
import io.zefio.core.payload.builder.FixedPayloadBuilder;
import io.zefio.core.payload.builder.config.CorrelationField;
import io.zefio.core.payload.builder.config.CorrelationIdType;
import io.zefio.core.payload.builder.config.FixedValues;
import io.zefio.core.payload.builder.config.Telegram;
import io.zefio.core.payload.spel.PayloadExpressionEvaluator;
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SpELRouterModifyTestCase {
    private static final Logger log = LoggerFactory.getLogger(SpELRouterModifyTestCase.class);

    private FixedPayloadBuilder fixedPayloadBuilder;
    private byte[] rawPayload;
    private OkHttpClient okHttpClient;

    @BeforeEach
    void setUp() throws Exception {
        // 🚀 OkHttp 클라이언트 초기화
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        // 1. Telegram 레이아웃 세팅
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

        TelegramFactory.clear();
        TelegramFactory.register("fixed-http-req", Telegram.Type.Fixed, valuesMap);
        fixedPayloadBuilder = (FixedPayloadBuilder) TelegramFactory.getBuilder("fixed-http-req");

        // 2. 가상의 수신 데이터 생성 (자바 8 대응: String.repeat 대체)
        String payloadStr = "REQ" + "ABC" + fillSpaces(58) + "TRX-9999-8888-7777-6666-5555-444";
        rawPayload = payloadStr.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("1. [EventBuilder] 바이트 원문을 SpEL 및 레이아웃을 통해 TrxID 추출하는가?")
    void testSpelCorrelationExtraction() throws Exception {
        Payload payload = fixedPayloadBuilder.withBody(rawPayload, StandardCharsets.UTF_8);
        assertNotNull(payload.getTrxID());
        assertEquals("TRX-9999-8888-7777-6666-5555-444", payload.getTrxID());
    }

    @Test
    @DisplayName("2. [Lazy Parsing] 레이아웃 기반 바이트 분할(Map 투영) 검증")
    void testLayoutLazyParsing() throws Exception {
        Payload payload = fixedPayloadBuilder.withBody(rawPayload, StandardCharsets.UTF_8);
        Map<String, Object> parsedBody = fixedPayloadBuilder.parseToMap(payload.getBody(), payload.getCurrentEncoding());

        assertEquals("REQ", parsedBody.get("PREFIX"));
        assertEquals("ABC", parsedBody.get("TARGET_VAL"));

        String targetVal = PayloadExpressionEvaluator.evaluate("#{body['TARGET_VAL']}", payload, String.class);
        assertEquals("ABC", targetVal);
    }

    @Test
    @DisplayName("3. [Flow Simulation] FIXED_VALUE_MODIFIER 필터 동작 모의 테스트")
    void testFixedValueModifierSimulation() throws Exception {
        Payload payload = fixedPayloadBuilder.withBody(rawPayload, StandardCharsets.UTF_8);
        byte[] currentBody = payload.getBody();

        int targetOffset = 3;
        byte[] newValue = "XYZ".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(newValue, 0, currentBody, targetOffset, newValue.length);
        payload.setBody(currentBody);

        Map<String, Object> reParsedBody = fixedPayloadBuilder.parseToMap(payload.getBody(), payload.getCurrentEncoding());
        assertEquals("XYZ", reParsedBody.get("TARGET_VAL"));
    }

    @Test
    @DisplayName("4. [Real HTTP] ROUTER 필터의 XML 타겟 분기 및 이기종 변환 검증")
    void testRealHttpServerModificationXmlRoute() throws Exception {
        com.sun.net.httpserver.HttpServer mockTargetServer = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(51101), 0);
        mockTargetServer.createContext("/", ex -> {
            // 🚀 JDK 1.8 대응: readAllBytes() 대신 IOUtils 사용
            byte[] body = IOUtils.toByteArray(ex.getRequestBody());
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        mockTargetServer.setExecutor(null);
        mockTargetServer.start();

        try {
            URI targetUri = URI.create("http://127.0.0.1:51001/");
            assumeTrue(isServerUp(targetUri), "엔진이 기동되지 않았습니다.");

            String inputPayload = new String(rawPayload, StandardCharsets.UTF_8);
            TestResponse response = sendHttpRequest(targetUri, inputPayload);

            assertEquals(200, response.getCode());
            String resBody = response.getBody();
            log.info("🎯 [OUTPUT XML]: \n{}", resBody);

            assertTrue(resBody.contains("<PREFIX>RES</PREFIX>"));
            assertTrue(resBody.contains("<TARGET_VAL>XYZ</TARGET_VAL>"));
            assertTrue(resBody.endsWith("</MSG>"));
        } finally {
            mockTargetServer.stop(0);
        }
    }

    @Test
    @DisplayName("5. [Real HTTP] VALIDATOR 가드레일 필터의 Fail-Fast 차단 검증")
    void testValidatorFailFastBlock() throws Exception {
        URI targetUri = URI.create("http://127.0.0.1:51001/");
        assumeTrue(isServerUp(targetUri), "엔진이 기동되지 않았습니다.");

        String badPayload = "REQ" + "BAD" + fillSpaces(58) + "TRX-BLOCK-TEST-7777-6666-5555-444";

        TestResponse response = sendHttpRequest(targetUri, badPayload);
        assertTrue(response.getBody().contains("Guardrail Blocked"));
    }

    @Test
    @DisplayName("6. [Real HTTP] ROUTER 필터의 JSON 타겟 분기 및 이기종 변환 검증")
    void testRealHttpServerModificationJsonRoute() throws Exception {
        com.sun.net.httpserver.HttpServer mockTargetServer = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(51101), 0);
        mockTargetServer.createContext("/", ex -> {
            byte[] body = IOUtils.toByteArray(ex.getRequestBody());
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        mockTargetServer.start();

        try {
            URI targetUri = URI.create("http://127.0.0.1:51001/");
            assumeTrue(isServerUp(targetUri), "엔진이 기동되지 않았습니다.");

            String jsonPayload = "REQ" + "ABC" + fillSpaces(58) + "TRX-JSON-8888-7777-6666-5555-444";
            TestResponse response = sendHttpRequest(targetUri, jsonPayload);

            assertEquals(200, response.getCode());
            assertTrue(response.getBody().contains("\"PREFIX\":\"RES\""));
            assertTrue(response.getBody().contains("\"TARGET_VAL\":\"XYZ\""));
        } finally {
            mockTargetServer.stop(0);
        }
    }

    // -------------------------------------------------------------
    // 🛠️ Helper Methods (JDK 1.8 Compatible)
    // -------------------------------------------------------------

    private String fillSpaces(int length) {
        char[] spaces = new char[length];
        java.util.Arrays.fill(spaces, ' ');
        return new String(spaces);
    }

    private boolean isServerUp(URI targetUri) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(targetUri.getHost(), targetUri.getPort()), 500);
            return true;
        } catch (Exception e) { return false; }
    }

    private TestResponse sendHttpRequest(URI targetUri, String inputPayload) throws Exception {
        RequestBody body = RequestBody.create(inputPayload, MediaType.parse("text/plain; charset=utf-8"));
        Request request = new Request.Builder()
                .url(targetUri.toString())
                .post(body)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            String bodyStr = (response.body() != null) ? response.body().string() : "";
            return new TestResponse(response.code(), bodyStr);
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
