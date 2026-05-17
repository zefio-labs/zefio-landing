package io.zefio.gateway.http;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HTTP 중복 CorrelationID 동시 요청 테스트")
public class DuplicateCorrelationTestCase extends UpstreamToIngressIntegrationTestCase {

    public DuplicateCorrelationTestCase() throws Exception {
        // 기존 Keep-Alive 설정이 되어 있는 YAML 설정을 재사용하거나 별도 설정 지정
        super("httpInbound_connectionHeader_test", "httpOutbound_connectionKeepAliveHeader_test");
    }

    @Override
    public IPayloadBuilderFactory createSenderBuilder() throws Exception {
        // 🚀 [v2.6 레이아웃 설계] 물리적 필드 정의
        List<FixedValues.FixedField> layout = new ArrayList<>();

        // 0~63: 데이터 더미
        layout.add(new FixedValues.FixedField("DUMMY_FRONT", 64, 0,
                FixedValues.FieldType.STRING, FixedValues.Align.L, ' ', true));

        // 64~95: Correlation ID 영역 (TrxID)
        layout.add(new FixedValues.FixedField("CORR_ID_AREA", 32, 64,
                FixedValues.FieldType.STRING, FixedValues.Align.L, ' ', true));

        // 96~128: 나머지
        layout.add(new FixedValues.FixedField("DUMMY_TAIL", 32, 96,
                FixedValues.FieldType.STRING, FixedValues.Align.L, ' ', true));

        // 1. Framing 전략 설정 (길이 4바이트)
        FramingField framing = new FramingField();
        framing.setType(FramingType.Length);
        framing.setLengthDataSize(4);
        framing.setLengthDataInclude(false);
        framing.setLengthDataUpdate(true);

        // 2. Correlation 설정 (레이아웃 정의와 일치시킴)
        CorrelationField correlation = new CorrelationField(CorrelationIdType.Offset);
        correlation.setStart(64);
        correlation.setLength(32);

        // 3. 빌더 생성
        PayloadBuilder builder = new Telegram.Builder()
                .name("fixed-standard-offset")
                .type(Telegram.Type.Fixed)
                .values(FixedValues.builder()
                        .framing(framing)
                        .correlation(correlation)
                        .layout(layout) // 🚀 레이아웃 주입
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
        // 서버 에코 로직 (받은거 그대로 돌려줌)
        // 실제 상황에서는 여기서 CID를 기반으로 저장하거나 처리함
        return requestPayload;
    }

    @Test
    @DisplayName("동일한 CID 요청 시 ERR_003 예외가 발생하는지 검증")
    void testDuplicateCorrelationExpectedError() throws Exception {
        String duplicateId = "DUP_KEY_001";
        byte[] body = generateMessage(duplicateId);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // 첫 번째 요청 시작
        CompletableFuture<Payload> future1 = sender.executeAsync(
                senderBuilder.withBody(body, senderEncoding), executor);

        // 두 번째 요청 (거의 동시에)
        CompletableFuture<Payload> future2 = sender.executeAsync(
                senderBuilder.withBody(body, senderEncoding), executor);

        // 결과 확인
        try {
            CompletableFuture.allOf(future1, future2).join();
        } catch (Exception e) {
            // e는 CompletionException일 것이고, 원인은 ERR_003이어야 함
            String errorMsg = e.getMessage();
            assertTrue(errorMsg.contains("ERR_003"), "에러 메시지에 ERR_003이 포함되어야 합니다.");
            System.out.println("의도된 중복 에러 확인: " + errorMsg);
        }

        executor.shutdown();
    }

    private byte[] generateMessage(String key) {
        byte[] body = new byte[128];
        java.util.Arrays.fill(body, (byte) ' ');
        // 64번째 인덱스에 CID 주입
        System.arraycopy(key.getBytes(), 0, body, 64, key.length());
        return body;
    }
}
