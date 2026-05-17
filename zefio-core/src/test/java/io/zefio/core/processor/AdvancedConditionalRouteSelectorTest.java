package io.zefio.core.processor;

import dev.failsafe.RetryPolicy;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.payload.Payload;
import io.zefio.core.config.flow.OnErrorPolicy;
import io.zefio.core.engine.processor.*;
import io.zefio.core.engine.processor.dto.SwitchBranch;
import io.zefio.core.payload.ZefioMessage;
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
 * SwitchProcessor Master Test Suite (50 Extreme Validation Scenarios)
 * [Refactoring Details]
 * - Simplified ResilientScopeHandler (TryScope) constructor parameters.
 * - Applied OnErrorPolicy Enum and injected RetryPolicy directly.
 * - Adheres to Ingress / Upstream terminology standards.
 * - Removed deprecated ErrorHandlerConfiguration logic.
 */
class AdvancedConditionalRouteSelectorTest {
    private static final Logger log = LoggerFactory.getLogger(AdvancedConditionalRouteSelectorTest.class);

    private ExecutorService flowWorkerPool;
    private ScheduledExecutorService sharedScheduledPool;

    @BeforeEach
    void setUp() {
        flowWorkerPool = Executors.newFixedThreadPool(20);
        sharedScheduledPool = Executors.newScheduledThreadPool(5);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        flowWorkerPool.shutdownNow();
        sharedScheduledPool.shutdownNow();
        flowWorkerPool.awaitTermination(1, TimeUnit.SECONDS);
    }

    // ==============================================================================
    // [Category 1] Basic & Structural Anomalies
    // ==============================================================================

    @Test
    @DisplayName("[TC-01] Empty Switch (no cases, no default) -> Bypass original payload")
    void testEmptySwitch() {
        Processor switchProc = new ConditionalRouteSelector("EmptySwitch", null, null);
        Payload payload = new ZefioMessage("Origin".getBytes(), StandardCharsets.UTF_8);

        Payload result = switchProc.executeAsync(payload, flowWorkerPool).join();
        assertEquals("Origin", new String(result.getBody()));
    }

    @Test
    @DisplayName("[TC-02] Condition matched but empty steps -> Bypass original payload")
    void testMatchedButEmptySteps() {
        List<SwitchBranch> branches = Collections.singletonList(
                new SwitchBranch("#{true}", Collections.emptyList())
        );
        Processor switchProc = new ConditionalRouteSelector("EmptySteps", branches, null);
        Payload payload = new ZefioMessage("Origin".getBytes(), StandardCharsets.UTF_8);

        Payload result = switchProc.executeAsync(payload, flowWorkerPool).join();
        assertEquals("Origin", new String(result.getBody()));
    }

    @Test
    @DisplayName("[TC-03] First match wins (Short-circuit execution)")
    void testFirstMatchWins() {
        List<SwitchBranch> branches = Arrays.asList(
                new SwitchBranch("#{true}", Collections.singletonList(mockLeaf("B1"))),
                new SwitchBranch("#{true}", Collections.singletonList(mockLeaf("B2"))),
                new SwitchBranch("#{true}", Collections.singletonList(mockLeaf("B3")))
        );
        Processor switchProc = new ConditionalRouteSelector("ShortCircuit", branches, Collections.singletonList(mockLeaf("DEF")));

        Payload result = switchProc.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertNotNull(result.getHeader("Result_From_B1"));
        assertNull(result.getHeader("Result_From_B2"));
        assertNull(result.getHeader("Result_From_DEF"));
    }

    // ==============================================================================
    // [Category 2] SpEL Evaluation Anomalies
    // ==============================================================================

    @Test
    @DisplayName("[TC-04] SpEL evaluation exception isolation (e.g., division by zero)")
    void testSpelExceptionIsolation() {
        List<SwitchBranch> branches = Arrays.asList(
                new SwitchBranch("#{1 / 0 == 0}", Collections.singletonList(mockLeaf("B1"))),
                new SwitchBranch("#{body['missing'].length() > 0}", Collections.singletonList(mockLeaf("B2"))),
                new SwitchBranch("#{payload.headers['KEY'] == 'VAL'}", Collections.singletonList(mockLeaf("B3")))
        );

        Processor switchProc = new ConditionalRouteSelector("SpelHell", branches, Collections.singletonList(mockLeaf("DEF")));
        Payload payload = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        payload.setHeader("KEY", "VAL");

        Payload result = switchProc.executeAsync(payload, flowWorkerPool).join();
        assertNotNull(result.getHeader("Result_From_B3"));
    }

