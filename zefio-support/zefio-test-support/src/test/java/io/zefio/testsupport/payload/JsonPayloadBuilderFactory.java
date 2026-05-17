package io.zefio.testsupport.payload;

import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.*;
import io.zefio.core.payload.util.TelegramFactory;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for creating JSON-formatted test payloads.
 * Supports SpEL-based correlation ID extraction and delimiter-based framing.
 */
public class JsonPayloadBuilderFactory extends AbstractPayloadBuilderFactory implements IPayloadBuilderFactory {

    private final PayloadBuilder builder;
    private final Charset charset;
    private int totalSize = 500;
    private String delimiter = "";

    public JsonPayloadBuilderFactory(PayloadBuilder builder, Charset charset) {
        TelegramFactory.register(builder.getTelegram().getName(), builder);
        this.builder = builder;
        this.charset = charset != null ? charset : StandardCharsets.UTF_8;
    }

    public JsonPayloadBuilderFactory(PayloadBuilder builder, Charset charset, int totalSize) {
        this(builder, charset);
        this.totalSize = totalSize;
    }

    /**
     * Static helper to create a standard JSON factory with SpEL correlation.
     */
    public static JsonPayloadBuilderFactory createStandardFactory(String telegramName, Charset charset, int totalSize, String delimiter)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {

        // 1. Configure Delimiter-based Framing
        FramingField framing = new FramingField();
        framing.setType(FramingType.Delimiter);
        framing.setDelimiter(delimiter);

        // 2. Configure SpEL-based Correlation (extract 'txnId' from body)
        CorrelationField correlation = new CorrelationField(CorrelationIdType.SpEL);
        correlation.setExpression("#{body['txnId']}");

        // 3. Assemble Telegram and PayloadBuilder
        PayloadBuilder builder = new Telegram.Builder()
                .name(telegramName)
                .type(Telegram.Type.JSON)
                .values(JsonValues.builder()
                        .framing(framing)
                        .correlation(correlation)
                        .encodingIgnore(false)
                        .build())
                .build();

        JsonPayloadBuilderFactory factory = new JsonPayloadBuilderFactory(builder, charset, totalSize);
        factory.delimiter = delimiter;
        return factory;
    }

    @Override
    public PayloadBuilder buildEventBuilder() {
        return builder;
    }

    /**
     * Constructs the physical JSON message byte array.
     * Includes automated padding and delimiter merging.
     */
    @Override
    public byte[] buildMessage() {
        String txnId = createRandomString(20);

        // 1. Construct JSON Map with mandatory status field
        Map<String, Object> jsonMap = new LinkedHashMap<>();
        jsonMap.put("txnId", txnId);
        jsonMap.put("status", "req");

        for (int i = 0; i < 3; i++) {
            jsonMap.put("field" + (i + 1), createRandomString(10));
        }

        try {
            byte[] jsonBytes = mapper.writeValueAsString(jsonMap).getBytes(charset);
            int bodyLength = totalSize;

            if (jsonBytes.length > bodyLength) {
                throw new IllegalArgumentException("Generated JSON exceeds the maximum body size defined.");
            }

            // 2. Body allocation and padding
            byte[] bodyBytes = new byte[bodyLength];
            System.arraycopy(jsonBytes, 0, bodyBytes, 0, jsonBytes.length);

            for (int i = jsonBytes.length; i < bodyBytes.length; i++) {
                bodyBytes[i] = ' ';
            }

            // 3. Merge Delimiter: $L_{result} = L_{body} + L_{delimiter}$
            byte[] delimBytes = delimiter.getBytes(charset);
            byte[] result = new byte[bodyBytes.length + delimBytes.length];
            System.arraycopy(bodyBytes, 0, result, 0, bodyBytes.length);
            System.arraycopy(delimBytes, 0, result, bodyBytes.length, delimBytes.length);

            return result;

        } catch (Exception e) {
            throw new RuntimeException("Failed to construct JSON test message", e);
        }
    }
}
