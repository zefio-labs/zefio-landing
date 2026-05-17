package io.zefio.core.monitor;

import io.zefio.core.Upstream;
import io.zefio.core.config.monitor.MonitorProperties;
import io.zefio.core.GatewayInterceptor;
import io.zefio.core.Ingress;
import io.zefio.core.engine.flow.FlowInitContext;
import io.zefio.core.engine.processor.Processor;
import io.zefio.core.telemetry.AbstractMonitorLogger;
import io.zefio.core.telemetry.MonitorInitContext;
import io.zefio.core.telemetry.module.ModuleMetricsAggregatorLogger;
import io.zefio.core.telemetry.provider.IQueueStatusProvider;
import io.zefio.core.telemetry.queue.QueueMonitorLogger;
import io.zefio.core.telemetry.thread.ThreadPoolMonitorLogger;
import io.zefio.core.telemetry.thread.ThreadPoolStateTracker;
import org.slf4j.Logger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Orchestrates monitoring activities for a specific flow.
 * It initializes and manages loggers for thread pools, internal queues, and module metrics
 * by traversing the pipeline tree to extract active interceptors.
 */
public class FlowMonitorManager {

    private final String flowName;
    private final Logger log;
    private final boolean enableFilterMetricsLogging;
    private final boolean enableThreadPoolLogging;
    private final boolean enableQueueLogging;
    private final ScheduledExecutorService sharedScheduler;

    private ThreadPoolMonitorLogger threadPoolLogger;

    private final MonitorProperties.ThreadPoolThreshold threadPoolThreshold;
    private final MonitorProperties.ModuleMetricsThreshold filterMetricsThreshold;
    private final MonitorProperties.NettyEventLoopThreshold nettyEventLoopThreshold;
    private final MonitorProperties.ConnectionPoolThreshold connectionPoolThreshold;
    private final MonitorProperties.QueueThreshold queueThreshold;

    private final List<ModuleMetricsAggregatorLogger> metricsAggregatorLoggers = new ArrayList<>();
    private final List<AbstractMonitorLogger> endpointMonitorLoggers = new ArrayList<>();
    private final List<QueueMonitorLogger> queueLoggers = new ArrayList<>();
    private final MonitorInitContext.MonitorInitContextBuilder monitorInitContextBuilder;

    public FlowMonitorManager(Logger log, FlowInitContext flowInitContext) {
        this.flowName = flowInitContext.getFlowName();
        this.log = log;

        this.enableFilterMetricsLogging = flowInitContext.getMonitorProperties().getDefaultOptions().isEnableModuleMetricsLogger();
        this.enableThreadPoolLogging = flowInitContext.getMonitorProperties().getDefaultOptions().isEnableThreadPoolLogger();
        this.enableQueueLogging = flowInitContext.getMonitorProperties().getDefaultOptions().isEnableQueueLogger();

        this.threadPoolThreshold = flowInitContext.getMonitorProperties().getThreadPoolThreshold();
        this.filterMetricsThreshold = flowInitContext.getMonitorProperties().getModuleMetricsThreshold();
        this.nettyEventLoopThreshold = flowInitContext.getMonitorProperties().getNettyEventLoopThreshold();
        this.connectionPoolThreshold = flowInitContext.getMonitorProperties().getConnectionPoolThreshold();
        this.queueThreshold = flowInitContext.getMonitorProperties().getQueueMonitorThreshold();

        this.sharedScheduler = flowInitContext.getSharedPools().getSharedScheduledPool();

        this.monitorInitContextBuilder = MonitorInitContext.builder()
                .flowName(flowInitContext.getFlowName())
                .flowLabel(flowInitContext.getFlowLabel())
                .sharedScheduler(flowInitContext.getSharedPools().getSharedScheduledPool())
                .meterRegistry(flowInitContext.getMeterRegistry());
    }

    /**
     * Initializes thread pool monitoring for the flow executor.
     */
    public void initialiseThreadPoolMonitoring(ThreadPoolTaskExecutor flowExecutor) {
        if (this.enableThreadPoolLogging) {
            ThreadPoolStateTracker threadPoolStateTracker = new ThreadPoolStateTracker(flowExecutor);
            this.threadPoolLogger = new ThreadPoolMonitorLogger(this.monitorInitContextBuilder.build(), threadPoolStateTracker, threadPoolThreshold);
        }
    }

