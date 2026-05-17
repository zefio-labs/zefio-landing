package io.zefio.gateway.error;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@DisplayName("JsonError 필터 테스트")
public class JsonErrorTestCase extends AbstractNormalFilterTestCase {

    public JsonErrorTestCase() throws Exception {
        super("json-test.yaml", "json1"); // resources/error/json1.yml 설정 사용
    }

    @Override
    public IPayloadBuilderFactory createSenderBuilder() throws Exception {
        return JsonPayloadBuilderFactory.createStandardFactory("json-default", filterEncoding, 500, "");
    }

    @Override
    public GatewayInterceptor createFilter(Map<String, Object> context) {
        return buildFilterWithContext(context);
    }

    // =========================================================================
    // 🛠️ [Helper 1] 필터 생성 보일러플레이트 제거
    // =========================================================================
    private GatewayInterceptor buildFilterWithContext(Map<String, Object> context) {
        PluginContext ctx = PluginContext.builder()
                .flowName("default")
                .pluginName(getClass().getName())
                .telegramName("json-default") // 🚀 생성자 검증용 공통 주입
                .sharedScheduledPool(sharedPool.getValue0())
                .sharedIoPool(sharedPool.getValue1())
                .context(context)
                .build();
        return new StructuredFaultHandler(ctx);
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
    private Payload createJsonEvent(String jsonBody) {
        byte[] bodyBytes = jsonBody != null ? jsonBody.getBytes(filterEncoding) : null;
        Payload payload = new ZefioMessage(bodyBytes, filterEncoding);
        payload.setTelegramName("json-default"); // 🚀 팩토리 조회용 공통 주입
        return payload;
    }

    // =========================================================================
    // 🧪 테스트 케이스 시작 (반복 코드 완벽 제거)
    // =========================================================================

    @Test
    @DisplayName("JSON 에러 필터 - FlowException 대체 테스트")
    void testJsonErrorWithErrorCodeReplacement() throws Exception {
        // 🚀 헬퍼 적용으로 Event 생성 로직이 단 한 줄로 축소!
        Payload requestPayload = createJsonEvent("{\"code\":\"E123\",\"message\":\"original\"}");
        requestPayload.setThrowable(new FlowException(new IOException("IO Fail"), FlowResultStatus.CUSTOM_FILTER_ERROR, "E123", ""));

        Payload responsePayload = executeAssert(requestPayload);
        String result = new String(responsePayload.getBody(), filterEncoding);
        System.out.println("Modified JSON: " + result);

        // Then (결과 검증 수정)
        // 1. errorCodeRules에 의해 "E123"이 "ABC"로 바뀌었는지 확인
        assert(result.contains("\"code\":\"ABC\""));

        // 2. valueOverrides에 의해 "desc" 키가 추가되었는지 확인
        assert(result.contains("\"desc\":\"json override\""));

        // 3. 기존 필드가 유지되는지 확인
        assert(result.contains("\"message\":\"original\""));
    }
}
