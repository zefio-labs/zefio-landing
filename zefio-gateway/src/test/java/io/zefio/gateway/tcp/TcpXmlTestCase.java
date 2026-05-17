package io.zefio.gateway.tcp;

import io.zefio.core.Ingress;
import io.zefio.core.Upstream;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ExchangePattern;
import io.zefio.testsupport.endpoint.UpstreamToIngressIntegrationTestCase;
import io.zefio.testsupport.payload.IPayloadBuilderFactory;
import io.zefio.testsupport.payload.XmlPayloadBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

@DisplayName("Tcp 2way 테스트")
public class TcpXmlTestCase extends UpstreamToIngressIntegrationTestCase {
    public TcpXmlTestCase() throws Exception {
        super("tcpInbound_xml", "tcpOutbound_xml");
    }

    private final String DELIMITER = "DDD";

    // 🚀 완전히 다이어트 성공!
    // Telegram 조립, Framing, Correlation 설정, Register가 모두 한 줄에 처리됨.
    @Override
    public IPayloadBuilderFactory createSenderBuilder() throws Exception {
        return XmlPayloadBuilderFactory.createStandardFactory("xml-delimiter-test", senderEncoding, 500, DELIMITER);
    }

    @Override
    public Ingress createReceiver(PluginContext.PluginContextBuilder builder) {
        PluginContext context = builder
                .exchangePattern(ExchangePattern.RequestReply)
                .build();
        return new TcpIngress(context);
    }
    @Override
    public Upstream createSender(PluginContext.PluginContextBuilder builder) {
        PluginContext context = builder
                .exchangePattern(ExchangePattern.RequestReply)
                .build();
        return new TcpUpstream(context);
    }

    @Override
    public Payload handleFilter(Payload requestPayload) throws Exception {
        // 🚀 [수정] 문자열 replace 대신 Map을 직접 핸들링하십시오.
        // getBodyMap()을 호출하는 순간 Lazy Parsing이 작동하여 정갈한 Map이 나옵니다.
        Map<String, Object> bodyMap = requestPayload.getBodyMap();

        // 비즈니스 로직: 필요한 가공 수행 (예: 응답 플래그 변경 등)
        bodyMap.put("processed", "true");
        requestPayload.setBodyMap(bodyMap);

        // 여기서는 단순히 원본 Map을 유지한 채 리턴해도 엔진이 Baking 시 동일한 태그를 생성합니다.
        requestPayload.setBodyMap(bodyMap);

        // 🚨 절대 수동으로 DELIMITER("DDD")를 붙이지 마십시오.
        // 엔진의 Framing 기능이 알아서 붙여줍니다.
        return requestPayload;
    }

    @Test
    @DisplayName("Tcp 송수신 테스트 (XML 전문 처리)")
    void testTcpSyncRequestResponse() throws Exception {
        send();
    }
}