    /**
     * Initializes metrics and thread logging for Ingress and all extracted Interceptors.
     */
    public void initialisePluginsMonitoring(FlowInitContext flowInitContext) {
        Ingress ingress = flowInitContext.getIngress();
        List<GatewayInterceptor> flatFilters = new java.util.ArrayList<>();

        // Extract filters from the main pipeline
        if (flowInitContext.getRootPipeline() != null) {
            for (Processor rootNode : flowInitContext.getRootPipeline()) {
                flatFilters.addAll(rootNode.extractFilters());
            }
        }

        // Extract filters from all error pipelines (multistage)
        if (flowInitContext.getErrorPipelines() != null) {
            for (List<Processor> errorPipeline : flowInitContext.getErrorPipelines().values()) {
                if (errorPipeline != null) {
                    for (Processor errorNode : errorPipeline) {
                        flatFilters.addAll(errorNode.extractFilters());
                    }
                }
            }
        }

        if (this.enableFilterMetricsLogging) {
            for (GatewayInterceptor filter : flatFilters) {
                ModuleMetricsAggregatorLogger logger = new ModuleMetricsAggregatorLogger(
                        this.monitorInitContextBuilder
                                .moduleName(filter.getPluginName())
                                .moduleLabel(filter.getPluginLabel())
                                .build(),
                        filter.getMetricsAggregator(), filterMetricsThreshold);
                this.metricsAggregatorLoggers.add(logger);
            }

            this.metricsAggregatorLoggers.add(new ModuleMetricsAggregatorLogger(
                    this.monitorInitContextBuilder
                            .moduleName(ingress.getPluginName())
                            .moduleLabel(ingress.getPluginLabel())
                            .build(),
                    ingress.getMetricsAggregator(), filterMetricsThreshold)
            );
        }

        if (this.enableThreadPoolLogging) {
            for (GatewayInterceptor filter : flatFilters) {
                if (filter instanceof Upstream) {
                    List<AbstractMonitorLogger> outLoggers = ((Upstream) filter).setupAndRegisterNettyMonitor(nettyEventLoopThreshold, connectionPoolThreshold);
                    if (outLoggers != null) {
                        endpointMonitorLoggers.addAll(outLoggers);
                    }
                }
            }
            if (ingress != null) {
                List<AbstractMonitorLogger> inLoggers = ingress.setupAndRegisterNettyMonitor(nettyEventLoopThreshold, connectionPoolThreshold);
                if (inLoggers != null) {
                    endpointMonitorLoggers.addAll(inLoggers);
                }
            }
        }
    }

    /**
     * Registers a queue for monitoring.
     */
    public void addQueueMonitoring(String queueName, IQueueStatusProvider queueStatusProvider) {
        if (this.enableQueueLogging) {
            MonitorInitContext context = this.monitorInitContextBuilder
                    .moduleName(queueName + "-queue")
                    .build();

            QueueMonitorLogger logger = new QueueMonitorLogger(context, queueStatusProvider, this.queueThreshold);
            this.queueLoggers.add(logger);
        }
    }

    /**
     * Starts all registered monitoring tasks.
     * Includes a slight delay if the shared pool was recently refreshed.
     */
    public void startMonitoringTasks() {
        if (sharedScheduler.isShutdown() || sharedScheduler.isTerminated()) {
            log.warn("[{}] Shared Scheduler Pool is terminated during Flow start. Waiting for a new pool instance...", flowName);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (this.threadPoolLogger != null) {
            if (!(sharedScheduler.isShutdown() || sharedScheduler.isTerminated())) {
                this.threadPoolLogger.start();
            } else {
                log.error("[{}] Failed to start ThreadPool monitor: Shared Scheduler remains terminated.", flowName);
            }
        }

        for (QueueMonitorLogger logger : this.queueLoggers) {
            logger.start();
        }

        for (ModuleMetricsAggregatorLogger logger : this.metricsAggregatorLoggers) {
            if (logger != null) {
                if (!(sharedScheduler.isShutdown() || sharedScheduler.isTerminated())) {
                    logger.start();
                } else {
                    log.error("[{}] Failed to start ModuleMetrics monitor: Shared Scheduler remains terminated.", flowName);
                }
            }
        }

        for (AbstractMonitorLogger logger : this.endpointMonitorLoggers) {
            if (logger != null) {
                if (!(sharedScheduler.isShutdown() || sharedScheduler.isTerminated())) {
                    logger.start();
                } else {
                    log.error("[{}] Failed to start Endpoint monitor: Shared Scheduler remains terminated.", flowName);
                }
            }
        }
    }

    public void resetAll() {
        if (this.threadPoolLogger != null) this.threadPoolLogger.reset();
        this.queueLoggers.forEach(AbstractMonitorLogger::reset);
        this.metricsAggregatorLoggers.forEach(AbstractMonitorLogger::reset);
        this.endpointMonitorLoggers.forEach(AbstractMonitorLogger::reset);
    }

    /**
     * Stops all loggers and releases monitoring resources.
     */
    public void shutdown() {
        if (this.threadPoolLogger != null) {
            this.threadPoolLogger.shutdown();
        }

        for (QueueMonitorLogger logger : this.queueLoggers) {
            if (logger != null) logger.shutdown();
        }
        this.queueLoggers.clear();

        for (ModuleMetricsAggregatorLogger logger : this.metricsAggregatorLoggers) {
            if (logger != null) logger.shutdown();
        }
        this.metricsAggregatorLoggers.clear();

        for (AbstractMonitorLogger logger : this.endpointMonitorLoggers) {
            if (logger != null) logger.shutdown();
        }
        this.endpointMonitorLoggers.clear();

        log.info("[{}] Monitoring resources shut down.", flowName);
    }
}
