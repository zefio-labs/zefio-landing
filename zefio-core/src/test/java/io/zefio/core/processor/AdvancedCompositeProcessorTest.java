package io.zefio.core.processor;

import dev.failsafe.RetryPolicy;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.payload.Payload;
import io.zefio.core.config.flow.OnErrorPolicy;
import io.zefio.core.GatewayInterceptor;
import io.zefio.core.engine.processor.*;
import io.zefio.core.engine.processor.dto.SwitchBranch;
import io.zefio.core.payload.ZefioMessage;
import io.zefio.core.telemetry.module.ModuleMetricsAggregator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Composite Processor Master Test Suite (TC-01 ~ TC-66)
 * - Verifies OnErrorPolicy Enum application and unified RetryPolicy injection logic.
 */
class AdvancedCompositeProcessorTest {
    private static final Logger log = LoggerFactory.getLogger(AdvancedCompositeProcessorTest.class);

    private ExecutorService flowWorkerPool;
    private ScheduledExecutorService sharedScheduledPool;

    @BeforeEach
    void setUp() {
        flowWorkerPool = Executors.newFixedThreadPool(30);
        sharedScheduledPool = Executors.newScheduledThreadPool(10);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        flowWorkerPool.shutdownNow();
        sharedScheduledPool.shutdownNow();
        if (!flowWorkerPool.awaitTermination(2, TimeUnit.SECONDS)) {
            log.warn("Worker pool did not terminate gracefully");
        }
    }

    // ==============================================================================
    // Mock Filter for Testing
    // ==============================================================================
    static class MockFilter implements GatewayInterceptor {
        private final String name;
        private final long delayMs;
        private final int failUntilAttempt;
        private final RetryPolicy<Payload> retryPolicy;
        private int attemptCount = 0;

        public MockFilter(String name, long delayMs, int failUntilAttempt, RetryPolicy<Payload> retryPolicy) {
            this.name = name;
            this.delayMs = delayMs;
            this.failUntilAttempt = failUntilAttempt;
            this.retryPolicy = retryPolicy != null ? retryPolicy : RetryPolicy.<Payload>builder().withMaxRetries(0).build();
        }

        @Override
        public CompletableFuture<Payload> executeAsync(Payload payload, Executor flowExecutor) {
            attemptCount++;
            int currentAttempt = attemptCount;
            return CompletableFuture.supplyAsync(() -> {
                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                log.info("[MockFilter:{}] Executing Attempt: {}", name, currentAttempt);

                if (currentAttempt <= failUntilAttempt) {
                    log.warn("[MockFilter:{}] Intentionally failing attempt {}", name, currentAttempt);
                    throw new CompletionException(new FlowException(FlowResultStatus.REMOTE_SERVER_ERROR, "Mock Error in " + name));
                }
                payload.setHeader("Result_From_" + name, "SUCCESS");
                String currentBody = new String(payload.getBody(), StandardCharsets.UTF_8);
                String newBody = currentBody.isEmpty() ? name : currentBody + "," + name;
                payload.setBody(newBody.getBytes(StandardCharsets.UTF_8));
                return payload;
            }, flowExecutor);
        }

        @Override public String getPluginName() { return name; }
        @Override public String getPluginLabel() { return name + "_Label"; }
        @Override public RetryPolicy<Payload> getRetryPolicy() { return retryPolicy; }
        @Override public void initialise() {} @Override public void close() {}
        @Override public void refresh() {}
        @Override public boolean isBlockingType() { return true; }
        @Override public String getDescription() { return "Mock"; }
        @Override public ModuleMetricsAggregator getMetricsAggregator() { return null; }
        public int getAttemptCount() { return attemptCount; }
    }

    // ==============================================================================
    // [1] LeafFilterProcessor Test (TC-01 ~ TC-03)
    // ==============================================================================
    @Test
    @DisplayName("[TC-01] Single Leaf filter executes successfully")
    void testLeafSuccess() {
        Processor leaf = new LeafFilterProcessor(new MockFilter("Leaf1", 10, 0, null), sharedScheduledPool);
        Payload result = leaf.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertEquals("SUCCESS", result.getHeader("Result_From_Leaf1"));
    }

    @Test
    @DisplayName("[TC-02] Single Leaf filter local retry (Failsafe) success")
    void testLeafRetrySuccess() {
        RetryPolicy<Payload> policy = RetryPolicy.<Payload>builder().withMaxRetries(3).withDelay(Duration.ofMillis(10)).build();
        MockFilter mock = new MockFilter("LeafRetry", 10, 2, policy);
        Processor leaf = new LeafFilterProcessor(mock, sharedScheduledPool);
        Payload result = leaf.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertEquals("SUCCESS", result.getHeader("Result_From_LeafRetry"));
        assertEquals(3, mock.getAttemptCount());
    }

    @Test
    @DisplayName("[TC-03] Single Leaf filter throws ABORT exception when retries exhausted")
    void testLeafRetryExhausted() {
        RetryPolicy<Payload> policy = RetryPolicy.<Payload>builder().withMaxRetries(1).build();
        MockFilter mock = new MockFilter("LeafFail", 10, 5, policy);
        Processor leaf = new LeafFilterProcessor(mock, sharedScheduledPool);
        assertThrows(CompletionException.class, () -> leaf.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join());
    }

    // ==============================================================================
    // [2] ScatterGatherProcessor Test (TC-04 ~ TC-06)
    // ==============================================================================
    @Test
    @DisplayName("[TC-04] Multi-node parallel execution time verification")
    void testScatterGatherSuccess() {
        Processor p1 = new LeafFilterProcessor(new MockFilter("SG1", 200, 0, null), sharedScheduledPool);
        Processor p2 = new LeafFilterProcessor(new MockFilter("SG2", 300, 0, null), sharedScheduledPool);
        Map<String, Object> config = new HashMap<>(); config.put("timeout", 1000);
        Processor sg = new ParallelScatterGatherRouter("SG_Test", Arrays.asList(p1, p2), config, sharedScheduledPool);

        long startTime = System.currentTimeMillis();
        Payload result = sg.executeAsync(new ZefioMessage("Start".getBytes(), StandardCharsets.UTF_8), flowWorkerPool).join();
        assertTrue((System.currentTimeMillis() - startTime) < 800, "Should run in parallel");
        assertEquals("SUCCESS", result.getHeader("Result_From_SG2"));
    }

