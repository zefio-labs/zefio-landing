package io.zefio.core.engine.flow;

import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.TimeUtils;
import io.zefio.core.config.flow.FlowOptions;
import io.zefio.core.PipelineService;
import io.zefio.core.Ingress;
import io.zefio.core.engine.processor.Processor;
import io.zefio.core.engine.registry.RouteDefinitionRegistry;
import io.zefio.core.monitor.FlowMonitorManager;
import io.zefio.core.util.MdcContextAwareExecutor;
import io.zefio.core.payload.Payload;
import io.zefio.core.telemetry.registry.MonitorRegistry;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The main implementation of PipelineService that orchestrates the entire lifecycle of a message flow.
 * It manages thread pools, SEDA workers (Compute and IO stages), and coordinates processing
 * through the PipelineOrchestrator. It also handles the graceful shutdown process to ensure zero data loss.
 * ==============================================================================================
 * SEDA Error Handling Architecture
 * ==============================================================================================
 * ┌──────────────┬──────────────────────────────────────────────┬──────────────────────────────────────────────────────┐
 * │ Zone         │ Occurrences & Examples                       │ Responsibility (Error Handling & Logging)            │
 * ├──────────────┼──────────────────────────────────────────────┼──────────────────────────────────────────────────────┤
 * │ Zone 1: Edge │ [Network Ingress]                            │ Ingress implementation & Netty Handlers              │
 * │              │ - Startup failures (Port conflict, etc.)      │ -> Immediate connection termination/rejection        │
 * │              │ - Parsing errors (Length limit, protocol)    │ -> Collect Edge errors and return error payloads     │
 * ├──────────────┼──────────────────────────────────────────────┼──────────────────────────────────────────────────────┤
 * │ Zone 2: Queue│ [SEDA Buffers & Scheduling]                  │ ComputeStageWorker, IoStageWorker                    │
 * │              │ - CPU/IO Queue Full (TPS surge)              │ -> Event rejection & Queue Full exception            │
 * │              │ - Thread Pool Exhaustion (Rejection)         │ -> Call FlowErrorHandler for common responses        │
 * ├──────────────┼──────────────────────────────────────────────┼──────────────────────────────────────────────────────┤
 * │ Zone 3: Core │ [Business Compute (CPU Bound)]               │ PipelineOrchestrator & FlowErrorHandler              │
 * │              │ - Transformation failures (JSON/XML/etc.)     │ -> Node-level Retry policies (Failsafe)              │
 * │              │ - Logic errors (NullPointer, etc.)           │ -> Delegate control to FlowErrorHandler              │
 * ├──────────────┼──────────────────────────────────────────────┼──────────────────────────────────────────────────────┤
 * │ Zone 4: Ext  │ [Upstream Systems (I/O Bound)]               │ IoStageWorker & FlowErrorHandler                     │
 * │              │ - Communication failure (Timeout, Refused)   │ -> Node-level Retry policies (Failsafe)              │
 * │              │ - DB connection or response delays           │ -> Delegate control to FlowErrorHandler              │
 * └──────────────┴──────────────────────────────────────────────┴──────────────────────────────────────────────────────┘
 */
public class FlowService implements PipelineService {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    private static final String SUFFIX_INGRESS_RECEIVER = "-ingress-receiver";
    private static final String SUFFIX_PROCESSOR = "-worker-";

    private final FlowInitContext flowInitContext;
    private final String flowName;
    private final FlowOptions options;
    private final Ingress ingress;

    private final List<Processor> rootPipeline;
    private final Map<String, List<Processor>> errorPipelines;

    private ThreadPoolTaskExecutor rawFlowPool;
    private ExecutorService flowExecutor;
    private FlowMonitorManager flowMonitorManager;

    private ComputeStageWorker computeStageWorker;
    private PipelineOrchestrator pipelineOrchestrator;
    private IoStageWorker ioStageWorker;

    private Thread ingressThread;

    public FlowService(FlowInitContext ctx) {
        this.flowInitContext = ctx;
        this.flowName = ctx.getFlowName();
        this.options = ctx.getOptions();
        this.ingress = ctx.getIngress();
        this.rootPipeline = ctx.getRootPipeline();
        this.errorPipelines = ctx.getErrorPipelines();
    }

