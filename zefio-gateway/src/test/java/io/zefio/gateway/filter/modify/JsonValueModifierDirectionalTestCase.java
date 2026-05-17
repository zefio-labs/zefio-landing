package io.zefio.gateway.filter.modify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zefio.core.GatewayInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ZefioMessage;
import io.zefio.testsupport.payload.IPayloadBuilderFactory;
import io.zefio.testsupport.payload.JsonPayloadBuilderFactory;
import io.zefio.testsupport.filter.AbstractNormalFilterTestCase;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JsonValueModifierDirectional 필터 테스트")
public class JsonValueModifierDirectionalTestCase extends AbstractNormalFilterTestCase {

    public JsonValueModifierDirectionalTestCase() throws Exception {
        super("modifyJsonBody");
    }

    @Override
    public IPayloadBuilderFactory createSenderBuilder() throws Exception {
        return JsonPayloadBuilderFactory.createStandardFactory("json-key-test", filterEncoding, 500, "");
    }

    @Override
    public GatewayInterceptor createFilter(Map<String, Object> context) {
        // 🚀 전역 팩토리에 JSON 빌더 등록
        return buildFilterWithContext(context);
    }

    // =========================================================================
    // 🛠️ [Helper 1] 필터 생성 보일러플레이트 제거
    // =========================================================================
    private GatewayInterceptor buildFilterWithContext(Map<String, Object> context) {
        PluginContext ctx = PluginContext.builder()
                .flowName("default")
                .pluginName(getClass().getName())
                .telegramName("json-key-test") // 🚀 생성자 검증용 공통 주입
                .sharedScheduledPool(sharedPool.getValue0())
                .sharedIoPool(sharedPool.getValue1())
                .context(context)
                .build();
        return new ValueModifierDirectional(ctx);
    }

    // =========================================================================
    // 🛠️ [Helper 2] 필터 재기동(Re-initialize) 헬퍼
    // =========================================================================
    private void reInitializeFilter(String yamlKey) throws Exception {
        Map<String, Object> context = getContext(yamlKey);
        this.filterEncoding = ObjectUtils.isEmpty(context.get("requestEncoding")) ?
                StandardCharsets.UTF_8 : Charset.forName(context.get("requestEncoding").toString());
        this.filter = buildFilterWithContext(context);
        this.filter.initialise();
    }

    // =========================================================================
    // 🛠️ [Helper 3] 이벤트 생성 보일러플레이트 제거
    // =========================================================================
    private Payload createJsonEvent(String jsonBody, String trxId) {
        byte[] bodyBytes = jsonBody != null ? jsonBody.getBytes(filterEncoding) : null;
        Payload payload = new ZefioMessage(bodyBytes, filterEncoding);
        payload.setTelegramName("json-key-test"); // 🚀 팩토리 조회용 공통 주입
        payload.setTrxID(trxId);
        return payload;
    }


    // =========================================================================
    // 🧪 테스트 케이스 시작 (반복 코드 완벽 제거)
    // =========================================================================

    @Test
    @DisplayName("MODIFY_BODY: 이벤트 body 내부 값 치환 테스트")
    public void testModifyBody() throws Exception {
        Payload requestPayload = createJsonEvent("{ \"person\": { \"name\": \"Bob\", \"age\": 20 } }", "abc");

        filter.executeAsync(requestPayload, Executors.newSingleThreadExecutor()).join();
        String result = new String(requestPayload.getBody(), filterEncoding);

        // 단순 문자열 비교
        assertTrue(result.contains("\"name\":\"Alice\""));
        assertTrue(result.contains("\"age\":\"30\""));
    }

    @Test
    @DisplayName("PROPERTY_TO_BODY: property → body 삽입 테스트")
    public void testPropertyToBody() throws Exception {
        reInitializeFilter("propertyToBody");

        // 기존 JSON에 user 객체가 있고 name, age 모두 존재
        Payload payload = createJsonEvent("{\"user\":{\"name\":\"XXX\",\"age\":30}}", "trx002");

        // 이벤트 property 설정
        payload.setHeader("userName", "Alice");
        payload.setHeader("userAge", 25);  // age 추가

        filter.executeAsync(payload, Executors.newSingleThreadExecutor()).join();

        // body JSON 비교
        ObjectMapper mapper = new ObjectMapper();
        JsonNode expectedJson = mapper.readTree("{\"user\":{\"name\":\"Alice\",\"age\":25}}");
        JsonNode actualJson = mapper.readTree(new String(payload.getBody(), filterEncoding));
        assertEquals(expectedJson, actualJson);

        // property 값 체크
        assertEquals("Alice", payload.getHeader("userName"));
        assertEquals(25, payload.getHeader("userAge"));

        filter.close();
    }

    @Test
    @DisplayName("BODY_TO_PROPERTY: body → property 추출 테스트")
    public void testBodyToProperty() throws Exception {
        reInitializeFilter("bodyToProperty");

        Payload requestPayload = createJsonEvent("{ \"user\": { \"name\": \"Bob\", \"age\": \"40\" } }", "trx003");

        filter.executeAsync(requestPayload, Executors.newSingleThreadExecutor()).join();

        assertEquals("Bob", requestPayload.getHeader("userName"));
        assertEquals("40", requestPayload.getHeader("userAge"));

        filter.close();
    }
}
