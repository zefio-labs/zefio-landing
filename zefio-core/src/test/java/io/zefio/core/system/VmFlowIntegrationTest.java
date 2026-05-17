package io.zefio.core.system;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.Ingress;
import io.zefio.core.IngressHandler;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.engine.flow.FlowService;
import io.zefio.core.engine.registry.RouteDefinitionRegistry;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ExchangePattern;
import io.zefio.core.payload.ResponseListener;
import io.zefio.core.payload.ZefioMessage;
import io.zefio.core.telemetry.module.ModuleMetricsAggregator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VmFlowIntegrationTest {

    private static final String TARGET_FLOW_NAME = "target-flow-A";

    @Mock private PluginContext upstreamContext;
    @Mock private PluginContext ingressContext;
    @Mock private FlowService targetFlowService;
    @Mock private IngressHandler targetIngressHandler;
    @Mock private ModuleMetricsAggregator mockMetricsAggregator;

    private LocalUpstream vmUpstream;
    private LocalIngress localIngress;

    @BeforeEach
    void setUp() throws Exception {
        MDC.clear();
        RouteDefinitionRegistry.clear();

        // 1. Ingress Context Configuration
        when(ingressContext.getFlowName()).thenReturn(TARGET_FLOW_NAME);
        when(ingressContext.getPluginName()).thenReturn("vmIngress-1");
        when(ingressContext.getExchangePattern()).thenReturn(ExchangePattern.RequestReply);
        when(ingressContext.getContext()).thenReturn(new HashMap<>());

        localIngress = new LocalIngress(ingressContext) {
            @Override
            public ModuleMetricsAggregator getMetricsAggregator() {
                // Mock return for STAT processing in OneWay callback
                return mockMetricsAggregator;
            }
        };
        localIngress.receive(targetIngressHandler);

        // 2. Upstream Context Configuration
        when(upstreamContext.getFlowName()).thenReturn("source-flow");
        when(upstreamContext.getPluginName()).thenReturn("vmUpstream-1");

        Map<String, Object> yamlMap = new HashMap<>();
        yamlMap.put("targetFlow", TARGET_FLOW_NAME);
        yamlMap.put("timeout", 3000L);
        when(upstreamContext.getContext()).thenReturn(yamlMap);

        // 3. Target Flow Registry Registration
        when(targetFlowService.getIngress()).thenReturn(localIngress);
        RouteDefinitionRegistry.register(TARGET_FLOW_NAME, targetFlowService);
    }

    @AfterEach
    void tearDown() {
        RouteDefinitionRegistry.clear();
        MDC.clear();
    }

    // =========================================================================
    // Core 5 Test Cases
    // =========================================================================

    @Test
    @DisplayName("A SERVICE_HANDLER_NOT_FOUND exception must occur if the target flow does not exist.")
    void testvmUpstream_TargetFlowNotFound() {
        RouteDefinitionRegistry.clear();
        when(upstreamContext.getExchangePattern()).thenReturn(ExchangePattern.FireAndForget);
        vmUpstream = new LocalUpstream(upstreamContext);

        ZefioMessage requestEvent = new ZefioMessage("NotFound Request Payload".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        FlowException ex = assertThrows(FlowException.class, () -> vmUpstream.blockingProcessInternal(requestEvent));
        assertEquals(FlowResultStatus.SERVICE_HANDLER_NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("In One-Way mode, it must inject the event into the target Ingress and return immediately without waiting.")
    void testOneWayCall_Success() throws Exception {
        when(upstreamContext.getExchangePattern()).thenReturn(ExchangePattern.FireAndForget);
        vmUpstream = new LocalUpstream(upstreamContext);

        ZefioMessage requestEvent = new ZefioMessage("OneWay Request Payload".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        requestEvent.setSuppressStatLog(true);

        Payload result = vmUpstream.blockingProcessInternal(requestEvent);
        assertSame(requestEvent, result);

        ArgumentCaptor<Payload> eventCaptor = ArgumentCaptor.forClass(Payload.class);
        verify(targetIngressHandler, times(1)).onPayload(eventCaptor.capture());

        Payload injectedPayload = eventCaptor.getValue();
        assertFalse(injectedPayload.isSuppressStatLog(), "Statistical log suppression should be disabled upon entering LocalIngress");
    }

    @Test
    @DisplayName("In Two-Way mode, it must block and wait for the target flow's callback (success) and merge the results.")
    void testTwoWayCall_Success() throws Exception {
        when(upstreamContext.getExchangePattern()).thenReturn(ExchangePattern.RequestReply);
        vmUpstream = new LocalUpstream(upstreamContext);

        ZefioMessage requestEvent = new ZefioMessage("TwoWay Request Payload".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        ZefioMessage responseEvent = new ZefioMessage("Target Flow Success Response".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        doAnswer(invocation -> {
            Payload injectedPayload = invocation.getArgument(0);
            ResponseListener callback = injectedPayload.getCallback();
            Executors.newSingleThreadExecutor().submit(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                callback.success(responseEvent);
            });
            return null;
        }).when(targetIngressHandler).onPayload(any(Payload.class));

        Payload result = vmUpstream.blockingProcessInternal(requestEvent);
        assertEquals("Target Flow Success Response", new String(result.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("In Two-Way mode, a Timeout exception must occur if the target flow does not respond within the specified time.")
    void testTwoWayCall_Timeout() {
        when(upstreamContext.getExchangePattern()).thenReturn(ExchangePattern.RequestReply);
        Map<String, Object> yamlMap = new HashMap<>();
        yamlMap.put("targetFlow", TARGET_FLOW_NAME);
        yamlMap.put("timeout", 100L); // 100ms
        when(upstreamContext.getContext()).thenReturn(yamlMap);
        vmUpstream = new LocalUpstream(upstreamContext);

        ZefioMessage requestEvent = new ZefioMessage("Timeout Expected Payload".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        doNothing().when(targetIngressHandler).onPayload(any(Payload.class));

        FlowException ex = assertThrows(FlowException.class, () -> vmUpstream.blockingProcessInternal(requestEvent));
        assertEquals(FlowResultStatus.TIMEOUT, ex.getStatus());
    }

    @Test
    @DisplayName("Verify that a FlowException is properly propagated when the target flow triggers an error callback in Two-Way mode.")
    void testTwoWayCall_TargetError() throws Exception {
        when(upstreamContext.getExchangePattern()).thenReturn(ExchangePattern.RequestReply);
        vmUpstream = new LocalUpstream(upstreamContext);

        ZefioMessage requestEvent = new ZefioMessage("Error Trigger Payload".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        ZefioMessage errorEvent = new ZefioMessage(null, StandardCharsets.UTF_8);
        errorEvent.setThrowable(new FlowException(FlowResultStatus.INTERNAL_SERVER_ERROR, "Business Logic Error in Target"));

        doAnswer(invocation -> {
            Payload injectedPayload = invocation.getArgument(0);
            ResponseListener callback = injectedPayload.getCallback();
            Executors.newSingleThreadExecutor().submit(() -> callback.error(errorEvent));
            return null;
        }).when(targetIngressHandler).onPayload(any(Payload.class));

        FlowException thrownEx = assertThrows(FlowException.class, () -> vmUpstream.blockingProcessInternal(requestEvent));
        assertEquals(FlowResultStatus.INTERNAL_SERVER_ERROR, thrownEx.getStatus());
    }

    // =========================================================================
    // Intensive Edge Cases
    // =========================================================================

    @Test
    @DisplayName("[Edge 1] Fallback Dispatch: If the target flow's Ingress is not LocalIngress, it calls general dispatch instead of inject.")
    void testFallbackDispatch_TargetNotVmIngress() throws Exception {
        when(upstreamContext.getExchangePattern()).thenReturn(ExchangePattern.FireAndForget);
        vmUpstream = new LocalUpstream(upstreamContext);

        // Change target Ingress to a general Ingress (Mock) instead of LocalIngress
        Ingress genericIngress = mock(Ingress.class);
        when(targetFlowService.getIngress()).thenReturn(genericIngress);

        ZefioMessage requestEvent = new ZefioMessage("Fallback Payload".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        vmUpstream.blockingProcessInternal(requestEvent);

        // Since it cannot pass LocalIngress.inject(), verify that the fallback logic targetFlow.dispatch is called once
        verify(targetFlowService, times(1)).dispatch(any(Payload.class));
    }

    @Test
    @DisplayName("[Edge 2] Thread Interrupt: An INTERRUPTED exception occurs immediately if the thread is interrupted during Two-Way waiting due to system shutdown, etc.")
    void testTwoWayCall_Interrupted() throws Exception {
        when(upstreamContext.getExchangePattern()).thenReturn(ExchangePattern.RequestReply);
        vmUpstream = new LocalUpstream(upstreamContext);

        ZefioMessage requestEvent = new ZefioMessage("Interrupt Test".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        // Target flow waits infinitely (no response)
        doNothing().when(targetIngressHandler).onPayload(any(Payload.class));

        java.util.concurrent.atomic.AtomicReference<Exception> caughtException = new java.util.concurrent.atomic.AtomicReference<>();

        Thread taskThread = new Thread(() -> {
            try {
                vmUpstream.blockingProcessInternal(requestEvent);
            } catch (Exception e) {
                caughtException.set(e);
            }
        });

        taskThread.start();
        Thread.sleep(100);      // Wait 100ms for the thread to enter blocking (get) state
        taskThread.interrupt(); // Force interrupt like a system shutdown signal
        taskThread.join();

        Exception cause = caughtException.get();
        assertNotNull(cause, "Exception should be caught.");
        assertTrue(cause instanceof FlowException);
        assertEquals(FlowResultStatus.INTERRUPTED, ((FlowException) cause).getStatus(), "Interrupt should be converted to FlowException(INTERRUPTED).");
    }

    @Test
    @DisplayName("[Edge 3] One-Way Error Isolation: In OneWay mode, even if the target flow emits an error callback, it is not thrown to the caller and the wrapper object quietly processes the STAT log.")
    void testOneWayCall_ErrorCallbackIsolation() throws Exception {
        when(upstreamContext.getExchangePattern()).thenReturn(ExchangePattern.FireAndForget);
        vmUpstream = new LocalUpstream(upstreamContext);

        // Recreate LocalIngress in One-Way mode so that ResponseOneWayListener is set
        when(ingressContext.getExchangePattern()).thenReturn(ExchangePattern.FireAndForget);
        localIngress = new LocalIngress(ingressContext) {
            @Override
            public ModuleMetricsAggregator getMetricsAggregator() {
                return mockMetricsAggregator;
            }
        };
        localIngress.receive(targetIngressHandler);
        when(targetFlowService.getIngress()).thenReturn(localIngress); // Reconnect to the changed Ingress

        ZefioMessage requestEvent = new ZefioMessage("OneWay Error Isolation".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        // Call terminates normally immediately
        vmUpstream.blockingProcessInternal(requestEvent);

        ArgumentCaptor<Payload> eventCaptor = ArgumentCaptor.forClass(Payload.class);
        verify(targetIngressHandler, times(1)).onPayload(eventCaptor.capture());

        Payload injectedPayload = eventCaptor.getValue();
        ResponseListener wrapperCallback = injectedPayload.getCallback();

        // Assume the target flow emitted an error in the background
        Payload errorPayload = new ZefioMessage(null, StandardCharsets.UTF_8);
        errorPayload.setThrowable(new RuntimeException("Background Target Error"));

        // Perfect One-Way isolation: Calling the error callback should not leak (throw) the exception outside
        assertDoesNotThrow(() -> wrapperCallback.error(errorPayload));

        // Verify if ResponseOneWayListener inside LocalIngress successfully incremented the failure count (Passed!)
        verify(mockMetricsAggregator, atLeastOnce()).incrementPayloadFailedCount();
    }

    @Test
    @DisplayName("[Edge 4] Unknown Exception Conversion: If a general RuntimeException occurs in the target flow instead of FlowException, it is safely wrapped/converted.")
    void testTwoWayCall_UnknownExceptionWrapping() throws Exception {
        when(upstreamContext.getExchangePattern()).thenReturn(ExchangePattern.RequestReply);
        vmUpstream = new LocalUpstream(upstreamContext);

        ZefioMessage requestEvent = new ZefioMessage("Raw Exception Test".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        doAnswer(invocation -> {
            Payload injectedPayload = invocation.getArgument(0);
            ResponseListener callback = injectedPayload.getCallback();

            Payload errorPayload = mock(Payload.class);
            // Simulate a raw NullPointerException caused by a business developer's mistake
            when(errorPayload.getThrowable()).thenReturn(new NullPointerException("Null object referenced in User Filter"));

            Executors.newSingleThreadExecutor().submit(() -> callback.error(errorPayload));
            return null;
        }).when(targetIngressHandler).onPayload(any(Payload.class));

        FlowException thrownEx = assertThrows(FlowException.class, () -> vmUpstream.blockingProcessInternal(requestEvent));

        // Since it went through FlowErrorUtils.convert(), the exception must be wrapped or the original message preserved
        assertTrue(thrownEx.getMessage().contains("Null object referenced in User Filter")
                        || thrownEx.getCause() instanceof NullPointerException,
                "The original cause must be preserved so that unknown errors can be traced.");
    }
}
