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

@DisplayName("HTTP Connection Header (Keep-Alive) 검증 테스트")
public class ConnectionKeepAliveHeaderTestCase extends UpstreamToIngressIntegrationTestCase {

    public ConnectionKeepAliveHeaderTestCase() throws Exception {
        // YAML 설정의 name과 일치해야 함
        super("httpInbound_connectionHeader_test", "httpOutbound_connectionKeepAliveHeader_test");
    }

    @Override
    public IPayloadBuilderFactory createSenderBuilder() throws Exception {
        // 🚀 [v2.6 레이아웃 설계] 물리적 필드 정의
        List<FixedValues.FixedField> layout = new ArrayList<>();

        // 0~63: 데이터 더미 영역
        layout.add(new FixedValues.FixedField("DUMMY_HEAD", 64, 0,
                FixedValues.FieldType.STRING, FixedValues.Align.L, ' ', true));

        // 64~95: Correlation ID (TID) 영역
        layout.add(new FixedValues.FixedField("CORR_ID_AREA", 32, 64,
                FixedValues.FieldType.STRING, FixedValues.Align.L, ' ', true));

        // 96~128: 테일 영역
        layout.add(new FixedValues.FixedField("DUMMY_TAIL", 32, 96,
                FixedValues.FieldType.STRING, FixedValues.Align.L, ' ', true));

        // 1. Framing 전략 설정 (길이 헤더 4바이트)
        FramingField framing = new FramingField();
        framing.setType(FramingType.Length);
        framing.setLengthDataSize(4);
        framing.setLengthDataInclude(false);
        framing.setLengthDataUpdate(true);

        // 2. Correlation 설정 (레이아웃 정의와 동기화)
        CorrelationField correlation = new CorrelationField(CorrelationIdType.Offset);
        correlation.setStart(64);
        correlation.setLength(32);

        // 3. Telegram 및 빌더 생성
        PayloadBuilder builder = new Telegram.Builder()
                .name("fixed-standard-offset")
                .type(Telegram.Type.Fixed)
                .values(FixedValues.builder()
                        .framing(framing)
                        .correlation(correlation)
                        .layout(layout)            // 🚀 레이아웃 주입
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
        return new HttpUpstream(builder.exchangePattern(ExchangePattern.RequestReply).build());
    }

    @Override
    public Payload handleFilter(Payload requestPayload) throws Exception {
        // 서버 에코 로직
        return requestPayload;
    }

    @Test
    @DisplayName("Case 1: Connection Keep-Alive 테스트 - 연속 요청 시 CID 동일 여부 확인")
    void testConnectionKeepAlive() throws Exception {
        // 첫 번째 요청
        Payload req1 = senderBuilder.withBody(generateMessage("KEEP_TEST_01"), senderEncoding);

        sender.executeAsync(req1, Executors.newSingleThreadExecutor()).join();
        String cid1 = (String) getReceiverCapturedEvent().getMdcContext().get(MDCKey.CID.name());


        // 두 번째 요청 (연결이 유지되었다면 같은 CID 사용)
        Payload req2 = senderBuilder.withBody(generateMessage("KEEP_TEST_02"), senderEncoding);

        sender.executeAsync(req2, Executors.newSingleThreadExecutor()).join();
        String cid2 = (String) getReceiverCapturedEvent().getMdcContext().get(MDCKey.CID.name());

        System.out.println("Keep-Alive 테스트 - CID1: " + cid1 + ", CID2: " + cid2);

        // 동일한 아웃바운드 인스턴스에서 Keep-alive 시 CID가 같아야 함 (커넥션 풀링 작동 시)
        assertEquals(cid1, cid2, "Keep-Alive 요청임에도 커넥션이 재사용되지 않았습니다.");
    }

    private byte[] generateMessage(String key) {
        byte[] body = new byte[128];
        java.util.Arrays.fill(body, (byte) ' ');
        System.arraycopy(key.getBytes(), 0, body, 64, key.length());
        return body;
    }
}
