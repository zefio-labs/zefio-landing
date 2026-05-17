package io.zefio.core.telemetry;

import io.zefio.core.config.monitor.MonitorProperties;
import io.zefio.core.telemetry.provider.IMetricsResettable;
import io.zefio.core.telemetry.registry.MonitorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.TimeZone;
import java.util.concurrent.ScheduledFuture;

/**
 * Scheduler for resetting telemetry metrics and alert states.
 * Automatically updates schedules when configuration is refreshed (Hot-Deploy).
 */
@Component
public class MetricsResetScheduler implements InitializingBean, DisposableBean {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private GlobalMonitorManager globalMonitorManager;

    @Autowired
    private MonitorProperties monitorProperties;

    @Autowired
    private TaskScheduler taskScheduler;

    private ScheduledFuture<?> scheduledTask;

    @Override
    public void afterPropertiesSet() {
        scheduleTask("Startup");
    }

    /**
     * Re-registers the task when a configuration refresh event occurs.
     */
    @EventListener(RefreshScopeRefreshedEvent.class)
    public void onConfigRefresh(RefreshScopeRefreshedEvent event) {
        log.info("[Scheduler] Hot-Deploy detected. Re-calculating schedule...");
        scheduleTask("Hot-Deploy");
    }

    private synchronized void scheduleTask(String reason) {
        String cron = monitorProperties.getMetricsReset().getCron();
        String zone = monitorProperties.getMetricsReset().getZone();

        log.info("[Scheduler] [{}] Registering task. Cron: [{}] Zone: [{}]", reason, cron, zone);

        cancelTask();

        try {
            this.scheduledTask = taskScheduler.schedule(
                    this::metricsReset,
                    new CronTrigger(cron, TimeZone.getTimeZone(zone))
            );
        } catch (Exception e) {
            log.error("[Scheduler] Failed to schedule task with cron [{}]", cron, e);
        }
    }

    @Override
    public void destroy() {
        cancelTask();
    }

    private void cancelTask() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(true);
            log.info("[Scheduler] Previous reset task cancelled.");
        }
    }

    /**
     * Core execution logic for resetting all registered metrics and states.
     */
    public void metricsReset() {
        log.info("==========================================================");
        log.info(" 🔄 Starting Scheduled Metrics & Alert State Reset");
        log.info("==========================================================");

        try {
            globalMonitorManager.resetAll();
            MonitorRegistry.getAll().forEach(IMetricsResettable::resetMetrics);
            log.info(" ✅ All metrics reset successfully.");
        } catch (Exception e) {
            log.error(" ❌ Error occurred during metrics reset", e);
        }
        log.info("==========================================================");
    }
}