    @Override
    public void initialise() throws Exception {
        setupThreadPool(options);

        FlowErrorHandler errorHandler = new FlowErrorHandler(this.flowInitContext);

        this.computeStageWorker = new ComputeStageWorker(this.flowInitContext, errorHandler);
        this.pipelineOrchestrator = new PipelineOrchestrator(this.flowInitContext, this.flowExecutor, errorHandler);
        this.ioStageWorker = new IoStageWorker(this.flowInitContext, errorHandler);

        this.pipelineOrchestrator.setIoStageWorker(this.ioStageWorker);
        this.ioStageWorker.setPipelineOrchestrator(this.pipelineOrchestrator);

        RouteDefinitionRegistry.register(this.flowName, this);
        MonitorRegistry.register(this);

        printConfigurationLog();
    }

    private void setupThreadPool(FlowOptions options) throws Exception {
        int flowCorePoolSize = options.getThreadPool().getCorePoolSize();
        int flowMaxPoolSize = options.getThreadPool().getMaxPoolSize();
        int flowQueueCapacity = options.getThreadPool().getQueueCapacity();
        String poolIdentifier = this.flowName + SUFFIX_PROCESSOR;

        final RejectedExecutionHandler rejectionHandler = (Runnable r, ThreadPoolExecutor executor) -> {
            String status = String.format("PoolSize=%d, Active=%d, Queue=%d", executor.getPoolSize(), executor.getActiveCount(), executor.getQueue().size());
            throw new TaskRejectedException("Task rejected from [" + poolIdentifier + "]. " + status);
        };

        try {
            this.rawFlowPool = new ThreadPoolTaskExecutor();
            this.rawFlowPool.setCorePoolSize(flowCorePoolSize);
            this.rawFlowPool.setMaxPoolSize(flowMaxPoolSize);
            this.rawFlowPool.setQueueCapacity(flowQueueCapacity);
            this.rawFlowPool.setRejectedExecutionHandler(rejectionHandler);
            this.rawFlowPool.setThreadNamePrefix(poolIdentifier);
            this.rawFlowPool.initialize();

            this.flowExecutor = new MdcContextAwareExecutor(this.rawFlowPool.getThreadPoolExecutor());
        } catch (Exception e) {
            log.error("[{}] Thread Pool Initialization Failed [{}]: {}", flowName, FlowResultStatus.PIPELINE_EXECUTION_ERROR.name(), e.getMessage());
            throw e;
        }
    }

    private void setupMonitoring() {
        this.flowMonitorManager = new FlowMonitorManager(log, this.flowInitContext);
        this.flowMonitorManager.initialiseThreadPoolMonitoring(rawFlowPool);
        this.flowMonitorManager.addQueueMonitoring("cpu", this.computeStageWorker);
        this.flowMonitorManager.addQueueMonitoring("io", this.ioStageWorker);
        this.flowMonitorManager.initialisePluginsMonitoring(this.flowInitContext);
        this.flowMonitorManager.startMonitoringTasks();
    }

    private void printConfigurationLog() {
        int flowCorePoolSize = options.getThreadPool().getCorePoolSize();
        int flowMaxPoolSize = options.getThreadPool().getMaxPoolSize();
        int flowQueueCapacity = options.getThreadPool().getQueueCapacity();

        log.info("{}", StringUtils.center(" " + flowName + " CONFIGURATION ", 70, "■"));
        log.info("[{}] : {}", StringUtils.rightPad("Running", 20), TimeUtils.timestamp());
        log.info("[{}] : {}", StringUtils.rightPad("Ingress", 20), this.ingress.getPluginName());

        log.info("[{}] : {}", StringUtils.rightPad("Thread CorePoolSize", 20), flowCorePoolSize);
        log.info("[{}] : {}", StringUtils.rightPad("Thread MaxPoolSize", 20), flowMaxPoolSize);
        log.info("[{}] : {}", StringUtils.rightPad("Thread QueueCapacity", 20), flowQueueCapacity);

        for(Processor processor: rootPipeline) {
            log.info("[{}] : {}", StringUtils.rightPad("Processor", 20), processor.getName());
        }

        log.info("{}", StringUtils.center(" FLOW SCENARIO ", 70, "□"));
        log.info("{} #1\t ▶", this.ingress.getPluginName());
        for(Processor processor: rootPipeline) {
            log.info("\t\t\t{}", processor.getName());
            log.info("\t\t\t\t ▼");
        }
        if(this.ingress.isTwoWay()) log.info("{} #1\t ◀", this.ingress.getPluginName());
        else log.info("One-Way #1\t ◀");
        log.info("{}", StringUtils.center("", 70, "□"));
    }

