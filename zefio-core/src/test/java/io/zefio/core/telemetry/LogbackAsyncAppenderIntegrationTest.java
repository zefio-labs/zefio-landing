package io.zefio.core.telemetry;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Logback AsyncAppender Integration Test
 * Verifies the initialization, capacity, and saturation behavior of the asynchronous logging queue.
 */
class LogbackAsyncAppenderIntegrationTest {

    private static final String ASYNC_APPENDER_NAME = "ASYNC_FILE";
    private Logger testLogger;
    private AsyncAppender asyncAppender;
    private int queueSize;

    // Forcefully loads the Logback Context before all tests begin.
    @BeforeAll
    static void initializeLogback() throws Exception {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        // Logback Context reset (Mandatory)
        context.reset();

        // 1. Create JoranConfigurator instance
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);

        // 2. Obtain logback-spring.xml file URL
        URL configFileUrl = LogbackAsyncAppenderIntegrationTest.class.getClassLoader().getResource("logback-spring.xml");

        if (configFileUrl == null) {
            throw new IllegalStateException("logback-spring.xml file could not be found in the classpath.");
        }

        try {
            // 3. Load XML configuration file
            configurator.doConfigure(configFileUrl);

        } catch (JoranException e) {
            // Handle configuration file parsing errors
            throw new RuntimeException("Error occurred while loading Logback configuration file: " + configFileUrl, e);
        }

        // Start the Logger Context to ensure the loaded Appender starts properly
        context.start();
        System.out.println("Logback context manually initialized successfully using JoranConfigurator.");
    }

    // Clean up Logback Context after all tests finish
    @AfterAll
    static void cleanupLogback() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.stop();
        System.out.println("Logback context manually stopped.");
    }

    @BeforeEach
    void setUp() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        // Ensure Root Logger level is DEBUG (following logback-spring.xml settings)
        ch.qos.logback.classic.Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.DEBUG);

        // Obtain AsyncAppender instance (Should succeed since it was loaded in BeforeAll)
        asyncAppender = (AsyncAppender) rootLogger.getAppender(ASYNC_APPENDER_NAME);

        // Core validation: Ensure the Appender is not null
        assertNotNull(asyncAppender, "ASYNC_FILE Appender was not loaded even after manual initialization.");

        queueSize = asyncAppender.getQueueSize();
        testLogger = LoggerFactory.getLogger(LogbackAsyncAppenderIntegrationTest.class);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // Wait briefly for all queued tasks to complete after testing
        TimeUnit.MILLISECONDS.sleep(100);
    }

    @Test
    @DisplayName("Verify AsyncAppender initializes with the correct queue size")
    void testAsyncAppenderInitialization() {
        assertEquals(512, queueSize, "AsyncAppender queue size should be 512.");
    }

    @Test
    @DisplayName("Verify normal logging when the queue is not saturated")
    void testNormalLogging() {
        int initialRemainingCapacity = asyncAppender.getRemainingCapacity();
        assertTrue(initialRemainingCapacity > 0);

        int logCount = queueSize / 4;

        for (int i = 0; i < logCount; i++) {
            testLogger.info("Test Log - Normal {}", i);
        }

        // Since processing is asynchronous, verify only that remaining capacity has decreased from initial capacity
        int remainingCapacityAfterLogging = asyncAppender.getRemainingCapacity();
        assertTrue(remainingCapacityAfterLogging < initialRemainingCapacity,
                "Queue remaining capacity must decrease after sending logs.");
    }

    @Test
    @DisplayName("Indirectly verify log loss during queue saturation")
    void testQueueSaturationAndLogLoss() throws InterruptedException {

        int initialRemainingCapacity = asyncAppender.getRemainingCapacity();
        int logsToSend = queueSize + 10; // Send 10 more than the queue capacity (512)

        System.out.println("\n--- Starting Queue Saturation Test ---");

        for (int i = 0; i < logsToSend; i++) {
            // Logback may block the calling thread without discarding INFO level logs upon queue saturation.
            // DEBUG logs are highly likely to be discarded.
            testLogger.debug("DEBUG log: Sending Burst {}", i);
        }

        int remainingCapacityAfterBurst = asyncAppender.getRemainingCapacity();

        System.out.println("Logs Sent: " + logsToSend);
        System.out.println("Initial Capacity: " + initialRemainingCapacity);
        System.out.println("Remaining Capacity After Burst: " + remainingCapacityAfterBurst);

        // Verify if the queue remaining capacity differs from the initial capacity.
        assertNotEquals(initialRemainingCapacity, remainingCapacityAfterBurst,
                "Queue state must change after sending logs (should not be 0.00%).");

        // If the queue is completely emptied, remainingCapacityAfterBurst becomes equal to queueSize.
        // Indirectly verify that it briefly went through a saturated state before completely emptying.

        // e.g. Number of logs stacked in queue = 512 - 323 = 189
        int logsInQueue = queueSize - remainingCapacityAfterBurst;

        // Logs stacked in the queue must not be zero and must be less than the number of logs sent.
        assertTrue(logsInQueue > 0, "Logs must be stacked in the queue.");
        assertTrue(logsInQueue <= queueSize, "Logs stacked in the queue cannot exceed queue size.");
    }

