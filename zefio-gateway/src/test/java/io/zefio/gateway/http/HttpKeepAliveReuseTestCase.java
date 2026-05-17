package io.zefio.gateway.http;

import io.zefio.core.common.base.MDCKey;
import io.zefio.core.Ingress;
import io.zefio.core.Upstream;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ExchangePattern;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.*;
import io.zefio.testsupport.endpoint.UpstreamToIngressIntegrationTestCase;
import io.zefio.testsupport.payload.FixedPayloadBuilderFactory;
import io.zefio.testsupport.payload.IPayloadBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Http Keep-Alive 재사용 통합 테스트")
public class HttpKeepAliveReuseTestCase extends UpstreamToIngressIntegrationTestCase {

    public HttpKeepAliveReuseTestCase() throws Exception {
        // 설정 파일에 정의된 httpInbound_fixed와 httpOutbound_fixed 설정을 로드
        super("httpInbound_keepalive", "httpOutbound_keepalive");
    }

    @Override
    public IPayloadBuilderFactory createSenderBuilder() throws Exception {
        // 🚀 [v2.6 레이아웃 설계] 전문의 물리적 구조 정의
        List<FixedValues.FixedField> layout = new ArrayList<>();

        // 1. 헤더 영역 (0~63)
        layout.add(new FixedValues.FixedField("HEADER_DUMMY", 64, 0,
                FixedValues.FieldType.STRING, FixedValues.Align.L, ' ', true));

        // 2. TID 영역 (64~95) - 테스트 메서드에서 데이터를 박는 위치
        layout.add(new FixedValues.FixedField("TID_AREA", 32, 64,
                FixedValues.FieldType.STRING, FixedValues.Align.L, ' ', true));

        // 3. 나머지 영역 (96~500)
        layout.add(new FixedValues.FixedField("REMAINDER", 404, 96,
                FixedValues.FieldType.STRING, FixedValues.Align.L, ' ', true));

        // Framing 및 Correlation 설정 (기존 규격 유지)
        FramingField framing = new FramingField();
        framing.setType(FramingType.EOF);

        CorrelationField correlation = new CorrelationField(CorrelationIdType.SpEL);
        correlation.setExpression("#{body['TID_AREA']}");

        PayloadBuilder builder = new Telegram.Builder()
                .name("http-keepalive-fixed")
                .type(Telegram.Type.Fixed)
                .values(FixedValues.builder()
                        .framing(framing)
                        .correlation(correlation)
                        .layout(layout) // 🚀 정의한 레이아웃 주입
                        .encodingIgnore(false)
                        .build())
                .build();

        return new FixedPayloadBuilderFactory(builder, senderEncoding);
    }

    @Override
    public Ingress createReceiver(PluginContext.PluginContextBuilder builder) {
        return new HttpIngress(builder.exchangePattern(ExchangePattern.RequestReply).build());
    }

    @Override
    public Upstream createSender(PluginContext.PluginContextBuilder builder) {
        // Outbound 설정에서 keepAlive: keep-alive 가 이미 설정되어 있음
        return new HttpUpstream(builder.exchangePattern(ExchangePattern.RequestReply).build());
    }

    @Override
    public Payload handleFilter(Payload requestPayload) throws Exception {
        // 서버 측 로직: 받은 내용을 응답으로 에코(Echo)
        return requestPayload;
    }

    @Test
    @DisplayName("동일한 Upstream 커넥션을 이용한 연속 송수신 테스트")
    void testHttpConnectionReuse() throws Exception {
        // --- [STEP 1] 첫 번째 요청 전송 ---
        String tid1 = "REUSE_TEST_01_ABCDEFG_12345678";
        Payload reqPayload1 = senderBuilder.withBody(generateTestMessage(tid1), senderEncoding);

        System.out.println(">>> Sending First Request: " + tid1);
        sender.executeAsync(reqPayload1, Executors.newSingleThreadExecutor()).join();

        Payload resPayload1 = getReceiverCapturedEvent();
        assertNotNull(resPayload1, "첫 번째 응답이 null입니다.");
        System.out.println("<<< Received First Response. CID: " + resPayload1.getMdcContext().get(MDCKey.CID.name()));

        // 잠시 대기 (서버가 연결을 유지하는지 확인하기 위함)
        Thread.sleep(2000);

        // --- [STEP 2] 두 번째 요청 전송 (동일한 sender 인스턴스 사용) ---
        String tid2 = "REUSE_TEST_02_HIJKLMN_87654321";
        Payload reqPayload2 = senderBuilder.withBody(generateTestMessage(tid2), senderEncoding);

        System.out.println(">>> Sending Second Request: " + tid2);
        sender.executeAsync(reqPayload2, Executors.newSingleThreadExecutor()).join();

        Payload resPayload2 = getReceiverCapturedEvent();
        assertNotNull(resPayload2, "두 번째 응답이 null입니다.");
        System.out.println("<<< Received Second Response. CID: " + resPayload2.getMdcContext().get(MDCKey.CID.name()));

        // --- [STEP 3] 검증 ---
        // 1. 응답 바디가 각 요청의 TID를 포함하고 있는지 확인
        String resBody1 = new String(resPayload1.getBody(), resPayload1.getCurrentEncoding());
        String resBody2 = new String(resPayload2.getBody(), resPayload2.getCurrentEncoding());

        assertEquals(true, resBody1.contains("REUSE_TEST_01"));
        assertEquals(true, resBody2.contains("REUSE_TEST_02"));

        // 2. (중요) 두 이벤트의 CID가 같은지 비교 (재사용 여부 확인)
        // 주의: 프레임워크 내부 속성 명칭에 따라 "CID" 혹은 "ChannelID" 등으로 꺼내야 합니다.
        Object cid1 = resPayload1.getMdcContext().get(MDCKey.CID.name());
        Object cid2 = resPayload2.getMdcContext().get(MDCKey.CID.name());

        System.out.println("First CID: " + cid1 + " / Second CID: " + cid2);
        assertEquals(cid1, cid2, "Connection이 재사용되지 않고 새로 생성되었습니다!");
    }

    // ✅ TO-BE (레이아웃 규격에 맞춘 500바이트 생성)
    private byte[] generateTestMessage(String tid) {
        byte[] body = new byte[500]; // 🚀 128 -> 500으로 수정
        java.util.Arrays.fill(body, (byte) ' ');
        byte[] tidBytes = tid.getBytes();
        // 64번째 오프셋부터 32바이트 한도 내에서 TID 복사 (정상 유지)
        System.arraycopy(tidBytes, 0, body, 64, Math.min(tidBytes.length, 32));
        return body;
    }
}