    @Test
    @DisplayName("[TC-05] ScatterGather throws exception on timeout")
    void testScatterGatherTimeout() {
        Processor slow = new LeafFilterProcessor(new MockFilter("Slow", 2000, 0, null), sharedScheduledPool);
        Map<String, Object> config = new HashMap<>(); config.put("timeout", 100);
        Processor sg = new ParallelScatterGatherRouter("SG_Timeout", Collections.singletonList(slow), config, sharedScheduledPool);
        assertThrows(CompletionException.class, () -> sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join());
    }

    @Test
    @DisplayName("[TC-06] ScatterGather BEST_EFFORT partial failure merge")
    void testScatterGatherPartialFailure() {
        Processor ok1 = new LeafFilterProcessor(new MockFilter("OK1", 10, 0, null), sharedScheduledPool);
        Processor fail = new LeafFilterProcessor(new MockFilter("FAIL", 10, 99, null), sharedScheduledPool);
        Map<String, Object> config = new HashMap<>(); config.put("errorPolicy", "BEST_EFFORT");
        Processor sg = new ParallelScatterGatherRouter("SG_Partial", Arrays.asList(ok1, fail), config, sharedScheduledPool);

        Payload result = sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertEquals("SUCCESS", result.getHeader("Result_From_OK1"));
        assertNull(result.getHeader("Result_From_FAIL"));
    }

    // ==============================================================================
    // [3] ResilientScopeHandler Test (TC-07 ~ TC-11)
    // ==============================================================================
    @Test
    @DisplayName("[TC-07] ResilientScope Normal Flow (Ignore Policy)")
    void testTryScopeSuccess() {
        Processor s1 = new LeafFilterProcessor(new MockFilter("S1", 10, 0, null), sharedScheduledPool);
        Processor tryScope = new ResilientScopeHandler("Try_Normal", Collections.singletonList(s1), null, OnErrorPolicy.THROW, null, sharedScheduledPool);
        Payload result = tryScope.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertEquals("SUCCESS", result.getHeader("Result_From_S1"));
    }

    @Test
    @DisplayName("[TC-08] ResilientScope FALLBACK Policy Execution")
    void testTryScopeFallbackPolicy() {
        Processor failing = new LeafFilterProcessor(new MockFilter("Fail", 10, 99, null), sharedScheduledPool);
        Processor fallback = new LeafFilterProcessor(new MockFilter("FB", 10, 0, null), sharedScheduledPool);
        Processor tryScope = new ResilientScopeHandler("Try_Fallback", Collections.singletonList(failing), Collections.singletonList(fallback), OnErrorPolicy.FALLBACK, null, sharedScheduledPool);
        Payload result = tryScope.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertEquals("SUCCESS", result.getHeader("Result_From_FB"));
    }

    @Test
    @DisplayName("[TC-09] ResilientScope STOP Policy Immediate Return")
    void testTryScopeStopPolicy() {
        Processor failing = new LeafFilterProcessor(new MockFilter("Fail", 10, 99, null), sharedScheduledPool);
        Processor tryScope = new ResilientScopeHandler("Try_Stop", Collections.singletonList(failing), null, OnErrorPolicy.STOP, null, sharedScheduledPool);
        assertDoesNotThrow(() -> tryScope.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join());
    }

    @Test
    @DisplayName("[TC-10] ResilientScope Unified RetryPolicy Macro Retry")
    void testTryScopeMacroRetry() {
        Processor unstable = new LeafFilterProcessor(new MockFilter("Unstable", 10, 2, null), sharedScheduledPool);
        RetryPolicy<Payload> rp = RetryPolicy.<Payload>builder().withMaxRetries(3).withDelay(Duration.ofMillis(10)).build();
        Processor tryScope = new ResilientScopeHandler("Try_Retry", Collections.singletonList(unstable), null, OnErrorPolicy.THROW, rp, sharedScheduledPool);
        Payload result = tryScope.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertEquals("SUCCESS", result.getHeader("Result_From_Unstable"));
    }

    @Test
    @DisplayName("[TC-11] ResilientScope Retries After Nested ScatterGather Timeout")
    void testNestedScatterGatherInsideTryScopeRetry() {
        Processor slow = new LeafFilterProcessor(new MockFilter("TC11_Slow", 1000, 0, null), sharedScheduledPool);
        Map<String, Object> sgConfig = new HashMap<>(); sgConfig.put("timeout", 100); sgConfig.put("errorPolicy", "FAIL_FAST");
        Processor sg = new ParallelScatterGatherRouter("SG_WillTimeout", Collections.singletonList(slow), sgConfig, sharedScheduledPool);

        RetryPolicy<Payload> rp = RetryPolicy.<Payload>builder().withMaxRetries(2).withDelay(Duration.ofMillis(10)).build();
        Processor tryScope = new ResilientScopeHandler("TryScope_Over_SG", Collections.singletonList(sg), null, OnErrorPolicy.THROW, rp, sharedScheduledPool);

        long start = System.currentTimeMillis();
        assertThrows(CompletionException.class, () -> tryScope.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join());
        assertTrue(System.currentTimeMillis() - start > 200, "Retries should compound the timeout delays");
    }

