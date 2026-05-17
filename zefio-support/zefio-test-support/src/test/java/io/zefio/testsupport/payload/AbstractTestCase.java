package io.zefio.testsupport.payload;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zefio.core.config.flow.StepConfiguration;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.testsupport.filter.FilterConfigLoader;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Base class for Zefio Core unit and integration tests.
 * Manages configuration loading for filters and initializes simulation factories
 * to mimic Ingress client behavior.
 */
public abstract class AbstractTestCase extends AbstractPayloadBuilderFactory {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    protected final ObjectMapper mapper = new ObjectMapper();
    protected FilterConfigLoader loader = new FilterConfigLoader();
    protected Map<String, StepConfiguration> configs;

    protected IPayloadBuilderFactory senderFactory;
    protected PayloadBuilder senderBuilder;

    /** Shared pools for simulating asynchronous SEDA processing within tests. */
    protected Pair<ScheduledExecutorService, ExecutorService> sharedPool = getSharedPool();

    public AbstractTestCase() throws Exception {
        this("test.yaml");
    }

    public AbstractTestCase(String yamlFile) throws Exception {
        configs = loader.load(yamlFile);
    }

    /**
     * Factory method to create the specific Ingress simulation builder for the test case.
     */
    public abstract IPayloadBuilderFactory createSenderBuilder() throws Exception;

    /**
     * Initializes the sender factory and prepares the event builder instance.
     */
    public void initSenderFactoryBuilder() throws Exception {
        senderFactory = createSenderBuilder();
        senderBuilder = senderFactory.buildEventBuilder();
    }

    /**
     * Generates a raw test message byte array through the simulation factory.
     */
    protected byte[] generateTestMessage() {
        return senderFactory.buildMessage();
    }

    protected Map<String, Object> getContext(String filterName) {
        return configs.get(filterName).getConfig();
    }

    /**
     * Retrieves the full step configuration for a specific filter.
     */
    protected StepConfiguration getStepConfig(String filterName) {
        return configs.get(filterName);
    }

    private Pair<ScheduledExecutorService, ExecutorService> getSharedPool() {
        ScheduledExecutorService newScheduler = Executors.newScheduledThreadPool(1);
        ExecutorService newIoPool = Executors.newFixedThreadPool(1);
        return Pair.with(newScheduler, newIoPool);
    }
}
