package io.zefio.gateway.filter.modify;

import io.zefio.core.GatewayInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ZefioMessage;
import io.zefio.testsupport.payload.FixedPayloadBuilderFactory;
import io.zefio.testsupport.payload.IPayloadBuilderFactory;
import io.zefio.testsupport.filter.AbstractNormalFilterTestCase;
import org.apache.commons.lang3.ObjectUtils;
import org.javatuples.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("FixedBinaryModifierDirectional 필터 테스트")
public class FixedValueModifierDirectionalTestCase extends AbstractNormalFilterTestCase {

    public FixedValueModifierDirectionalTestCase() throws Exception {
        super("fixed-body-insert-1");
    }

    @Override
    public IPayloadBuilderFactory createSenderBuilder() throws Exception {
        return FixedPayloadBuilderFactory.createStandardFactory("fixed", filterEncoding, 500);
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
                .telegramName("fixed") // 🚀 공통 주입
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
    private Payload createFixedEvent(String bodyStr, String trxId) {
        byte[] bodyBytes = bodyStr != null ? bodyStr.getBytes(filterEncoding) : null;
        Payload payload = new ZefioMessage(bodyBytes, filterEncoding);
        payload.setTelegramName("fixed"); // 🚀 공통 주입
        payload.setTrxID(trxId);
        return payload;
    }


    // =========================================================================
    // 🧪 테스트 케이스 시작 (놀랍도록 간결해진 코드!)
    // =========================================================================

    @Test
    @DisplayName("기존 body에서 header key 값을 지정된 위치에 넣기")
    public void testSetKeyIntoBody() throws Exception {
        Payload requestPayload = createFixedEvent("XXXXXX", "trx001");
        requestPayload.setHeader("header", "ABC".getBytes(filterEncoding));

        executeAssertBodyEquals(requestPayload, "ABCXXX");
    }

    @Test
    @DisplayName("key 데이터가 없는 경우 예외 발생")
    public void testMissingKeyData() {
        Payload requestPayload = createFixedEvent("123456", "trx003");
        executeAssertThrows(Exception.class, requestPayload);
    }

    @Test
    @DisplayName("누적 덮어쓰기 병합")
    public void testMultipleChildren() throws Exception {
        reInitializeFilter("fixed-body-insert-2");

        Payload requestPayload = createFixedEvent("ABCDEZ", "trx004");
        requestPayload.setHeader("key1", "XXX".getBytes(filterEncoding));
        requestPayload.setHeader("key2", "YY".getBytes(filterEncoding));

        executeAssertBodyPropertyEquals(requestPayload, "XXXYYZ", Arrays.asList(Pair.with("key1", "XXX"), Pair.with("key2", "YY")));
    }

    @Test
    @DisplayName("body보다 큰 key 데이터를 삽입할 때 body가 확장되는지 확인")
    public void testBodyExpansion() throws Exception {
        reInitializeFilter("fixed-body-insert-4");

        Payload requestPayload = createFixedEvent("1234", "trx006");
        requestPayload.setHeader("largeKey", "XYZ987".getBytes(filterEncoding));

        executeAssertBodyEquals(requestPayload, "1234XYZ987");
    }

    @Test
    @DisplayName("문자열 앞 부분을 key로 저장하고 body에서 제거")
    public void testExtractAndRemoveFromBody() throws Exception {
        reInitializeFilter("fixedkeyExtractor1");
        Payload requestPayload = createFixedEvent("ABCDEF", "trx001");
        executeAssertBodyPropertyEquals(requestPayload, "DEF", Pair.with("header", "ABC"));
    }

    @Test
    @DisplayName("데이터가 부족할 경우 예외 발생")
    public void testDataTooShort() throws Exception {
        reInitializeFilter("fixedkeyExtractor1");
        Payload requestPayload = createFixedEvent("AB", "trx002");
        executeAssertThrows(Exception.class, requestPayload);
    }

    @Test
    @DisplayName("key로 추출된 값이 정확히 저장되었는지 확인")
    public void testKeyPropertyStored() throws Exception {
        reInitializeFilter("fixedkeyExtractor1");
        Payload requestPayload = createFixedEvent("12345678", "trx003");
        executeAssertBodyPropertyEquals(requestPayload, "45678", Pair.with("header", "123"));
    }

    @Test
    @DisplayName("null 바디가 입력되었을 경우 예외 발생")
    public void testNullBodyThrowsException() throws Exception {
        reInitializeFilter("fixedkeyExtractor1");
        Payload requestPayload = createFixedEvent(null, "trx004");
        executeAssertThrows(Exception.class, requestPayload);
    }

    @Test
    @DisplayName("removeExtracted=true 옵션으로 추출된 부분이 body에서 제거되는지 확인")
    public void testExtractAndRemoveWithOptionTrue() throws Exception {
        reInitializeFilter("fixedkeyExtractor2");
        Payload requestPayload = createFixedEvent("ABCDEF", "trx005");
        executeAssertBodyPropertyEquals(requestPayload, "DEF", Pair.with("header", "ABC"));
    }

    @Test
    @DisplayName("removeExtracted=false 옵션일 때 body가 변경되지 않는지 확인")
    public void testExtractWithoutRemovingBody() throws Exception {
        reInitializeFilter("fixedkeyExtractor3");
        Payload requestPayload = createFixedEvent("ABCDEF", "trx006");

        filter.executeAsync(requestPayload, Executors.newSingleThreadExecutor()).join();

        byte[] extracted = (byte[]) requestPayload.getHeader("header");
        assertArrayEquals("ABC".getBytes(filterEncoding), extracted);
        assertEquals("ABCDEF", new String(requestPayload.getBody(), filterEncoding));

        filter.close();
    }

    @Test
    @DisplayName("문자열 중간값을 조건부로 치환")
    public void testOffsetReplace_conditionally() throws Exception {
        reInitializeFilter("offsetModifier1");
        Payload requestPayload = createFixedEvent("000ABC123", "abc");
        executeAssertBodyEquals(requestPayload, "000XYZ123");
    }

    @Test
    @DisplayName("find가 일치하지 않아도 replace 적용됨 (else 로직)")
    public void testOffsetReplace_noFind() throws Exception {
        reInitializeFilter("offsetModifier1");
        Payload requestPayload = createFixedEvent("000DEF123", "abc");
        executeAssertBodyEquals(requestPayload, "000XYZ123");
    }

    @Test
    @DisplayName("입력이 null 이면 예외 발생")
    public void testNullInput_throwsException() throws Exception {
        reInitializeFilter("offsetModifier1");
        Payload requestPayload = createFixedEvent(null, "abc");
        executeAssertThrows(Exception.class, requestPayload);
    }

    @Test
    @DisplayName("offset이 문자열 길이를 초과할 경우 예외 발생")
    public void testOffsetOutOfBounds_throwsException() throws Exception {
        reInitializeFilter("offsetModifier1");
        Payload requestPayload = createFixedEvent("00", "abc");
        executeAssertThrows(Exception.class, requestPayload);
    }
}
