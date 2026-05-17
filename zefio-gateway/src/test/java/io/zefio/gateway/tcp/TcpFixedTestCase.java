package io.zefio.gateway.tcp;

import io.zefio.core.Ingress;
import io.zefio.core.Upstream;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ExchangePattern;
import io.zefio.testsupport.endpoint.UpstreamToIngressIntegrationTestCase;
import io.zefio.testsupport.payload.FixedPayloadBuilderFactory;
import io.zefio.testsupport.payload.IPayloadBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tcp 2way 테스트")
public class TcpFixedTestCase extends UpstreamToIngressIntegrationTestCase {
    public TcpFixedTestCase() throws Exception {
        super("tcpInbound_fixed", "tcpOutbound_fixed");
    }

    // 🚀 완전히 다이어트 성공!
    // Telegram 조립, Framing, Correlation 설정, Register가 모두 한 줄에 처리됨.
    @Override
    public IPayloadBuilderFactory createSenderBuilder() throws Exception {
        return FixedPayloadBuilderFactory.createStandardFactory("fixed-standard-tcp", senderEncoding, 500);
    }

    @Override
    public Ingress createReceiver(PluginContext.PluginContextBuilder builder) {
        return new TcpIngress(builder.exchangePattern(ExchangePattern.RequestReply).build());
    }

    @Override
    public Upstream createSender(PluginContext.PluginContextBuilder builder) {
        return new TcpUpstream(builder.exchangePattern(ExchangePattern.RequestReply).build());
    }

    @Override
    public Payload handleFilter(Payload requestPayload) throws Exception {
        String request = new String(requestPayload.getBody(), senderEncoding);
        byte[] send = request.replace("req", "res").getBytes(receiverEncoding);
        requestPayload.setBody(send);
        return requestPayload;
    }

    @Test
    @DisplayName("Tcp 송수신 테스트 (Fixed 전문 처리)")
    void testTcpSyncRequestResponse() throws Exception {
        send();
    }
}
