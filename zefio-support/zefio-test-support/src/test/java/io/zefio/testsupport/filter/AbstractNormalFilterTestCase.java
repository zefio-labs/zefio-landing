package io.zefio.testsupport.filter;

import io.zefio.core.GatewayInterceptor;
import io.zefio.core.payload.Payload;
import io.zefio.testsupport.payload.AbstractTestCase;
import org.apache.commons.lang3.ObjectUtils;
import org.javatuples.Pair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test case for validating individual GatewayInterceptors (Filters).
 * Provides a standardized environment for testing payload transformations
 * and property extractions within the pipeline.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractNormalFilterTestCase extends AbstractTestCase {

    protected final String filterName;
    protected Charset filterEncoding;

    public AbstractNormalFilterTestCase(String filterName) throws Exception {
        super();
        this.filterName = filterName;
    }

    public AbstractNormalFilterTestCase(String yamlFile, String filterName) throws Exception {
        super(yamlFile);
        this.filterName = filterName;
    }

    protected GatewayInterceptor filter;

    /**
     * Factory method to instantiate the specific filter under test.
     */
    public abstract GatewayInterceptor createFilter(Map<String, Object> context);

    @BeforeEach
    @DisplayName("Initialize payload factory and filter instance")
    protected void setup() throws Exception {
        initSenderFactoryBuilder();

        Map<String, Object> context = getContext(filterName);
        this.filterEncoding = ObjectUtils.isEmpty(context.get("requestEncoding")) ?
                StandardCharsets.UTF_8 : Charset.forName(context.get("requestEncoding").toString());

        filter = createFilter(context);
        filter.initialise();
    }

    @AfterAll
    void shutdown() {
        if (filter != null) filter.close();
    }

    /**
     * Executes the filter and asserts that the resulting payload and its body are not null.
     */
    protected Payload executeAssert(Payload requestPayload) throws Exception {
        filter.executeAsync(requestPayload, Executors.newSingleThreadExecutor()).join();

        assertNotNull(requestPayload);
        assertNotNull(requestPayload.getBody());

        return requestPayload;
    }

    /**
     * Executes the filter and validates if the resulting body matches the expected string.
     */
    protected Payload executeAssertBodyEquals(Payload requestPayload, String expectedBody) throws Exception {
        filter.executeAsync(requestPayload, Executors.newSingleThreadExecutor()).join();

        assertNotNull(requestPayload);
        assertNotNull(requestPayload.getBody());

        String actualResponse = new String(requestPayload.getBody(), filterEncoding);
        assertEquals(expectedBody, actualResponse);

        return requestPayload;
    }

    /**
     * Executes the filter and validates both the body and specific header properties.
     */
    protected void executeAssertBodyPropertyEquals(Payload requestPayload, String expectedBody,
                                                   List<Pair<String, String>> propertyPairs) throws Exception {
        requestPayload = executeAssertBodyEquals(requestPayload, expectedBody);

        for (Pair<String, String> propertyPair : propertyPairs) {
            byte[] extractedPropertyValue = (byte[]) requestPayload.getHeader(propertyPair.getValue0());
            assertNotNull(extractedPropertyValue, "Property not found: " + propertyPair.getValue0());
            assertEquals(propertyPair.getValue1(), new String(extractedPropertyValue, filterEncoding));
        }
    }

    protected void executeAssertBodyPropertyEquals(Payload requestPayload, String expectedBody,
                                                   Pair<String, String> propertyPair) throws Exception {
        executeAssertBodyPropertyEquals(requestPayload, expectedBody,
                Collections.singletonList(Pair.with(propertyPair.getValue0(), propertyPair.getValue1())));
    }

    /**
     * Asserts that the filter execution throws a specific exception type.
     */
    protected void executeAssertThrows(Class<? extends Exception> expectedType, Payload requestPayload) {
        assertThrows(expectedType, () -> filter.executeAsync(requestPayload, Executors.newSingleThreadExecutor()).join());
    }
}
