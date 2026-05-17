package io.zefio.core.system;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.beans.FlowSyncBridge;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ZefioMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlowSyncIntegrationTest {

    @Mock private PluginContext waitContext;
    @Mock private PluginContext notifyContext;
    @Mock private FlowSyncBridge syncBridge;

    private AsyncSuspendInterceptor waitFilter;
    private AsyncResumeInterceptor notifyFilter;

    // Real thread pool for asynchronous execution and timers
    private ScheduledExecutorService scheduledPool;
    private ExecutorService workerPool;

    // Internal storage for Mock bridge operations
    private Map<String, CompletableFuture<Payload>> mockBridgeMap;

    @BeforeEach
    void setUp() {
        MDC.clear();
        scheduledPool = Executors.newScheduledThreadPool(1);
        workerPool = Executors.newFixedThreadPool(2);
        mockBridgeMap = new ConcurrentHashMap<>();

        // Define Mock FlowSyncBridge behavior
        // 1. register (returns value)
        when(syncBridge.register(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            CompletableFuture<Payload> future = new CompletableFuture<>();
            mockBridgeMap.put(key, future);
            return future;
        });

        // 2. complete (returns value)
        when(syncBridge.complete(anyString(), any(Payload.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            Payload evt = inv.getArgument(1);
            CompletableFuture<Payload> future = mockBridgeMap.remove(key);
            if (future != null) {
                future.complete(evt);
                return true;
            }
            return false;
        });

        // 3. remove (void method -> use doAnswer)
        doAnswer(inv -> {
            mockBridgeMap.remove((String) inv.getArgument(0));
            return null;
        }).when(syncBridge).remove(anyString());

        // 4. completeExceptionally (error notification)
        when(syncBridge.completeExceptionally(anyString(), any(Throwable.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            Throwable ex = inv.getArgument(1);
            CompletableFuture<Payload> future = mockBridgeMap.remove(key);
            if (future != null) {
                return future.completeExceptionally(ex);
            }
            return false;
        });

        // Common Context Mock configuration
        when(waitContext.getFlowName()).thenReturn("upstream-flow");
        when(waitContext.getPluginName()).thenReturn("syncWait");
        when(waitContext.getFlowSyncBridge()).thenReturn(syncBridge);
        when(waitContext.getSharedScheduledPool()).thenReturn(scheduledPool);

        when(notifyContext.getFlowName()).thenReturn("ingress-flow");
        when(notifyContext.getPluginName()).thenReturn("syncNotify");
        when(notifyContext.getFlowSyncBridge()).thenReturn(syncBridge);
    }

    @AfterEach
    void tearDown() {
        scheduledPool.shutdownNow();
        workerPool.shutdownNow();
        mockBridgeMap.clear();
        MDC.clear();
    }

    private void initFilters(Map<String, Object> waitYaml, Map<String, Object> notifyYaml) {
        when(waitContext.getContext()).thenReturn(waitYaml != null ? waitYaml : new HashMap<>());
        when(notifyContext.getContext()).thenReturn(notifyYaml != null ? notifyYaml : new HashMap<>());

        waitFilter = new AsyncSuspendInterceptor(waitContext);
        notifyFilter = new AsyncResumeInterceptor(notifyContext);
    }

    @Test
    @DisplayName("Success Case: If Notify filter alerts while Wait filter is waiting, the response is merged into the original event and returned.")
    void testSyncWaitAndNotify_Success() throws Exception {
        // given
        Map<String, Object> waitYaml = new HashMap<>();
        waitYaml.put("timeout", 3000L); // wait for 3 seconds
        initFilters(waitYaml, null);

        ZefioMessage requestEvent = new ZefioMessage("Request".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        requestEvent.setTrxID("TRX-001");

        // when (Wait starts waiting - Non-blocking so it returns Future immediately)
        CompletableFuture<Payload> waitFuture = waitFilter.executeAsync(requestEvent, workerPool);

        // Assume an asynchronous response arrives from an external system and execute Notify filter
        ZefioMessage responseEvent = new ZefioMessage("Async Response".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        responseEvent.setTrxID("TRX-001"); // Same matching key

        notifyFilter.process(responseEvent);

        // then (Wait for WaitFuture to complete and verify the result)
        Payload finalResult = waitFuture.get(1, TimeUnit.SECONDS);

        assertNotNull(finalResult);
        assertEquals("Async Response", new String(finalResult.getBody(), StandardCharsets.UTF_8), "The response body should be merged into the original event");
        assertTrue(mockBridgeMap.isEmpty(), "The key should be removed from the bridge memory after processing");
    }

    @Test
    @DisplayName("Timeout Case: A TIMEOUT exception occurs if no response arrives within the configured time.")
    void testSyncWait_Timeout() {
        // given
        Map<String, Object> waitYaml = new HashMap<>();
        waitYaml.put("timeout", 100L); // set timeout very short to 100ms
        initFilters(waitYaml, null);

        ZefioMessage requestEvent = new ZefioMessage("Request".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        requestEvent.setTrxID("TRX-TIMEOUT");

        // when
        CompletableFuture<Payload> waitFuture = waitFilter.executeAsync(requestEvent, workerPool);

        // then (wait without calling Notify)
        CompletionException thrown = assertThrows(CompletionException.class, waitFuture::join);
        FlowException cause = (FlowException) thrown.getCause();

        assertEquals(FlowResultStatus.TIMEOUT, cause.getStatus());
        assertTrue(mockBridgeMap.isEmpty(), "Bridge memory cleanup (remove) should be performed even on timeout");
    }

    @Test
    @DisplayName("Custom Key Success Case: Successfully matches with the property value set by bridgeKeyProperty.")
    void testCustomBridgeKey_Success() throws Exception {
        // given
        Map<String, Object> customYaml = new HashMap<>();
        customYaml.put("bridgeKeyProperty", "MY_MSG_ID");
        customYaml.put("timeout", 3000L);
        initFilters(customYaml, customYaml); // Set the same key for both Wait and Notify

        ZefioMessage requestEvent = new ZefioMessage("Req".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        requestEvent.setHeader("MY_MSG_ID", "CUSTOM-KEY-123");

        CompletableFuture<Payload> waitFuture = waitFilter.executeAsync(requestEvent, workerPool);

        ZefioMessage responseEvent = new ZefioMessage("Res".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        responseEvent.setHeader("MY_MSG_ID", "CUSTOM-KEY-123");

        // when
        notifyFilter.process(responseEvent);

        // then
        Payload finalResult = waitFuture.get(1, TimeUnit.SECONDS);
        assertEquals("Res", new String(finalResult.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Missing Key Error: NOT_CORRELATION_KEY exception occurs if the custom key does not exist in the event properties.")
    void testWaitFilter_MissingKey() {
        // given
        Map<String, Object> waitYaml = new HashMap<>();
        waitYaml.put("bridgeKeyProperty", "MISSING_KEY");
        initFilters(waitYaml, null);

        ZefioMessage requestEvent = new ZefioMessage("Req".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        // Intentionally omit setting the "MISSING_KEY" property

        // when
        CompletableFuture<Payload> waitFuture = waitFilter.executeAsync(requestEvent, workerPool);

        // then
        CompletionException thrown = assertThrows(CompletionException.class, waitFuture::join);
        FlowException cause = (FlowException) thrown.getCause();

        assertEquals(FlowResultStatus.NOT_CORRELATION_KEY, cause.getStatus());
    }

    @Test
    @DisplayName("Blank Mandatory Key Error: NOT_CORRELATION_KEY exception occurs if the default key (TrxID) is null or blank.")
    void testSyncWait_BlankTrxId() {
        // given
        initFilters(null, null);
        ZefioMessage requestEvent = new ZefioMessage("Req".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        requestEvent.setTrxID("   "); // Intentionally set to blank

        // when
        CompletableFuture<Payload> waitFuture = waitFilter.executeAsync(requestEvent, workerPool);

        // then
        CompletionException thrown = assertThrows(CompletionException.class, waitFuture::join);
        FlowException cause = (FlowException) thrown.getCause();

        assertEquals(FlowResultStatus.NOT_CORRELATION_KEY, cause.getStatus());
        assertTrue(cause.getMessage().contains("blank"));
    }

    @Test
    @DisplayName("Bridge Capacity Exceeded: QUEUE_CAPACITY_EXCEEDED error occurs if FlowSyncBridge is full and rejects registration.")
    void testSyncWait_CapacityExceeded() {
        // given
        initFilters(null, null);
        ZefioMessage requestEvent = new ZefioMessage("Req".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        requestEvent.setTrxID("FULL-TRX");

        // Force mocking to return a failed Future assuming the bridge is full
        CompletableFuture<Payload> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new FlowException(FlowResultStatus.QUEUE_CAPACITY_EXCEEDED, "System Busy: Bridge Capacity Exceeded"));
        when(syncBridge.register("FULL-TRX")).thenReturn(failedFuture);

        // when
        CompletableFuture<Payload> waitFuture = waitFilter.executeAsync(requestEvent, workerPool);

        // then
        CompletionException thrown = assertThrows(CompletionException.class, waitFuture::join);
        FlowException cause = (FlowException) thrown.getCause();

        assertEquals(FlowResultStatus.QUEUE_CAPACITY_EXCEEDED, cause.getStatus(), "The error status must be propagated exactly when bridge registration is rejected");
    }

    @Test
    @DisplayName("Orphan Response Case: A response with no waiting target (Ghost) is skipped without throwing an error.")
    void testNotifyFilter_GhostResponse() throws Exception {
        // given
        initFilters(null, null);

        ZefioMessage ghostEvent = new ZefioMessage("Ghost".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        ghostEvent.setTrxID("UNKNOWN-TRX");

        // Process Notify immediately without calling the waiting Wait filter (register)
        // when
        Payload result = notifyFilter.process(ghostEvent);

        // then
        assertNotNull(result); // It should flow the original event without throwing an error
        assertEquals("Ghost", new String(result.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Ignore Invalid Notify Data: Skips without error if a custom key is specified but the corresponding property in the arrived response is blank.")
    void testNotifyFilter_BlankCustomKey() throws Exception {
        // given
        Map<String, Object> notifyYaml = new HashMap<>();
        notifyYaml.put("bridgeKeyProperty", "MY_MSG_ID");
        initFilters(null, notifyYaml);

        ZefioMessage responseEvent = new ZefioMessage("Res".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        responseEvent.setTrxID("TRX-001");
        responseEvent.setHeader("MY_MSG_ID", "   "); // The set custom key value is blank

        // when (Attempts Notify processing but should skip because the key is blank)
        Payload result = notifyFilter.process(responseEvent);

        // then
        assertNotNull(result);
        assertEquals("Res", new String(result.getBody(), StandardCharsets.UTF_8));
        // Verify that bridge complete is never called (0 interactions)
        verify(syncBridge, never()).complete(anyString(), any(Payload.class));
    }

    @Test
    @DisplayName("External Forced Error (Eviction/Shutdown): Instantly wakes up if the bridge throws an exception due to forced system shutdown etc. while waiting.")
    void testSyncWait_ExternalException() {
        // given
        initFilters(null, null);
        ZefioMessage requestEvent = new ZefioMessage("Req".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        requestEvent.setTrxID("EVICT-TRX");

        // Enter Wait
        CompletableFuture<Payload> waitFuture = waitFilter.executeAsync(requestEvent, workerPool);

        // when (Before timeout or normal response arrives, the bridge sends a forced error notification due to TTL expiration or shutdown)
        syncBridge.completeExceptionally("EVICT-TRX", new FlowException(FlowResultStatus.SYNC_BRIDGE_EXPIRED, "Bridge Evicted"));

        // then
        CompletionException thrown = assertThrows(CompletionException.class, waitFuture::join);
        FlowException cause = (FlowException) thrown.getCause();

        // Verify that the wrapped error is properly unwrapped and passed to the core engine
        assertEquals(FlowResultStatus.SYNC_BRIDGE_EXPIRED, cause.getStatus());
        assertEquals("Bridge Evicted", cause.getMessage());
    }
}