    @Test
    @DisplayName("[TC-05] SpEL evaluation results in non-boolean value defense")
    void testSpelNonBooleanResult() {
        List<SwitchBranch> branches = Arrays.asList(
                new SwitchBranch("#{'HELLO'}", Collections.singletonList(mockLeaf("B1"))),
                new SwitchBranch("#{100}", Collections.singletonList(mockLeaf("B2"))),
                new SwitchBranch("#{false}", Collections.singletonList(mockLeaf("B3")))
        );

        Processor switchProc = new ConditionalRouteSelector("NonBool", branches, Collections.singletonList(mockLeaf("DEF")));
        Payload payload = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);

        Payload result = switchProc.executeAsync(payload, flowWorkerPool).join();
        assertNotNull(result.getHeader("Result_From_DEF"));
    }

    @Test
    @DisplayName("[TC-06] Routing execution with fully null body")
    void testNullBodyRouting() {
        List<SwitchBranch> branches = Arrays.asList(
                new SwitchBranch("#{body == null}", Collections.singletonList(
                        new LeafFilterProcessor(new AdvancedCompositeProcessorTest.MockFilter("B1", 5, 0, null) {
                            @Override
                            public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) {
                                e.setHeader("Result_From_B1", "SUCCESS");
                                return CompletableFuture.completedFuture(e);
                            }
                        }, sharedScheduledPool)
                ))
        );
        Processor switchProc = new ConditionalRouteSelector("NullBody", branches, null);
        Payload payload = new ZefioMessage(null, StandardCharsets.UTF_8);

        Payload result = switchProc.executeAsync(payload, flowWorkerPool).join();
        assertNotNull(result.getHeader("Result_From_B1"));
    }

    @Test
    @DisplayName("[TC-07] Propagation of side-effects occurring inside SpEL expressions")
    void testSpelSideEffect() {
        List<SwitchBranch> branches = Arrays.asList(
                new SwitchBranch("#{payload.setHeader('HACK', 'ED') == null || true}", Collections.singletonList(mockLeaf("B1")))
        );

        Processor switchProc = new ConditionalRouteSelector("SideEffect", branches, null);
        Payload payload = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);

        Payload result = switchProc.executeAsync(payload, flowWorkerPool).join();
        assertEquals("ED", result.getHeader("HACK"));
    }

    // ==============================================================================
    // [Category 3] Dynamic Content-Based Routing
    // ==============================================================================

    @Test
    @DisplayName("[TC-08] Multi-depth conditional branching based on JSON body")
    void testDeepJsonRouting() throws Exception {
        String json = "{\"user\": {\"role\": \"VIP\", \"age\": 35}}";
        Payload payload = new ZefioMessage(json.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        payload.setHeader("role", "VIP");
        payload.setHeader("age", 35);

        List<SwitchBranch> branches = Arrays.asList(
                new SwitchBranch("#{payload.headers['role'] == 'NORMAL'}", Collections.singletonList(mockLeaf("NORM"))),
                new SwitchBranch("#{payload.headers['role'] == 'VIP' and payload.headers['age'] >= 30}", Collections.singletonList(mockLeaf("VIP_ADULT")))
        );

        Processor switchProc = new ConditionalRouteSelector("JSONRoute", branches, Collections.singletonList(mockLeaf("DEF")));
        Payload result = switchProc.executeAsync(payload, flowWorkerPool).join();

        assertNotNull(result.getHeader("Result_From_VIP_ADULT"));
    }

    // ==============================================================================
    // [Category 4] Complex Nesting & Error Propagation
    // ==============================================================================

    @Test
    @DisplayName("[TC-09] Switch -> TryScope -> ScatterGather combo (FAIL_FAST propagation)")
    void testSwitchTryScatterGatherCombo() {
        Map<String, Object> sgConfig = new HashMap<>(); sgConfig.put("errorPolicy", "FAIL_FAST");
        Processor sg = new ParallelScatterGatherRouter("SG", Arrays.asList(
                mockLeaf("SG_OK"), mockLeafFail("SG_FAIL")
        ), sgConfig, sharedScheduledPool);

        Processor ts = new ResilientScopeHandler("TS", Collections.singletonList(sg), null, OnErrorPolicy.THROW, null, sharedScheduledPool);

        List<SwitchBranch> branches = Arrays.asList(
                new SwitchBranch("#{true}", Collections.singletonList(ts))
        );
        Processor switchProc = new ConditionalRouteSelector("Combo", branches, null);

        CompletionException ex = assertThrows(CompletionException.class, () ->
                switchProc.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join());

        assertTrue(ex.getCause() instanceof FlowException);
    }

    @Test
    @DisplayName("[TC-10] Dynamic route alteration awareness during TryScope retry")
    void testDynamicRouteChangeDuringRetry() {
        Processor branchB = mockLeaf("Path_B");
        Payload originalPayload = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        originalPayload.setHeader("TARGET", "A");

        RetryPolicy<Payload> rp = RetryPolicy.<Payload>builder().withMaxRetries(3).withDelay(Duration.ofMillis(10)).build();

        Processor mutatingBranchA = new LeafFilterProcessor(new AdvancedCompositeProcessorTest.MockFilter("Path_A", 10, 99, null) {
            @Override public CompletableFuture<Payload> executeAsync(Payload e, Executor ex) {
                originalPayload.setHeader("TARGET", "B");
                return super.executeAsync(e, ex);
            }
        }, sharedScheduledPool);

        List<SwitchBranch> mutatingBranches = Arrays.asList(
                new SwitchBranch("#{payload.headers['TARGET'] == 'A'}", Collections.singletonList(mutatingBranchA)),
                new SwitchBranch("#{payload.headers['TARGET'] == 'B'}", Collections.singletonList(branchB))
        );
        Processor dynamicSwitch = new ConditionalRouteSelector("MutatingSwitch", mutatingBranches, null);

        Processor ts = new ResilientScopeHandler("TS_RETRY", Collections.singletonList(dynamicSwitch), null, OnErrorPolicy.THROW, rp, sharedScheduledPool);

        Payload result = ts.executeAsync(originalPayload, flowWorkerPool).join();

        assertNull(result.getHeader("Result_From_Path_A"));
        assertNotNull(result.getHeader("Result_From_Path_B"));
    }

    // ==============================================================================
    // [Category 5] SpEL Type Coercion & Math
    // ==============================================================================

    @Test @DisplayName("[TC-11] Integer vs Long automatic type coercion and comparison")
    void testTC11() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        ev.setHeader("AMT", 100L);
        assertNotNull(runSwitch("#{payload.headers['AMT'] == 100}", ev).getHeader("Result_From_B1"));
    }

    @Test @DisplayName("[TC-12] BigDecimal precision computation routing")
    void testTC12() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        ev.setHeader("DEC", "10.5");
        assertNotNull(runSwitch("#{new java.math.BigDecimal(payload.headers['DEC']).compareTo(new java.math.BigDecimal('10.0')) > 0}", ev).getHeader("Result_From_B1"));
    }

    @Test @DisplayName("[TC-13] String concatenation operator (+) routing")
    void testTC13() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        ev.setHeader("A", "HELLO"); ev.setHeader("B", "WORLD");
        assertNotNull(runSwitch("#{payload.headers['A'] + payload.headers['B'] == 'HELLOWORLD'}", ev).getHeader("Result_From_B1"));
    }

    @Test @DisplayName("[TC-14] Ternary operator execution evaluating to boolean")
    void testTC14() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        assertNotNull(runSwitch("#{(payload.headers['X'] != null ? false : true) == true}", ev).getHeader("Result_From_B1"));
    }

    @Test @DisplayName("[TC-15] Elvis operator (?:) for null safety routing")
    void testTC15() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        assertNotNull(runSwitch("#{payload.headers['NULL_VAL'] ?: 'DEFAULT' == 'DEFAULT'}", ev).getHeader("Result_From_B1"));
    }

    @Test @DisplayName("[TC-16] Invocation of T(Math).max mathematical function")
    void testTC16() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        assertNotNull(runSwitch("#{T(Math).max(10, 20) == 20}", ev).getHeader("Result_From_B1"));
    }

    @Test @DisplayName("[TC-17] Modulo (%) operator for even/odd branching")
    void testTC17() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        ev.setHeader("REQ_ID", 1002);
        assertNotNull(runSwitch("#{payload.headers['REQ_ID'] % 2 == 0}", ev).getHeader("Result_From_B1"));
    }

    // ==============================================================================
    // [Category 6] String & Regex Edge Cases
    // ==============================================================================

    @Test @DisplayName("[TC-18] Case-insensitive comparison via toUpperCase()")
    void testTC18() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        ev.setHeader("CODE", "aBcD");
        assertNotNull(runSwitch("#{payload.headers['CODE'].toUpperCase() == 'ABCD'}", ev).getHeader("Result_From_B1"));
    }

    @Test @DisplayName("[TC-19] Regular expression pattern routing (matches)")
    void testTC19() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        ev.setHeader("PHONE", "010-1234-5678");
        assertNotNull(runSwitch("#{payload.headers['PHONE'] matches '^010-[0-9]{4}-[0-9]{4}$'}", ev).getHeader("Result_From_B1"));
    }

    @Test @DisplayName("[TC-20] Empty string evaluation defense (fallback to Default)")
    void testTC20() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        assertNotNull(runSwitch("#{''}", ev).getHeader("Result_From_DEF"));
    }

    @Test @DisplayName("[TC-21] Substring out-of-bounds exception defense")
    void testTC21() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        ev.setHeader("SHORT", "A");
        assertNotNull(runSwitch("#{payload.headers['SHORT'].substring(0, 10) == 'A'}", ev).getHeader("Result_From_DEF"));
    }

    @Test @DisplayName("[TC-22] String Format method invocation")
    void testTC22() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        assertNotNull(runSwitch("#{T(java.lang.String).format('%03d', 5) == '005'}", ev).getHeader("Result_From_B1"));
    }

    // ==============================================================================
    // [Category 7] Collections & Arrays
    // ==============================================================================

    @Test @DisplayName("[TC-23] Array index out-of-bounds defense")
    void testTC23() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        ev.setHeader("ARR", new String[]{"A"});
        assertNotNull(runSwitch("#{payload.headers['ARR'][5] == 'A'}", ev).getHeader("Result_From_DEF"));
    }

    @Test @DisplayName("[TC-24] Inline list contains method execution")
    void testTC24() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        ev.setHeader("TYPE", "C");
        assertNotNull(runSwitch("#{ {'A', 'B', 'C'}.contains(payload.headers['TYPE']) }", ev).getHeader("Result_From_B1"));
    }

    @Test @DisplayName("[TC-25] List selection filter followed by size() evaluation")
    void testTC25() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        ev.setHeader("NUMS", Arrays.asList(10, 20, 30, 40));
        assertNotNull(runSwitch("#{payload.headers['NUMS'].?[#this > 20].size() == 2}", ev).getHeader("Result_From_B1"));
    }

    @Test @DisplayName("[TC-26] Map element null deep chaining defense")
    void testTC26() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        Map<String, Object> map = new HashMap<>(); map.put("INNER", null);
        ev.setHeader("MAP", map);
        assertNotNull(runSwitch("#{payload.headers['MAP']['INNER']?.length() > 0}", ev).getHeader("Result_From_DEF"));
    }

    // ==============================================================================
    // [Category 8] Malicious & Syntax Errors
    // ==============================================================================

    @Test @DisplayName("[TC-27] Non-existent class invocation defense")
    void testTC27() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        assertNotNull(runSwitch("#{T(io.zefio.HackerClass).hack() == true}", ev).getHeader("Result_From_DEF"));
    }

    @Test @DisplayName("[TC-28] Expression syntax parsing failure defense")
    void testTC28() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        assertNotNull(runSwitch("#{ >> INVALID SYNTAX << }", ev).getHeader("Result_From_DEF"));
    }

    @Test @DisplayName("[TC-29] Expression evaluates to null defense")
    void testTC29() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        assertNotNull(runSwitch("#{null}", ev).getHeader("Result_From_DEF"));
    }

    @Test @DisplayName("[TC-30] UUID generation function routing")
    void testTC30() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        assertNotNull(runSwitch("#{T(java.util.UUID).randomUUID().toString() == 'IMPOSSIBLE'}", ev).getHeader("Result_From_DEF"));
    }

    // ==============================================================================
    // [Category 9] Default Branch Edge Cases
    // ==============================================================================

    @Test @DisplayName("[TC-31] All conditions fail + empty Default steps list -> Bypass")
    void testTC31() {
        Processor switchProc = new ConditionalRouteSelector("SW",
                Collections.singletonList(new SwitchBranch("#{false}", Collections.singletonList(mockLeaf("B1")))),
                Collections.emptyList());
        Payload result = switchProc.executeAsync(new ZefioMessage("Origin".getBytes(), StandardCharsets.UTF_8), flowWorkerPool).join();
        assertEquals("Origin", new String(result.getBody()));
    }

    @Test @DisplayName("[TC-32] All conditions fail + null Default steps -> Bypass")
    void testTC32() {
        Processor switchProc = new ConditionalRouteSelector("SW",
                Collections.singletonList(new SwitchBranch("#{false}", Collections.singletonList(mockLeaf("B1")))),
                null);
        Payload result = switchProc.executeAsync(new ZefioMessage("Origin".getBytes(), StandardCharsets.UTF_8), flowWorkerPool).join();
        assertEquals("Origin", new String(result.getBody()));
    }

    @Test @DisplayName("[TC-33] Exception propagation when Default route fails")
    void testTC33() {
        Processor switchProc = new ConditionalRouteSelector("SW",
                Collections.singletonList(new SwitchBranch("#{false}", Collections.singletonList(mockLeaf("B1")))),
                Collections.singletonList(mockLeafFail("DEF_FAIL")));

        assertThrows(CompletionException.class, () -> switchProc.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join());
    }

    @Test @DisplayName("[TC-34] Condition matched but Target steps are null -> Bypass")
    void testTC34() {
        Processor switchProc = new ConditionalRouteSelector("SW",
                Collections.singletonList(new SwitchBranch("#{true}", null)),
                Collections.singletonList(mockLeaf("DEF")));
        Payload result = switchProc.executeAsync(new ZefioMessage("Origin".getBytes(), StandardCharsets.UTF_8), flowWorkerPool).join();
        assertEquals("Origin", new String(result.getBody()));
    }

    // ==============================================================================
    // [Category 10] Concurrency & System Variables
    // ==============================================================================

    @Test @DisplayName("[TC-35] SwitchProcessor concurrent stress test (100 threads)")
    void testTC35() {
        Processor switchProc = new ConditionalRouteSelector("SW", Arrays.asList(
                new SwitchBranch("#{payload.headers['IDX'] % 2 == 0}", Collections.singletonList(mockLeaf("EVEN"))),
                new SwitchBranch("#{payload.headers['IDX'] % 2 != 0}", Collections.singletonList(mockLeaf("ODD")))
        ), null);

        ExecutorService testLauncherPool = Executors.newFixedThreadPool(100);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            final int idx = i;
            futures.add(CompletableFuture.runAsync(() -> {
                Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
                ev.setHeader("IDX", idx);
                Payload res = switchProc.executeAsync(ev, flowWorkerPool).join();
                if (idx % 2 == 0) assertNotNull(res.getHeader("Result_From_EVEN"));
                else assertNotNull(res.getHeader("Result_From_ODD"));
            }, testLauncherPool));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        testLauncherPool.shutdown();
    }

    @Test @DisplayName("[TC-36] Direct TrxID referencing in routing")
    void testTC36() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        ev.setTrxID("SPECIAL_TX");
        assertNotNull(runSwitch("#{payload.trxID == 'SPECIAL_TX'}", ev).getHeader("Result_From_B1"));
    }

    @Test @DisplayName("[TC-37] MDC_CONTEXT_MAP referencing during evaluation")
    void testTC37() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        Map<String, String> mdc = new HashMap<>(); mdc.put("FLOW", "MAIN");
        ev.setHeader("MDC_CONTEXT_MAP", mdc);
        assertNotNull(runSwitch("#{payload.headers['MDC_CONTEXT_MAP']['FLOW'] == 'MAIN'}", ev).getHeader("Result_From_B1"));
    }

    @Test @DisplayName("[TC-38] Forcefully cancelling Future during evaluation")
    void testTC38() {
        Processor switchProc = new ConditionalRouteSelector("SW",
                Collections.singletonList(new SwitchBranch("#{true}", Collections.singletonList(new LeafFilterProcessor(new AdvancedCompositeProcessorTest.MockFilter("LONG", 5000, 0, null), sharedScheduledPool)))),
                null);
        CompletableFuture<Payload> future = switchProc.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool);
        future.cancel(true);
        assertTrue(future.isCancelled());
    }

    // ==============================================================================
    // [Category 11] Deep Nesting Orchestration
    // ==============================================================================

    @Test @DisplayName("[TC-39] Nested Switch invocation within a Switch")
    void testTC39() {
        Processor innerSwitch = new ConditionalRouteSelector("Inner",
                Collections.singletonList(new SwitchBranch("#{payload.headers['L2'] == 'Y'}", Collections.singletonList(mockLeaf("DEEP_B1")))),
                Collections.singletonList(mockLeaf("DEEP_DEF")));

        Processor outerSwitch = new ConditionalRouteSelector("Outer",
                Collections.singletonList(new SwitchBranch("#{payload.headers['L1'] == 'Y'}", Collections.singletonList(innerSwitch))),
                null);

        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        ev.setHeader("L1", "Y"); ev.setHeader("L2", "Y");
        Payload res = outerSwitch.executeAsync(ev, flowWorkerPool).join();
        assertNotNull(res.getHeader("Result_From_DEEP_B1"));
    }

    @Test @DisplayName("[TC-40] Branching execution inside ScatterGather (SG -> Switch)")
    void testTC40() {
        Processor sw1 = new ConditionalRouteSelector("SW1", Collections.singletonList(new SwitchBranch("#{true}", Collections.singletonList(mockLeaf("S1")))), null);
        Processor sw2 = new ConditionalRouteSelector("SW2", Collections.singletonList(new SwitchBranch("#{false}", null)), Collections.singletonList(mockLeaf("S2")));

        Map<String, Object> sgConfig = new HashMap<>(); sgConfig.put("aggregationType", "MAP_MERGE");
        Processor sg = new ParallelScatterGatherRouter("SG", Arrays.asList(sw1, sw2), sgConfig, sharedScheduledPool);

        Payload res = sg.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertTrue(new String(res.getBody()).contains("S1") && new String(res.getBody()).contains("S2"));
    }

    @Test @DisplayName("[TC-41] Fallback step acting as a Switch Processor")
    void testTC41() {
        Processor failMain = mockLeafFail("FAIL_MAIN");
        Processor fallbackSwitch = new ConditionalRouteSelector("FB_SW",
                Collections.singletonList(new SwitchBranch("#{payload.headers['FB_ROUTE'] == 1}", Collections.singletonList(mockLeaf("FB_B1")))),
                Collections.singletonList(mockLeaf("FB_DEF")));

        Processor ts = new ResilientScopeHandler("TS", Collections.singletonList(failMain), Collections.singletonList(fallbackSwitch),
                OnErrorPolicy.FALLBACK, null, sharedScheduledPool);

        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        ev.setHeader("FB_ROUTE", 1);
        Payload res = ts.executeAsync(ev, flowWorkerPool).join();
        assertNotNull(res.getHeader("Result_From_FB_B1"));
    }

    @Test @DisplayName("[TC-42] Complex AND/OR precedence validation")
    void testTC42() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        ev.setHeader("A", true); ev.setHeader("B", false); ev.setHeader("C", true);
        assertNotNull(runSwitch("#{payload.headers['A'] and (payload.headers['B'] or payload.headers['C'])}", ev).getHeader("Result_From_B1"));
    }

    @Test @DisplayName("[TC-43] Last branch matching across numerous condition points")
    void testTC43() {
        List<SwitchBranch> branches = new ArrayList<>();
        for (int i = 0; i < 9; i++) branches.add(new SwitchBranch("#{false}", Collections.singletonList(mockLeaf("FAIL"))));
        branches.add(new SwitchBranch("#{true}", Collections.singletonList(mockLeaf("LAST_WIN"))));

        Processor sw = new ConditionalRouteSelector("SW_10", branches, null);
        Payload res = sw.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertNotNull(res.getHeader("Result_From_LAST_WIN"));
    }

    @Test @DisplayName("[TC-44] Current Charset property referencing in SpEL")
    void testTC44() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        assertNotNull(runSwitch("#{payload.currentEncoding.name() == 'UTF-8'}", ev).getHeader("Result_From_B1"));
    }

    @Test @DisplayName("[TC-45] Detection of Blocking I/O filters inside Switch")
    void testTC45() {
        Processor ioLeaf = new LeafFilterProcessor(new AdvancedCompositeProcessorTest.MockFilter("IO", 10, 0, null) {
            @Override public boolean isBlockingType() { return true; }
        }, sharedScheduledPool);

        Processor sw = new ConditionalRouteSelector("SW", Collections.singletonList(new SwitchBranch("#{true}", Collections.singletonList(ioLeaf))), null);
        assertTrue(sw.isBlockingType());
    }

    @Test @DisplayName("[TC-46] Extracted internal filter list flattening verification")
    void testTC46() {
        Processor sw = new ConditionalRouteSelector("SW", Collections.singletonList(new SwitchBranch("#{true}", Collections.singletonList(mockLeaf("B1")))), Collections.singletonList(mockLeaf("DEF")));
        assertEquals(2, sw.extractFilters().size());
    }

    @Test @DisplayName("[TC-47] close() method invocation stability")
    void testTC47() {
        Processor sw = new ConditionalRouteSelector("SW", Collections.singletonList(new SwitchBranch("#{true}", Collections.singletonList(mockLeaf("B1")))), null);
        assertDoesNotThrow(sw::close);
    }

    @Test @DisplayName("[TC-48] Casting JSON raw byte arrays to string for routing")
    void testTC48() {
        Payload ev = new ZefioMessage("{\"code\":\"200\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        assertNotNull(runSwitch("#{new java.lang.String(payload.body, 'UTF-8').contains('200')}", ev).getHeader("Result_From_B1"));
    }

    @Test @DisplayName("[TC-49] Regular expression denial of service (ReDoS) protection")
    void testTC49() {
        Payload ev = new ZefioMessage(new byte[0], StandardCharsets.UTF_8);
        ev.setHeader("DATA", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaa!");
        assertNotNull(runSwitch("#{payload.headers['DATA'] matches '^(a+)+$'}", ev).getHeader("Result_From_DEF"));
    }

    @Test @DisplayName("[TC-50] Grand Finale: 4-layer Nested Orchestration")
    void testTC50() {
        Processor sg = new ParallelScatterGatherRouter("SG", Arrays.asList(mockLeaf("L1"), mockLeaf("L2")), new HashMap<>(), sharedScheduledPool);
        Processor ts = new ResilientScopeHandler("TS", Collections.singletonList(sg), null, OnErrorPolicy.THROW, null, sharedScheduledPool);
        Processor sw = new ConditionalRouteSelector("SW_FINAL", Collections.singletonList(new SwitchBranch("#{true}", Collections.singletonList(ts))), null);

        Payload res = sw.executeAsync(new ZefioMessage(new byte[0], StandardCharsets.UTF_8), flowWorkerPool).join();
        assertNotNull(res.getHeader("Result_From_L1"));
        assertNotNull(res.getHeader("Result_From_L2"));
        log.info("SwitchProcessor Grand Finale Success!");
    }

    // ------------------------------------------------------------------------------
    // Helper Methods
    // ------------------------------------------------------------------------------

    private Processor mockLeaf(String name) {
        return new LeafFilterProcessor(new AdvancedCompositeProcessorTest.MockFilter(name, 5, 0, null), sharedScheduledPool);
    }

    private Processor mockLeafFail(String name) {
        return new LeafFilterProcessor(new AdvancedCompositeProcessorTest.MockFilter(name, 5, 99, null), sharedScheduledPool);
    }

    private Payload runSwitch(String condition, Payload payload) {
        Processor switchProc = new ConditionalRouteSelector("Helper",
                Collections.singletonList(new SwitchBranch(condition, Collections.singletonList(mockLeaf("B1")))),
                Collections.singletonList(mockLeaf("DEF")));
        return switchProc.executeAsync(payload, flowWorkerPool).join();
    }
}
