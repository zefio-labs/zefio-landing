package io.zefio.testsupport.endpoint;

import io.zefio.core.Ingress;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.testsupport.payload.AbstractTestCase;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Base test case for validating Ingress components.
 * Sets up a receiver (Ingress) and captures incoming events asynchronously.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class TestSourceToIngressTestCase extends AbstractTestCase {
    protected final String ingressName;
    protected Charset receiverEncoding;

    protected final AtomicReference<Payload> receiverCapturedEvent = new AtomicReference<>();
    protected Ingress receiver;
    private final ExecutorService service = Executors.newFixedThreadPool(1);

    public TestSourceToIngressTestCase(String ingressName) throws Exception {
        super();
        this.ingressName = ingressName;
    }

    /**
     * Factory method to create the specific Ingress component under test.
     */
    public abstract Ingress createReceiver(PluginContext.PluginContextBuilder builder);

    public PayloadBuilder initReceiverBuilder() throws Exception {
        return senderBuilder;
    }

    @BeforeEach
    @DisplayName("Initialize Ingress receiver and start listener thread")
    protected void setupServer() throws Exception {
        initSenderFactoryBuilder();

        PayloadBuilder receiverBuilder = initReceiverBuilder();
        Map<String, Object> context = getContext(ingressName);

        this.receiverEncoding = ObjectUtils.isEmpty(context.get("responseEncoding")) ?
                Charset.forName(context.get("requestEncoding").toString()) :
                Charset.forName(context.get("responseEncoding").toString());

        PluginContext.PluginContextBuilder builder = PluginContext.builder()
                .flowName("default")
                .pluginName(getClass().getName())
                .telegramName(receiverBuilder.getTelegram().getName())
                .sharedScheduledPool(sharedPool.getValue0())
                .sharedIoPool(sharedPool.getValue1())
                .context(context);

        receiver = createReceiver(builder);
        receiver.initialise();

        // Start Ingress receiving in a background thread
        service.submit(() -> {
            receiver.receive((event) -> {
                try {
                    Payload response = handleFilter(event);
                    receiverCapturedEvent.set(response);
                    response.getCallback().success(response);
                } catch (Exception e) {
                    throw new RuntimeException("Ingress receive callback failed", e);
                }
            });
        });
    }

    @AfterAll
    void shutdown() {
        service.shutdownNow();
        if (receiver != null) receiver.close();
    }

    public Payload handleFilter(Payload requestPayload) throws Exception {
        return requestPayload;
    }

    /**
     * Waits for the Ingress component to capture an event within a specified timeout.
     */
    protected Payload getReceiverCapturedEvent() {
        await()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> receiverCapturedEvent.get() != null);

        Payload receivedPayload = receiverCapturedEvent.get();
        assertNotNull(receivedPayload, "Captured payload must not be null");
        return receivedPayload;
    }
}
