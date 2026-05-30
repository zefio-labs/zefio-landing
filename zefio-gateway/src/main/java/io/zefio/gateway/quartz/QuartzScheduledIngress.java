package io.zefio.gateway.quartz;

import io.zefio.core.ReactiveIngress;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.factory.PluginContext;
import io.zefio.gateway.quartz.dto.QuartzIngressValues;
import io.zefio.gateway.quartz.base.QuartzIngressObject;
import io.zefio.gateway.quartz.job.ZefioFlowTriggerJob;
import org.apache.commons.lang3.StringUtils;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Enterprise Specification Scheduled Ingress Core Component driven by Quartz Engine.
 * Implements SEDA Event Generation model to prevent worker starvation and decouple time-trigger steps.
 * Fully compatible with JDK 1.8 and Quartz 2.4 framework specifications.
 */
public class QuartzScheduledIngress extends ReactiveIngress {

    private final QuartzIngressValues ingressValues;
    private Scheduler scheduler;

    public QuartzScheduledIngress(PluginContext context) {
        super(context);
        // Deserializes and maps JSON/YAML DSL configuration maps natively into annotated values DTO
        this.ingressValues = yamlMapper.convertValue(context.getContext(), QuartzIngressValues.class);
    }

    @Override
    public String getDescription() {
        return "Zefio Open-Source Middleware - Asynchronous SEDA Event Trigger Ingress";
    }

    @Override
    public void initialise() throws Exception {
        super.initialise();

        String activeCron = ingressValues.getActiveCron();
        if (StringUtils.isBlank(activeCron)) {
            throw new FlowException(FlowResultStatus.INTERNAL_SERVER_ERROR,
                    "[" + pluginName + "] Critical Setup Failure: Scheduler timing token (cronExpression) is null.");
        }

        log.info("{} Quartz driver subsystem configuration initializing...", logHeader);
        Properties properties = new Properties();

        // Dynamically provisions infrastructure properties depending on external configuration bindings
        if (StringUtils.isNotBlank(ingressValues.getConfig())) {
            PropertiesFactoryBean propertiesFactoryBean = new PropertiesFactoryBean();
            propertiesFactoryBean.setLocation(new ClassPathResource(ingressValues.getConfig()));
            propertiesFactoryBean.afterPropertiesSet();
            properties = propertiesFactoryBean.getObject();
        } else {
            // High-throughput optimized embedded defaults tailored for lean asynchronous offloading topologies
            properties.put("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
            properties.put("org.quartz.threadPool.threadCount", "4");
            properties.put("org.quartz.scheduler.instanceName", "Zefio-Reactive-Quartz-Engine-" + pluginName);
        }

        SchedulerFactory schedulerFactory = new StdSchedulerFactory(properties);
        this.scheduler = schedulerFactory.getScheduler();
    }

    @Override
    protected void doStart() throws Exception {
        try {
            this.scheduler.start();

            JobKey jobKey = new JobKey("zefio.seda.trigger." + pluginName, "ZEFIO_CORE_GROUP");
            JobDataMap jobDataMap = new JobDataMap();

            // Packages context wrappers substituting the old inbound reference names into the modern Ingress layout
            jobDataMap.put("base", new QuartzIngressObject(ingressValues.getValue(), this, requestEncoding));

            // Binds the decoupled internal framework worker instance ensuring zero telemetry latency overhead
            JobDetail jobDetail = JobBuilder.newJob(ZefioFlowTriggerJob.class)
                    .withIdentity(jobKey)
                    .setJobData(jobDataMap)
                    .build();

            // Configures standard cron schedule schedule maps alongside precision error instructions rules
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(ingressValues.getActiveCron());
            scheduleBuilder = configureMisfirePolicy(scheduleBuilder);

            Trigger cronTrigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger_" + jobKey.getName(), "ZEFIO_CORE_GROUP")
                    .withSchedule(scheduleBuilder)
                    .forJob(jobDetail)
                    .build();

            // 🚀 Remedy: Instantiate a JDK 1.8 compliant HashSet to aggregate multiple triggers atomically
            Set<Trigger> triggersForJob = new HashSet<Trigger>();
            triggersForJob.add(cronTrigger);

            // Conditionally evaluate cold-start protection parameters to append an immediate startNow trigger vector
            if (ingressValues.isFireOnStartup()) {
                log.info("{} Cold-start defense active. Accumulating initialization startup trigger...", logHeader);

                Trigger immediateBootTrigger = TriggerBuilder.newTrigger()
                        .withIdentity("immediate_startup_trigger_" + jobKey.getName(), "ZEFIO_CORE_GROUP")
                        .startNow() // Executes instantly upon framework bootstrapping
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withRepeatCount(0)) // Strict rule: Fires exactly ONCE and terminates instantly
                        .forJob(jobDetail)
                        .build();

                triggersForJob.add(immediateBootTrigger);
            }

            // 🚀 Remedy: Utilizes native Quartz 2.x multi-trigger atomic orchestration matrix registration contract.
            // scheduleJob(JobDetail, Set<Trigger>, boolean replace) maps beautifully within standard legacy boundaries.
            this.scheduler.scheduleJob(jobDetail, triggersForJob, true);

            log.info("{} Quartz Scheduled Ingress activated successfully with target CRON expression [{}].", logHeader, ingressValues.getActiveCron());
            if (ingressValues.isFireOnStartup()) {
                log.info("{} Cold-start warmup transaction scheduled successfully alongside baseline cron schedules.", logHeader);
            }

        } catch (Exception e) {
            logFatalStartupError(e);
            if (e instanceof FlowException) throw e;
            throw new FlowException(e, FlowResultStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Resolves and appends misfire execution directives inside the underlying schedule builder context.
     */
    private CronScheduleBuilder configureMisfirePolicy(CronScheduleBuilder builder) {
        if (ingressValues.getMisfirePolicy() == null) return builder.withMisfireHandlingInstructionDoNothing();

        switch (ingressValues.getMisfirePolicy()) {
            case FIRE_NOW:
                return builder.withMisfireHandlingInstructionFireAndProceed();
            case DO_NOTHING:
                return builder.withMisfireHandlingInstructionDoNothing();
            case IGNORE_MISFIRES:
                return builder.withMisfireHandlingInstructionIgnoreMisfires();
            case SMART_POLICY:
            default:
                return builder; // Use standard framework smart defaults natively
        }
    }

    @Override
    public void close() {
        try {
            if (this.scheduler != null && !this.scheduler.isShutdown()) {
                // Blocks until currently executing triggers complete before shutting down cleanly
                this.scheduler.shutdown(true);
            }
        } catch (SchedulerException e) {
            log.error("{} Exception caught during quartz cluster shutdown timeline routine.", logHeader, e);
        }
        this.scheduler = null;
        super.close();
    }
}
