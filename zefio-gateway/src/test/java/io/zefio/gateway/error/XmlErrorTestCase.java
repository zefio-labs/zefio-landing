package io.zefio.gateway.error;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.GatewayInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ZefioMessage;
import io.zefio.testsupport.payload.IPayloadBuilderFactory;
import io.zefio.testsupport.payload.XmlPayloadBuilderFactory;
import io.zefio.testsupport.filter.AbstractNormalFilterTestCase;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@DisplayName("XmlError 필터 테스트")
public class XmlErrorTestCase extends AbstractNormalFilterTestCase {

    public XmlErrorTestCase() throws Exception {
        super("xml-test.yaml", "xml1"); // resources/error/xml1.yml 설정 사용
    }

    @Override
    public IPayloadBuilderFactory createSenderBuilder() throws Exception {
        return XmlPayloadBuilderFactory.createStandardFactory("xml-default", filterEncoding, 500, "");
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
                .telegramName("xml-default") // 🚀 생성자 검증용 공통 주입
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
    private Payload createXmlEvent(String xmlBody) {
        byte[] bodyBytes = xmlBody != null ? xmlBody.getBytes(filterEncoding) : null;
        Payload payload = new ZefioMessage(bodyBytes, filterEncoding);
        payload.setTelegramName("xml-default"); // 🚀 팩토리 조회용 공통 주입
        return payload;
    }

    // =========================================================================
    // 🧪 테스트 케이스 시작 (반복 코드 완벽 제거)
    // =========================================================================

    @Test
    @DisplayName("XML 에러 필터 - 에러 코드 대체 테스트")
    void testXmlErrorWithErrorCodeReplacement() throws Exception {
        // 🚀 헬퍼 적용으로 Event 생성 로직 축소!
        String xml = "<root><code>E123</code><message>fail</message><errorMessage>fail</errorMessage></root>";
        Payload requestPayload = createXmlEvent(xml);
        requestPayload.setThrowable(new FlowException(new Exception(), FlowResultStatus.CUSTOM_FILTER_ERROR, "E123", ""));

        Payload responsePayload = executeAssert(requestPayload);
        String result = new String(responsePayload.getBody(), filterEncoding);
        System.out.println("Modified XML: " + result);

        // key "code" 가 "ABC"로 치환됨
        assert result.contains("<code>ABC</code>");
        // valueOverrides에 의한 덮어쓰기 "desc" = "xml override" 존재 여부 확인
        assert result.contains("<desc>xml override</desc>");
        // messageRule.key ("errorMessage")는 에러 메시지 병합용으로 유지됨
        assert result.contains("E123");
    }

}