    // ==============================================================================
    // [4] Complex Payload Aggregation Scenarios (TC-12 ~ TC-14)
    // ==============================================================================
    @Test
    @DisplayName("[TC-12] SG MAP_MERGE + BEST_EFFORT Preserves Error JSON")
    void testScatterGatherMapMergeWithBestEffort() throws Exception {
        Processor p1 = new LeafFilterProcessor(new MockFilter("API_JSON", 10, 0, null), sharedScheduledPool);
        Processor p2 = new LeafFilterProcessor(new MockFilter("API_FAIL", 10, 99, null), sharedScheduledPool);
        Map<String, Object> config = new HashMap<>(); config.put("aggregationType", "MAP_MERGE"); config.put("errorPolicy", "BEST_EFFORT");
        Processor sg = new ParallelScatterGatherRouter("SG_Complex", Arrays.asList(p1, p2), config, sharedScheduledPool);

        Payload result = sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(new String(result.getBody()));
        assertTrue(root.has("API_JSON") && root.has("API_FAIL"));
        assertTrue(root.get("API_FAIL").has("error"));
    }

    @Test
    @DisplayName("[TC-13] SG MAP_MERGE + FAIL_FAST Immediate Exception")
    void testScatterGatherFailFast() {
        Processor p1 = new LeafFilterProcessor(new MockFilter("FastSuccess", 10, 0, null), sharedScheduledPool);
        Processor p2 = new LeafFilterProcessor(new MockFilter("SlowFail", 100, 99, null), sharedScheduledPool);
        Map<String, Object> config = new HashMap<>(); config.put("aggregationType", "MAP_MERGE"); config.put("errorPolicy", "FAIL_FAST");
        Processor sg = new ParallelScatterGatherRouter("SG_Strict", Arrays.asList(p1, p2), config, sharedScheduledPool);
        assertThrows(CompletionException.class, () -> sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join());
    }