//    <dependency>
//        <groupId>ch.qos.logback</groupId>
//        <artifactId>logback-jmx</artifactId>
//        <version>1.5.18</version>
//    </dependency>
//    @Test
//    @DisplayName("Verify AsyncAppender MBean registration in Logback JMX MBeanServer")
//    void testJmxRegistration() throws Exception {
//        // 1. Obtain JVM MBean Server
//        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
//
//        // 2. Obtain LoggerContext name
//        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
//        String contextName = context.getName(); // Usually 'default' in Spring Boot environments
//
//        // Debugging Tip: Verify if Logback Context has fully started
//        if (!context.isStarted()) {
//            context.start();
//        }
//
//        // 3. Define ObjectName pattern for AsyncAppender MBean
//        // Logback's ObjectName standard pattern includes Name=<LoggerContextName>.
//        // ex: ch.qos.logback.classic:Name=default,Type=AsyncAppender,appenderName=ASYNC_FILE,*
//
//        // Note: Modify pattern since Context Name generally goes into the 'Name' field of ObjectName.
//        // Original pattern: "ch.qos.logback.classic:Type=AsyncAppender,appenderName=ASYNC_FILE,*"
//        // Modified pattern: "ch.qos.logback.classic:Name=" + contextName + ",Type=AsyncAppender,appenderName=ASYNC_FILE,*"
//
//        ObjectName pattern = new ObjectName(
//                "ch.qos.logback.classic:Name=" + contextName +
//                        ",Type=AsyncAppender,appenderName=" + ASYNC_APPENDER_NAME + ",*"
//        );
//
//        // 4. Verify MBean registration status
//        Set<ObjectName> mbeanNames = mbs.queryNames(pattern, null);
//        int count = mbeanNames.size();
//
//        System.out.println("LoggerContext Name: " + contextName);
//        System.out.println("Query Pattern: " + pattern.toString());
//        System.out.println("Found AsyncAppender MBeans (via JMX): " + count);
//
//        // Validation: At least one AsyncAppender MBean must be registered.
//        assertTrue(count >= 1,
//                "AsyncAppender's JMX MBean is not registered in MBeanServer. (Context Name: " + contextName + ")");
//    }
//
//    @Test
//    @DisplayName("Verify ability to read queueSize attribute of AsyncAppender via JMX")
//    void testJmxReadQueueSizeAttribute() throws Exception {
//        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
//
//        // 1. Obtain ObjectName of AsyncAppender MBean (Assuming single MBean)
//        ObjectName pattern = new ObjectName(
//                "ch.qos.logback.classic:Type=AsyncAppender,appenderName=" + ASYNC_APPENDER_NAME + ",*"
//        );
//
//        ObjectName asyncAppenderMBeanName = mbs.queryNames(pattern, null).stream()
//                .findFirst()
//                .orElseThrow(() -> new IllegalStateException("AsyncAppender MBean could not be found."));
//
//        // 2. Read 'QueueSize' attribute value via MBean
//        Object queueSizeFromJmx = mbs.getAttribute(asyncAppenderMBeanName, "QueueSize");
//
//        // 3. Validation: Check if value read via JMX matches the configuration file value
//        assertNotNull(queueSizeFromJmx, "Cannot read QueueSize attribute value from JMX.");
//
//        // JMX attribute values are typically returned as Long types.
//        assertEquals(512L, ((Number) queueSizeFromJmx).longValue(), "Queue size read via JMX should be 512.");
//
//        System.out.println("QueueSize read from JMX: " + queueSizeFromJmx);
//    }
}
