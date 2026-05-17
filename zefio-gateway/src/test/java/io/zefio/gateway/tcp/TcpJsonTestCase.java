package io.zefio.gateway.tcp;

import io.zefio.core.Ingress;
import io.zefio.core.Upstream;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ExchangePattern;
import io.zefio.testsupport.endpoint.UpstreamToIngressIntegrationTestCase;
import io.zefio.testsupport.payload.IPayloadBuilderFactory;
import io.zefio.testsupport.payload.JsonPayloadBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tcp 2way 테스트")
public class TcpJsonTestCase extends UpstreamToIngressIntegrationTestCase {
    public TcpJsonTestCase() throws Exception {
        super("tcpInbound_json", "tcpOutbound_json");
    }

    private final String DELIMITER = "\n";

    // 🚀 완전히 다이어트 성공!
    // Telegram 조립, Framing, Correlation 설정, Register가 모두 한 줄에 처리됨.
    @Override
    public IPayloadBuilderFactory createSenderBuilder() throws Exception {
        return JsonPayloadBuilderFactory.createStandardFactory("json-spel-test", senderEncoding, 500, DELIMITER);
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
        String request = new String(requestPayload.getBody(), senderEncoding);
        String response = request.replace("req", "res");
        byte[] send = response.getBytes(receiverEncoding);
        requestPayload.setBody(send);

        return requestPayload;
    }

    @Test
    @DisplayName("Tcp 송수신 테스트 (JSON 전문 처리)")
    void testTcpSyncRequestResponse() throws Exception {
        send();
    }
}