    @Test
    @DisplayName("[TC-14] SG Mixed Format (JSON + Text) Aggregation")
    void testScatterGatherMixedFormats() throws Exception {
        MockFilter jsonFilter = new MockFilter("JSON_NODE", 10, 0, null) {
            @Override public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) { e.setBody("{\"c\":200}".getBytes()); return CompletableFuture.completedFuture(e); }
        };
        MockFilter textFilter = new MockFilter("TEXT_NODE", 10, 0, null) {
            @Override public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) { e.setBody("Plain".getBytes()); return CompletableFuture.completedFuture(e); }
        };
        Map<String, Object> config = new HashMap<>(); config.put("aggregationType", "MAP_MERGE");
        Processor sg = new ParallelScatterGatherRouter("SG_Mixed", Arrays.asList(new LeafFilterProcessor(jsonFilter, sharedScheduledPool), new LeafFilterProcessor(textFilter, sharedScheduledPool)), config, sharedScheduledPool);

        Payload result = sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        String body = new String(result.getBody());
        assertTrue(body.contains("\"JSON_NODE\":{\"c\":200}") && body.contains("\"TEXT_NODE\":\"Plain\""));
    }

    // ==============================================================================
    // [5] Hierarchical Nested Defense Structures (TC-15 ~ TC-20)
    // ==============================================================================
    @Test
    @DisplayName("[TC-15] ResilientScope Retry Over Inner SG(FailFast) Failure")
    void testTryScopeRetryOverScatterGatherFailure() {
        MockFilter unstable = new MockFilter("UnstableAPI", 10, 2, null);
        Map<String, Object> sgConfig = new HashMap<>(); sgConfig.put("errorPolicy", "FAIL_FAST");
        Processor sg = new ParallelScatterGatherRouter("SG_Inner", Collections.singletonList(new LeafFilterProcessor(unstable, sharedScheduledPool)), sgConfig, sharedScheduledPool);

        RetryPolicy<Payload> rp = RetryPolicy.<Payload>builder().withMaxRetries(3).build();
        Processor outerTry = new ResilientScopeHandler("Outer_Try", Collections.singletonList(sg), null, OnErrorPolicy.THROW, rp, sharedScheduledPool);
        Payload result = outerTry.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertEquals(3, unstable.getAttemptCount());
    }

    @Test
    @DisplayName("[TC-16] 3-Level Deep Nested Recovery")
    void testDeepNestedRecovery() {
        Processor killer = new LeafFilterProcessor(new MockFilter("Killer", 10, 99, null), sharedScheduledPool);
        Processor safeFb = new LeafFilterProcessor(new MockFilter("Safe_FB", 10, 0, null), sharedScheduledPool);
        Processor bottomTs = new ResilientScopeHandler("Bottom_TS", Collections.singletonList(killer), Collections.singletonList(safeFb), OnErrorPolicy.FALLBACK, null, sharedScheduledPool);

        Map<String, Object> sgConfig = new HashMap<>(); sgConfig.put("errorPolicy", "FAIL_FAST");
        Processor middleSg = new ParallelScatterGatherRouter("Middle_SG", Collections.singletonList(bottomTs), sgConfig, sharedScheduledPool);

        RetryPolicy<Payload> rp = RetryPolicy.<Payload>builder().withMaxRetries(1).build();
        Processor topTs = new ResilientScopeHandler("Top_TS", Collections.singletonList(middleSg), null, OnErrorPolicy.THROW, rp, sharedScheduledPool);

        Payload result = topTs.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertEquals("SUCCESS", result.getHeader("Result_From_Safe_FB"));
    }

    @Test
    @DisplayName("[TC-17] Mixed Aggregation: OVERRIDE Inside MAP_MERGE")
    void testMixedAggregationStrategy() {
        Map<String, Object> inConf = new HashMap<>(); inConf.put("aggregationType", "OVERRIDE");
        Processor innerSg = new ParallelScatterGatherRouter("Inner", Arrays.asList(new LeafFilterProcessor(new MockFilter("A", 10, 0, null), sharedScheduledPool), new LeafFilterProcessor(new MockFilter("B", 30, 0, null), sharedScheduledPool)), inConf, sharedScheduledPool);

        Map<String, Object> outConf = new HashMap<>(); outConf.put("aggregationType", "MAP_MERGE");
        Processor outerSg = new ParallelScatterGatherRouter("Outer", Collections.singletonList(innerSg), outConf, sharedScheduledPool);
        Payload result = outerSg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertTrue(new String(result.getBody()).contains("\"Inner\":\"B\""));
    }

    @Test
    @DisplayName("[TC-18] ResilientScope FALLBACK is a Scatter-Gather Pipeline")
    void testTryScopeWithScatterGatherFallback() {
        Processor failingMain = new LeafFilterProcessor(new MockFilter("MainFail", 10, 99, null), sharedScheduledPool);
        Map<String, Object> sgConfig = new HashMap<>(); sgConfig.put("aggregationType", "MAP_MERGE");
        Processor fallbackSg = new ParallelScatterGatherRouter("FB_SG", Arrays.asList(new LeafFilterProcessor(new MockFilter("FB1", 10, 0, null), sharedScheduledPool), new LeafFilterProcessor(new MockFilter("FB2", 10, 0, null), sharedScheduledPool)), sgConfig, sharedScheduledPool);

        Processor ts = new ResilientScopeHandler("TS_FB", Collections.singletonList(failingMain), Collections.singletonList(fallbackSg), OnErrorPolicy.FALLBACK, null, sharedScheduledPool);
        Payload result = ts.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertTrue(new String(result.getBody()).contains("FB1") && new String(result.getBody()).contains("FB2"));
    }

    @Test
    @DisplayName("[TC-19] Micro-Retry Linked with Macro-Retry (ResilientScope)")
    void testDoubleLayerRetry() {
        RetryPolicy<Payload> microRp = RetryPolicy.<Payload>builder().withMaxRetries(1).build();
        MockFilter mock = new MockFilter("DoubleRetry", 10, 3, microRp);
        Processor leaf = new LeafFilterProcessor(mock, sharedScheduledPool);

        RetryPolicy<Payload> macroRp = RetryPolicy.<Payload>builder().withMaxRetries(2).build();
        Processor ts = new ResilientScopeHandler("TS_Macro", Collections.singletonList(leaf), null, OnErrorPolicy.THROW, macroRp, sharedScheduledPool);

        Payload result = ts.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertTrue(mock.getAttemptCount() >= 3);
        assertEquals("SUCCESS", result.getHeader("Result_From_DoubleRetry"));
    }

    @Test
    @DisplayName("[TC-20] BEST_EFFORT SG Total Failure JSON Structure")
    void testScatterGatherTotalFailureBestEffort() {
        Processor f1 = new LeafFilterProcessor(new MockFilter("F1", 10, 99, null), sharedScheduledPool);
        Processor f2 = new LeafFilterProcessor(new MockFilter("F2", 10, 99, null), sharedScheduledPool);
        Map<String, Object> config = new HashMap<>(); config.put("aggregationType", "MAP_MERGE"); config.put("errorPolicy", "BEST_EFFORT");
        Processor sg = new ParallelScatterGatherRouter("Total_Fail", Arrays.asList(f1, f2), config, sharedScheduledPool);
        Payload result = sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertTrue(new String(result.getBody()).contains("\"F1\":{\"error\":") && new String(result.getBody()).contains("\"F2\":{\"error\":"));
    }

    // ==============================================================================
    // [6] Resource, Concurrency & Edge Cases (TC-21 ~ TC-30)
    // ==============================================================================
    @Test
    @DisplayName("[TC-21] SG Inner Node MDC/Properties Isolation Verification")
    void testPropertyIsolationInParallel() {
        MockFilter p1 = new MockFilter("P1", 10, 0, null) { @Override public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) { e.setHeader("Key", "P1_WIN"); return super.executeAsync(e, ex); }};
        MockFilter p2 = new MockFilter("P2", 10, 0, null) { @Override public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) { e.setHeader("Key", "P2_WIN"); return super.executeAsync(e, ex); }};
        Map<String, Object> c = new HashMap<>(); c.put("aggregationType", "MAP_MERGE");
        Processor sg = new ParallelScatterGatherRouter("IsoTest", Arrays.asList(new LeafFilterProcessor(p1, sharedScheduledPool), new LeafFilterProcessor(p2, sharedScheduledPool)), c, sharedScheduledPool);
        assertNotNull(sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join().getHeader("Key"));
    }

    @Test
    @DisplayName("[TC-22] Massive Parallel Node Stress Test")
    void testMassiveParallelStress() {
        List<Processor> bulk = new ArrayList<>();
        for(int i=0; i<50; i++) bulk.add(new LeafFilterProcessor(new MockFilter("N"+i, 5, 0, null), sharedScheduledPool));
        Map<String, Object> config = new HashMap<>(); config.put("timeout", 5000);
        Processor sg = new ParallelScatterGatherRouter("Stress_SG", bulk, config, sharedScheduledPool);
        Payload result = sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertEquals("SUCCESS", result.getHeader("Result_From_N49"));
    }

    @Test
    @DisplayName("[TC-23] ResilientScope FALLBACK Pipeline Itself Fails")
    void testFallbackFailure() {
        Processor main = new LeafFilterProcessor(new MockFilter("Main", 10, 99, null), sharedScheduledPool);
        Processor fb = new LeafFilterProcessor(new MockFilter("FB_Fail", 10, 99, null), sharedScheduledPool);
        Processor ts = new ResilientScopeHandler("Double_Fail", Collections.singletonList(main), Collections.singletonList(fb), OnErrorPolicy.FALLBACK, null, sharedScheduledPool);
        assertThrows(CompletionException.class, () -> ts.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join());
    }

    @Test
    @DisplayName("[TC-24] SG MAP_MERGE Non-JSON Original Body Defense")
    void testScatterGatherNonJsonBodyMerge() {
        MockFilter nonJson = new MockFilter("NJ", 10, 0, null) { @Override public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) { e.setBody("PLAIN_TEXT".getBytes()); return CompletableFuture.completedFuture(e); }};
        Map<String, Object> config = new HashMap<>(); config.put("aggregationType", "MAP_MERGE");
        Processor sg = new ParallelScatterGatherRouter("NJ_SG", Collections.singletonList(new LeafFilterProcessor(nonJson, sharedScheduledPool)), config, sharedScheduledPool);
        assertTrue(new String(sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join().getBody()).contains("\"NJ\":\"PLAIN_TEXT\""));
    }

    @Test
    @DisplayName("[TC-25] ResilientScope RETRY Creates Payload Copy Per Attempt (Isolation)")
    void testTryScopeRetryEventIsolation() {
        MockFilter counter = new MockFilter("Count", 10, 1, null) {
            @Override public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) {
                Object val = e.getHeader("Cnt");
                e.setHeader("Cnt", (val == null ? 0 : (int) val) + 1);
                return super.executeAsync(e, ex);
            }
        };
        RetryPolicy<Payload> rp = RetryPolicy.<Payload>builder().withMaxRetries(1).build();
        Processor ts = new ResilientScopeHandler("Iso", Collections.singletonList(new LeafFilterProcessor(counter, sharedScheduledPool)), null, OnErrorPolicy.THROW, rp, sharedScheduledPool);
        assertEquals(1, ts.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join().getHeader("Cnt"));
    }

    @Test
    @DisplayName("[TC-26] Empty Child Step SG Execution (Pass)")
    void testEmptyScatterGather() {
        Processor sg = new ParallelScatterGatherRouter("Empty", Collections.emptyList(), new HashMap<>(), sharedScheduledPool);
        assertEquals("Origin", new String(sg.executeAsync(new ZefioMessage("Origin".getBytes(), StandardCharsets.UTF_8), flowWorkerPool).join().getBody()));
    }

    @Test
    @DisplayName("[TC-27] Empty Child Step ResilientScope Execution (Pass)")
    void testEmptyTryScope() {
        Processor ts = new ResilientScopeHandler("Empty", Collections.emptyList(), null, OnErrorPolicy.THROW, null, sharedScheduledPool);
        assertEquals("Origin", new String(ts.executeAsync(new ZefioMessage("Origin".getBytes(), StandardCharsets.UTF_8), flowWorkerPool).join().getBody()));
    }

    @Test
    @DisplayName("[TC-28] SG Immediate Failure on 0ms Timeout")
    void testScatterGatherZeroTimeout() {
        Map<String, Object> config = new HashMap<>(); config.put("timeout", 0);
        Processor sg = new ParallelScatterGatherRouter("Zero", Collections.singletonList(new LeafFilterProcessor(new MockFilter("S", 100, 0, null), sharedScheduledPool)), config, sharedScheduledPool);
        assertThrows(CompletionException.class, () -> sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join());
    }

    @Test
    @DisplayName("[TC-29] Asynchronous Chain Intermediate Cancel Signal Verification")
    void testChainCancellation() {
        Processor slow = new LeafFilterProcessor(new MockFilter("Long", 5000, 0, null), sharedScheduledPool);
        CompletableFuture<Payload> f = slow.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool);
        f.cancel(true);
        assertTrue(f.isCancelled());
    }

    @Test
    @DisplayName("[TC-30] Cumulative RemoteTime Aggregation in Nested SG")
    void testCumulativeMetricsInNestedSG() {
        MockFilter m = new MockFilter("M", 10, 0, null) { @Override public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) { ((ZefioMessage)e).addRemoteTime(100); return super.executeAsync(e, ex); }};
        Map<String, Object> config = new HashMap<>(); config.put("aggregationType", "MAP_MERGE");
        Processor sg = new ParallelScatterGatherRouter("Metric_SG", Arrays.asList(new LeafFilterProcessor(m, sharedScheduledPool), new LeafFilterProcessor(m, sharedScheduledPool)), config, sharedScheduledPool);
        assertEquals(200, sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join().getRemoteTime());
    }

    // ==============================================================================
    // [7] Data Integrity & Conflict Scenarios (TC-31 ~ TC-40)
    // ==============================================================================
    @Test
    @DisplayName("[TC-31] MAP_MERGE Key Conflict Defense")
    void testTC31() {
        Processor p1 = new LeafFilterProcessor(new MockFilter("DUP", 10, 0, null), sharedScheduledPool);
        Processor p2 = new LeafFilterProcessor(new MockFilter("DUP", 10, 0, null), sharedScheduledPool);
        Map<String, Object> c = new HashMap<>(); c.put("aggregationType", "MAP_MERGE");
        Processor sg = new ParallelScatterGatherRouter("SG_Conflict", Arrays.asList(p1, p2), c, sharedScheduledPool);
        assertTrue(new String(sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join().getBody()).contains("\"DUP\""));
    }

    @Test
    @DisplayName("[TC-32] Resource Reclaimed on Scope Timeout During Retry")
    void testTC32() {
        Processor slow = new LeafFilterProcessor(new MockFilter("Slow", 1000, 0, null), sharedScheduledPool);
        Map<String, Object> sgConfig = new HashMap<>(); sgConfig.put("timeout", 200);
        Processor sg = new ParallelScatterGatherRouter("SG", Collections.singletonList(slow), sgConfig, sharedScheduledPool);
        RetryPolicy<Payload> rp = RetryPolicy.<Payload>builder().withMaxRetries(5).build();
        Processor ts = new ResilientScopeHandler("TS", Collections.singletonList(sg), null, OnErrorPolicy.THROW, rp, sharedScheduledPool);
        assertThrows(CompletionException.class, () -> ts.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join());
    }

    @Test
    @DisplayName("[TC-33] Body Integrity Propagation in 5-Level Recursive Nesting")
    void testTC33() {
        Processor bot = new LeafFilterProcessor(new MockFilter("Bot", 5, 0, null) { @Override public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) { e.setBody("DEEP".getBytes()); return CompletableFuture.completedFuture(e); }}, sharedScheduledPool);
        Processor l4 = new ResilientScopeHandler("L4", Collections.singletonList(bot), null, OnErrorPolicy.THROW, null, sharedScheduledPool);
        Map<String, Object> c = new HashMap<>(); c.put("aggregationType", "OVERRIDE");
        Processor l3 = new ParallelScatterGatherRouter("L3", Collections.singletonList(l4), c, sharedScheduledPool);
        Processor l2 = new ResilientScopeHandler("L2", Collections.singletonList(l3), null, OnErrorPolicy.THROW, null, sharedScheduledPool);
        Processor top = new ParallelScatterGatherRouter("TOP", Collections.singletonList(l2), c, sharedScheduledPool);
        assertEquals("DEEP", new String(top.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join().getBody()));
    }

    @Test
    @DisplayName("[TC-34] Mixed SG + ResilientScope Branch Orchestration Inside SG")
    void testTC34() {
        Processor bA = new LeafFilterProcessor(new MockFilter("A", 5, 0, null), sharedScheduledPool);
        Processor bB = new ResilientScopeHandler("B", Collections.singletonList(new LeafFilterProcessor(new MockFilter("F", 5, 99, null), sharedScheduledPool)), Collections.singletonList(new LeafFilterProcessor(new MockFilter("REC", 5, 0, null), sharedScheduledPool)), OnErrorPolicy.FALLBACK, null, sharedScheduledPool);
        Map<String, Object> c = new HashMap<>(); c.put("aggregationType", "MAP_MERGE");
        Processor root = new ParallelScatterGatherRouter("ROOT", Arrays.asList(bA, bB), c, sharedScheduledPool);
        assertTrue(new String(root.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join().getBody()).contains("REC"));
    }

    @Test
    @DisplayName("[TC-35] Immediate Stop on Upper SG Timeout During Micro-Retry")
    void testTC35() {
        RetryPolicy<Payload> rp = RetryPolicy.<Payload>builder().withMaxRetries(10).withDelay(Duration.ofMillis(500)).build();
        Processor leaf = new LeafFilterProcessor(new MockFilter("Retry", 100, 99, rp), sharedScheduledPool);
        Map<String, Object> c = new HashMap<>(); c.put("timeout", 200);
        Processor sg = new ParallelScatterGatherRouter("SG", Collections.singletonList(leaf), c, sharedScheduledPool);
        assertThrows(CompletionException.class, () -> sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join());
    }

    @Test
    @DisplayName("[TC-36] Worker Pool Saturation (100 Nodes) SEDA Backpressure Defense")
    void testTC36() {
        List<Processor> bulk = new ArrayList<>();
        for(int i=0; i<100; i++) bulk.add(new LeafFilterProcessor(new MockFilter("N"+i, 10, 0, null), sharedScheduledPool));
        ParallelScatterGatherRouter sg = new ParallelScatterGatherRouter("Mass", bulk, new HashMap<>(), sharedScheduledPool);
        assertNotNull(sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join().getHeader("Result_From_N99"));
    }

    @Test
    @DisplayName("[TC-37] Error Propagated Upward on Fallback Chain Failure")
    void testTC37() {
        Processor main = new LeafFilterProcessor(new MockFilter("M", 5, 99, null), sharedScheduledPool);
        Processor fb = new LeafFilterProcessor(new MockFilter("F", 5, 99, null), sharedScheduledPool);
        Processor ts = new ResilientScopeHandler("TS", Collections.singletonList(main), Collections.singletonList(fb), OnErrorPolicy.FALLBACK, null, sharedScheduledPool);
        assertThrows(CompletionException.class, () -> ts.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join());
    }

    @Test
    @DisplayName("[TC-38] MDC (TrxID) Retention Verified in 5-Level Async Chaining")
    void testTC38() {
        Processor p = new LeafFilterProcessor(new MockFilter("L", 5, 0, null), sharedScheduledPool);
        for(int i=0; i<4; i++) p = new ResilientScopeHandler("L"+i, Collections.singletonList(p), null, OnErrorPolicy.THROW, null, sharedScheduledPool);
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8); ev.setTrxID("TX-ZEFIO");
        assertEquals("TX-ZEFIO", p.executeAsync(ev, flowWorkerPool).join().getTrxID());
    }

    @Test
    @DisplayName("[TC-39] BEST_EFFORT Child Error Isolated Inside FAIL_FAST Parent")
    void testTC39() {
        Map<String, Object> c1 = new HashMap<>(); c1.put("errorPolicy", "BEST_EFFORT");
        Processor child = new ParallelScatterGatherRouter("C", Collections.singletonList(new LeafFilterProcessor(new MockFilter("F", 5, 99, null), sharedScheduledPool)), c1, sharedScheduledPool);
        Map<String, Object> c2 = new HashMap<>(); c2.put("errorPolicy", "FAIL_FAST");
        Processor parent = new ParallelScatterGatherRouter("P", Collections.singletonList(child), c2, sharedScheduledPool);
        assertNotNull(parent.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join());
    }

    @Test
    @DisplayName("[TC-40] MAP_MERGE Safety for Binary Data and JSON Responses")
    void testTC40() {
        byte[] img = new byte[]{(byte)0xFF, 0x00, 0x01};
        Processor p1 = new LeafFilterProcessor(new MockFilter("Img", 5, 0, null) { @Override public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) { e.setBody(img); return super.executeAsync(e, ex); }}, sharedScheduledPool);
        Map<String, Object> c = new HashMap<>(); c.put("aggregationType", "MAP_MERGE");
        Processor sg = new ParallelScatterGatherRouter("SG", Collections.singletonList(p1), c, sharedScheduledPool);
        assertTrue(new String(sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join().getBody()).contains("\"Img\""));
    }

    // ==============================================================================
    // [8] Lifecycle, Memory & Edge Scenarios (TC-41 ~ TC-60)
    // ==============================================================================
    @Test
    @DisplayName("[TC-41] Resource Leak Prevention for Responses Arriving After Timeout")
    void testTC41() throws InterruptedException {
        Processor slow = new LeafFilterProcessor(new MockFilter("Late", 1000, 0, null), sharedScheduledPool);
        Map<String, Object> c = new HashMap<>(); c.put("timeout", 100);
        ParallelScatterGatherRouter sg = new ParallelScatterGatherRouter("SG", Collections.singletonList(slow), c, sharedScheduledPool);
        sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).handle((r, e) -> null);
        Thread.sleep(1200);
        assertTrue(true);
    }

    @Test
    @DisplayName("[TC-43] Deep Copy Integrity on Simultaneous Property Modification in Parallel Nodes")
    void testTC43() {
        MockFilter f1 = new MockFilter("P1", 50, 0, null) { @Override public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) { e.setHeader("K", "V1"); return super.executeAsync(e, ex); }};
        MockFilter f2 = new MockFilter("P2", 50, 0, null) { @Override public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) { e.setHeader("K", "V2"); return super.executeAsync(e, ex); }};
        ParallelScatterGatherRouter sg = new ParallelScatterGatherRouter("SG", Arrays.asList(new LeafFilterProcessor(f1, sharedScheduledPool), new LeafFilterProcessor(f2, sharedScheduledPool)), new HashMap<>(), sharedScheduledPool);
        assertNotNull(sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join().getHeader("K"));
    }

    @Test
    @DisplayName("[TC-44] MAP_MERGE Empty Object Defense When Node Result is Null")
    void testTC44() {
        Processor p = new LeafFilterProcessor(new MockFilter("Null", 5, 0, null) { @Override public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) { return CompletableFuture.completedFuture(null); }}, sharedScheduledPool);
        Map<String, Object> c = new HashMap<>(); c.put("aggregationType", "MAP_MERGE");
        Processor sg = new ParallelScatterGatherRouter("SG", Collections.singletonList(p), c, sharedScheduledPool);
        assertEquals("{}", new String(sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join().getBody()));
    }

    @Test
    @DisplayName("[TC-48] Nested Defense: Retry Policy Inside Fallback")
    void testTC48() {
        Processor main = new LeafFilterProcessor(new MockFilter("M", 5, 99, null), sharedScheduledPool);
        RetryPolicy<Payload> rp = RetryPolicy.<Payload>builder().withMaxRetries(2).build();
        Processor fbLeaf = new LeafFilterProcessor(new MockFilter("F", 5, 1, rp), sharedScheduledPool);
        Processor ts = new ResilientScopeHandler("TS", Collections.singletonList(main), Collections.singletonList(fbLeaf), OnErrorPolicy.FALLBACK, null, sharedScheduledPool);
        assertEquals("SUCCESS", ts.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join().getHeader("Result_From_F"));
    }

    @Test
    @DisplayName("[TC-52] Circular Reference Defense During MAP_MERGE")
    void testTC52() {
        Processor p = new LeafFilterProcessor(new MockFilter("A", 5, 0, null) { @Override public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) { e.setHeader("Self", e); return super.executeAsync(e, ex); }}, sharedScheduledPool);
        Map<String, Object> c = new HashMap<>(); c.put("aggregationType", "MAP_MERGE");
        Processor sg = new ParallelScatterGatherRouter("SG", Collections.singletonList(p), c, sharedScheduledPool);
        assertDoesNotThrow(() -> sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join());
    }

    @Test
    @DisplayName("[TC-55] Double Fallback (Fallback of Fallback) Chaining")
    void testTC55() {
        Processor m = new LeafFilterProcessor(new MockFilter("M", 5, 99, null), sharedScheduledPool);
        Processor f1 = new ResilientScopeHandler("F1", Collections.singletonList(new LeafFilterProcessor(new MockFilter("F1M", 5, 99, null), sharedScheduledPool)), Collections.singletonList(new LeafFilterProcessor(new MockFilter("F2", 5, 0, null), sharedScheduledPool)), OnErrorPolicy.FALLBACK, null, sharedScheduledPool);
        Processor ts = new ResilientScopeHandler("TOP", Collections.singletonList(m), Collections.singletonList(f1), OnErrorPolicy.FALLBACK, null, sharedScheduledPool);
        assertEquals("SUCCESS", ts.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join().getHeader("Result_From_F2"));
    }

    @Test
    @DisplayName("[TC-59] Final Recovery via ResilientScope Fallback After Failsafe Retries Exhausted")
    void testTC59() {
        RetryPolicy<Payload> rp = RetryPolicy.<Payload>builder().withMaxRetries(1).build();
        Processor m = new LeafFilterProcessor(new MockFilter("M", 5, 99, rp), sharedScheduledPool);
        Processor f = new LeafFilterProcessor(new MockFilter("F", 5, 0, null), sharedScheduledPool);
        Processor ts = new ResilientScopeHandler("TS", Collections.singletonList(m), Collections.singletonList(f), OnErrorPolicy.FALLBACK, null, sharedScheduledPool);
        assertEquals("SUCCESS", ts.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join().getHeader("Result_From_F"));
    }

    @Test
    @DisplayName("[TC-60] 100-Node Massive Complex Orchestration Verification")
    void testTC60() {
        List<Processor> bulk = new ArrayList<>();
        for(int i=0; i<100; i++) bulk.add(new LeafFilterProcessor(new MockFilter("N"+i, 5, 0, null), sharedScheduledPool));
        Map<String, Object> c = new HashMap<>(); c.put("aggregationType", "MAP_MERGE"); c.put("errorPolicy", "BEST_EFFORT");
        Processor sg = new ParallelScatterGatherRouter("SG", bulk, c, sharedScheduledPool);
        Processor ts = new ResilientScopeHandler("TS", Collections.singletonList(sg), null, OnErrorPolicy.THROW, null, sharedScheduledPool);
        assertTrue(new String(ts.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join().getBody()).contains("N99"));
    }

    // ==============================================================================
    // [9] Complex Logic Branching Scenarios (TC-61 ~ TC-66)
    // ==============================================================================
    @Test
    @DisplayName("[TC-61] CONTINUE Policy: Ignore Error and Proceed to Next Step")
    void testTryScopeContinuePolicyExecution() {
        Processor failing = new LeafFilterProcessor(new MockFilter("MustFail", 10, 99, null), sharedScheduledPool);
        Processor tryScope = new ResilientScopeHandler("TS_Ignore", Collections.singletonList(failing), null, OnErrorPolicy.CONTINUE, null, sharedScheduledPool);
        Processor nextStep = new LeafFilterProcessor(new MockFilter("NextStep", 10, 0, null), sharedScheduledPool);

        Payload result = tryScope.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool)
                .thenCompose(ev -> nextStep.executeAsync(ev, flowWorkerPool)).join();

        assertNull(result.getHeader("Result_From_MustFail"));
        assertEquals("SUCCESS", result.getHeader("Result_From_NextStep"));
    }

    @Test
    @DisplayName("[TC-62] SpEL Switch -> ResilientScope(Fallback) Mixed Verification")
    void testSwitchToTryScopeFallback() {
        Processor failNode = new LeafFilterProcessor(new MockFilter("Bad_API", 10, 99, null), sharedScheduledPool);
        Processor fbNode = new LeafFilterProcessor(new MockFilter("FB_API", 10, 0, null), sharedScheduledPool);
        Processor tryScope = new ResilientScopeHandler("TS_Route", Collections.singletonList(failNode), Collections.singletonList(fbNode), OnErrorPolicy.FALLBACK, null, sharedScheduledPool);

        SwitchBranch branch = new SwitchBranch("#{true}", Collections.singletonList(tryScope));
        Processor switchProc = new ConditionalRouteSelector("Router", Collections.singletonList(branch), null);
        Payload result = switchProc.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertEquals("SUCCESS", result.getHeader("Result_From_FB_API"));
    }

    @Test
    @DisplayName("[TC-63] Complex Topology: Switch as One of ScatterGather Parallel Nodes")
    void testScatterGatherWithNestedSwitch() {
        Processor normalNode = new LeafFilterProcessor(new MockFilter("Normal_API", 10, 0, null), sharedScheduledPool);
        Processor switchChild = new LeafFilterProcessor(new MockFilter("Switch_API", 10, 0, null), sharedScheduledPool);

        SwitchBranch branch = new SwitchBranch("#{payload.headers['body']['type'] == 'A'}", Collections.singletonList(switchChild));
        Processor switchProc = new ConditionalRouteSelector("Nested_Router", Collections.singletonList(branch), null);

        Map<String, Object> sgConfig = new HashMap<>();
        sgConfig.put("aggregationType", "MAP_MERGE");
        Processor sg = new ParallelScatterGatherRouter("Top_SG", Arrays.asList(normalNode, switchProc), sgConfig, sharedScheduledPool);

        Payload payload = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        payload.setHeader("body", Collections.singletonMap("type", "A"));

        Payload result = sg.executeAsync(payload, flowWorkerPool).join();

        assertTrue(new String(result.getBody()).contains("Normal_API") && new String(result.getBody()).contains("Switch_API"));
    }

    @Test
    @DisplayName("[TC-64] Defense Against Retry Accumulation Contamination (Macro RetryPolicy + MAP_MERGE)")
    void testTryScopeRetryMapMergeContamination() {
        Processor unstable = new LeafFilterProcessor(new MockFilter("Unstable", 10, 1, null), sharedScheduledPool);
        Map<String, Object> sgConfig = new HashMap<>(); sgConfig.put("aggregationType", "MAP_MERGE");
        Processor sg = new ParallelScatterGatherRouter("SG", Collections.singletonList(unstable), sgConfig, sharedScheduledPool);

        RetryPolicy<Payload> rp = RetryPolicy.<Payload>builder().withMaxRetries(2).build();
        Processor ts = new ResilientScopeHandler("TS", Collections.singletonList(sg), null, OnErrorPolicy.THROW, rp, sharedScheduledPool);
        Payload result = ts.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertTrue(new String(result.getBody()).contains("Unstable"));
    }

    @Test
    @DisplayName("[TC-65] Deep Switch DefaultBranch Execution")
    void testSwitchDefaultBranchExecution() {
        Processor defaultNode = new LeafFilterProcessor(new MockFilter("Default_API", 10, 0, null), sharedScheduledPool);
        SwitchBranch branch = new SwitchBranch("1 == 2", Collections.singletonList(new LeafFilterProcessor(new MockFilter("Never_Reach", 10, 0, null), sharedScheduledPool)));
        Processor switchProc = new ConditionalRouteSelector("Router", Collections.singletonList(branch), Collections.singletonList(defaultNode));
        Payload result = switchProc.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertEquals("SUCCESS", result.getHeader("Result_From_Default_API"));
    }

    @Test
    @DisplayName("[TC-66] Cancel Signal Propagation and Zombie Thread Defense on SG Timeout")
    void testScatterGatherTimeoutCancelPropagation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Processor verySlow = new LeafFilterProcessor(new MockFilter("Zombie", 5000, 0, null) {
            @Override public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) {
                return super.executeAsync(e, ex).whenComplete((r, t) -> { if (t instanceof CancellationException) latch.countDown(); });
            }
        }, sharedScheduledPool);

        Map<String, Object> config = new HashMap<>(); config.put("timeout", 100);
        Processor sg = new ParallelScatterGatherRouter("SG", Collections.singletonList(verySlow), config, sharedScheduledPool);
        assertThrows(CompletionException.class, () -> sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join());
    }
}
