package io.zefio.gateway.error;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.GatewayInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.builder.config.*;
import io.zefio.core.payload.util.TelegramFactory;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ZefioMessage;
import io.zefio.testsupport.payload.FixedPayloadBuilderFactory;
import io.zefio.testsupport.payload.IPayloadBuilderFactory;
import io.zefio.testsupport.filter.AbstractNormalFilterTestCase;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FixedError 필터 테스트")
public class FixedErrorTestCase extends AbstractNormalFilterTestCase {

    public FixedErrorTestCase() throws Exception {
        super("fixed-test.yaml", "error1");
    }

    @Override
    public IPayloadBuilderFactory createSenderBuilder() throws Exception {
        FramingField framing = new FramingField();
        framing.setType(FramingType.Length);
        framing.setLengthDataSize(4);
        framing.setLengthDataInclude(false);
        framing.setLengthDataUpdate(true);

        CorrelationField correlation = new CorrelationField(CorrelationIdType.Offset);
        correlation.setStart(43);
        correlation.setLength(20);

        return new FixedPayloadBuilderFactory(
                new Telegram.Builder()
                        .name("fixed")
                        .type(Telegram.Type.Fixed)
                        .values(FixedValues.builder()
                                .framing(framing)
                                .correlation(correlation)
                                .encodingIgnore(false)
                                .build())
                        .build(),
                filterEncoding);
    }

    @Override
    public GatewayInterceptor createFilter(Map<String, Object> context) {
        TelegramFactory.register("fixed", senderBuilder);
        return buildFilterWithContext(context);
    }

    private GatewayInterceptor buildFilterWithContext(Map<String, Object> context) {
        PluginContext ctx = PluginContext.builder()
                .flowName("default")
                .pluginName(getClass().getName())
                .telegramName("fixed")
                .sharedScheduledPool(sharedPool.getValue0())
                .sharedIoPool(sharedPool.getValue1())
                .context(context)
                .build();
        return new StructuredFaultHandler(ctx);
    }

    private void reInitializeFilter(String yamlKey) throws Exception {
        Map<String, Object> context = getContext(yamlKey);
        this.filterEncoding = ObjectUtils.isEmpty(context.get("requestEncoding")) ?
                StandardCharsets.UTF_8 : Charset.forName(context.get("requestEncoding").toString());
        this.filter = buildFilterWithContext(context);
        this.filter.initialise();
    }

    private Payload createFixedEvent(String bodyStr) {
        byte[] bodyBytes = bodyStr != null ? bodyStr.getBytes(filterEncoding) : null;
        Payload payload = new ZefioMessage(bodyBytes, filterEncoding);
        payload.setTelegramName("fixed");
        return payload;
    }

    @Test
    @DisplayName("일반에러, errorCodeRules, valueOverrides, messageRule 테스트 (error1)")
    void testProcessSync_withValidErrorValuesAndFlowException() throws Exception {
        Payload requestPayload = createFixedEvent("ORIGINAL_MESSAGE");
        // FlowException의 메시지는 내부적으로 "[E123] " 형태로 가공됨
        requestPayload.setThrowable(new FlowException(new IOException(), FlowResultStatus.CUSTOM_FILTER_ERROR, "E123", ""));

        Payload responsePayload = executeAssert(requestPayload);

        String modifiedBodyStr = new String(responsePayload.getBody(), filterEncoding);
        System.out.println("Modified body: " + modifiedBodyStr);

        // 🚀 [수정] 13바이트 Payload + E123 에러코드 병합 결과 확인
        assertTrue(modifiedBodyStr.startsWith("0013IABC_M[E123]"));
    }

    @Test
    @DisplayName("NullPointerException 에러, errorCodeRules, valueOverrides, messageRule 테스트 (error2)")
    void testProcessSync_withNullPointerExceptionCause() throws Exception {
        reInitializeFilter("error2");

        Payload requestPayload = createFixedEvent("SIMPLESIMPLESIMPLESIMPLE");
        requestPayload.setThrowable(new FlowException(new NullPointerException("NPE"), FlowResultStatus.CUSTOM_FILTER_ERROR));

        Payload responsePayload = executeAssert(requestPayload);

        String modifiedBodyStr = new String(responsePayload.getBody(), filterEncoding);
        System.out.println("Modified body: " + modifiedBodyStr);

        // 🚀 정상 통과 (10바이트 Payload + null 병합)
        assertTrue(modifiedBodyStr.startsWith("0010LESZZZnull"));
    }

    @Test
    @DisplayName("일반에러, errorCodeRules, valueOverrides, messageRule 테스트 (error3)")
    void testProcessSync_withNoErrorMessageFormat() throws Exception {
        reInitializeFilter("error3");

        Payload requestPayload = createFixedEvent("SIMPLE");
        // "Error Happened" 문자열 주입 (14바이트)
        requestPayload.setThrowable(new FlowException(new Exception("Error Happened"), FlowResultStatus.CUSTOM_FILTER_ERROR));

        Payload responsePayload = executeAssert(requestPayload);

        String modifiedBodyStr = new String(responsePayload.getBody(), filterEncoding);
        System.out.println("Modified body: " + modifiedBodyStr);

        // 🚀 [수정] "Error Happened" 병합 결과로 총 Payload 16바이트 보정값 확인
        assertTrue(modifiedBodyStr.startsWith("0016LEError Happened"));
    }
}
