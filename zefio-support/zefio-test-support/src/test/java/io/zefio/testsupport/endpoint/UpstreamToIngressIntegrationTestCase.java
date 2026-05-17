package io.zefio.testsupport.endpoint;

import io.zefio.core.Upstream;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test case for end-to-end Upstream to Ingress flow validation.
 * Verifies that messages sent via Upstream are correctly received by Ingress
 * with matching Transaction IDs (TrxID).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class UpstreamToIngressIntegrationTestCase extends TestSourceToIngressTestCase {
    protected Charset senderEncoding;
    protected final String upstreamName;
    protected Upstream sender;

    public UpstreamToIngressIntegrationTestCase(String ingressName, String upstreamName) throws Exception {
        super(ingressName);
        this.upstreamName = upstreamName;
    }

    /**
     * Factory method to create the specific Upstream component for sending.
     */
    public abstract Upstream createSender(PluginContext.PluginContextBuilder builder);

    @BeforeEach
    @DisplayName("Initialize Upstream sender and Ingress receiver")
    protected void setupServer() throws Exception {
        super.setupServer();

        Map<String, Object> context = getContext(upstreamName);
        this.senderEncoding = ObjectUtils.isNotEmpty(context.get("requestEncoding")) ?
                Charset.forName(context.get("requestEncoding").toString()) :
                StandardCharsets.UTF_8;

        PluginContext.PluginContextBuilder builder = PluginContext.builder()
                .flowName("default")
                .pluginName(getClass().getName())
                .telegramName(senderBuilder.getTelegram().getName())
                .sharedScheduledPool(sharedPool.getValue0())
                .sharedIoPool(sharedPool.getValue1())
                .context(context);

        sender = createSender(builder);
        sender.initialise();
    }

    @AfterAll
    void shutdown() {
        super.shutdown();
        if (sender != null) sender.close();
    }

    /**
     * Sends a message via Upstream and validates the response captured by Ingress.
     */
    protected Payload send(byte[] message) throws Exception {
        Payload requestPayload = senderBuilder.withBody(message, senderEncoding);
        String requestTrxId = requestPayload.getTrxID();

        log.info("Starting integration test: Send via Upstream. TrxID: {}", requestTrxId);

        // Execute asynchronous send and wait for Ingress capture
        sender.executeAsync(requestPayload, Executors.newSingleThreadExecutor()).join();
        Payload responsePayload = getReceiverCapturedEvent();

        // Validation of existence
        assertNotNull(responsePayload, "Response payload must not be null (Timeout or Processing Error)");
        assertNotNull(responsePayload.getTrxID(), "Response TrxID must be present");
        assertNotNull(responsePayload.getBody(), "Response Body must be present");

        // Strict validation of Transaction ID integrity
        String responseTrxId = responsePayload.getTrxID();
        assertEquals(requestTrxId, responseTrxId, "TrxID mismatch between Upstream request and Ingress reception");

        log.info("Integration test successful. Received TrxID: {}", responseTrxId);
        return responsePayload;
    }

    protected Payload send() throws Exception {
        return send(generateTestMessage());
    }
}