    @Override
    public void start() throws Exception {
        log.info("{}", StringUtils.center(" " + this.flowName + " STARTING ", 70, "■"));

        for(Processor processor : this.rootPipeline) {
            processor.initialise();
        }

        if (this.errorPipelines != null) {
            for (List<Processor> pipeline : this.errorPipelines.values()) {
                for (Processor processor : pipeline) {
                    processor.initialise();
                }
            }
        }
        this.ingress.initialise();

        this.ioStageWorker.start();
        this.computeStageWorker.start(this.flowExecutor, this.rawFlowPool, event -> pipelineOrchestrator.process(event, 0));

        setupMonitoring();

        log.info("{}", StringUtils.center(" " + this.flowName + " READY ", 70, "■"));

        this.ingressThread = new Thread(() -> {
            log.info("Ingress receiver thread [{}] started.", Thread.currentThread().getName());
            try {
                ingress.receive(this::dispatch);
            } catch (Exception e) {
                log.error("Critical error in ingress receiver [{}]", Thread.currentThread().getName(), e);
            }
        }, flowName + SUFFIX_INGRESS_RECEIVER);
        this.ingressThread.setDaemon(true);
        this.ingressThread.start();
    }

    @Override
    public void dispatch(Payload payload) {
        computeStageWorker.submit(payload);
    }

    @Override
    public void resetMetrics() {
        if (this.flowMonitorManager != null) {
            this.flowMonitorManager.resetAll();
        }
    }

    @Override
    public boolean shutdown() {
        if (!isShuttingDown.compareAndSet(false, true)) {
            log.debug("[{}] Shutdown already in progress. Skipping.", flowName);
            return true;
        }

        log.info("Initiating Graceful Shutdown for flow: {}", flowName);

        MonitorRegistry.unregister(this);
        RouteDefinitionRegistry.unregister(this.flowName);

        this.ingress.close();
        if (this.ingressThread != null) {
            try { this.ingressThread.join(3000); } catch (InterruptedException ignored) {}
        }

        log.info("[{}] Draining remaining items from Worker queues...", flowName);
        this.computeStageWorker.stop();
        this.ioStageWorker.stop();

        if (!computeStageWorker.isQueueEmpty()) {
            log.error("SHUTDOWN ALERT: {} CPU events could not be dispatched during drain!", computeStageWorker.getQueueSize());
        }

        if(this.rawFlowPool != null) {
            this.rawFlowPool.shutdown();
            try {
                int safeTimeout = 60;
                log.info("[{}] Waiting up to {}s for in-flight transactions to complete...", flowName, safeTimeout);

                if (!this.rawFlowPool.getThreadPoolExecutor().awaitTermination(safeTimeout, TimeUnit.SECONDS)) {
                    log.error("[{}] CRITICAL: Worker pool timeout. Forcing shutdown. DATA MAY BE LOST!", flowName);
                    this.rawFlowPool.getThreadPoolExecutor().shutdownNow();
                } else {
                    log.info("[{}] All in-flight transactions processed successfully. (Zero Loss)", flowName);
                }
            } catch (InterruptedException e) {
                this.rawFlowPool.getThreadPoolExecutor().shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        for(Processor processor : this.rootPipeline) processor.close();
        this.rootPipeline.clear();

        if (this.errorPipelines != null) {
            for (List<Processor> pipeline : this.errorPipelines.values()) {
                for (Processor processor : pipeline) {
                    processor.close();
                }
            }
            this.errorPipelines.clear();
        }

        if (this.flowMonitorManager != null) this.flowMonitorManager.shutdown();
        return true;
    }

    @Override
    public String getName() {
        return this.flowName;
    }

    @Override
    public Ingress getIngress() {
        return this.ingress;
    }
}
