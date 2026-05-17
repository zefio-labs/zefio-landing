package io.zefio.testsupport.payload;

import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.*;
import io.zefio.core.payload.util.TelegramFactory;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Factory for creating XML-formatted test payloads.
 * Provides automated tag construction and delimiter synchronization for Ingress testing.
 */
public class XmlPayloadBuilderFactory extends AbstractPayloadBuilderFactory implements IPayloadBuilderFactory {

    private final PayloadBuilder builder;
    private final Charset charset;
    private int totalSize = 500;
    private String delimiter = "";

    public XmlPayloadBuilderFactory(PayloadBuilder builder, Charset charset) {
        TelegramFactory.register(builder.getTelegram().getName(), builder);
        this.builder = builder;
        this.charset = charset != null ? charset : StandardCharsets.UTF_8;
    }

    public XmlPayloadBuilderFactory(PayloadBuilder builder, Charset charset, int totalSize) {
        this(builder, charset);
        this.totalSize = totalSize;
    }

    /**
     * Static helper to create a standard XML factory with SpEL correlation.
     */
    public static XmlPayloadBuilderFactory createStandardFactory(String telegramName, Charset charset, int totalSize, String delimiter)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {

        FramingField framing = new FramingField();
        framing.setType(FramingType.Delimiter);
        framing.setDelimiter(delimiter);

        CorrelationField correlation = new CorrelationField(CorrelationIdType.SpEL);
        correlation.setExpression("#{body['txnId']}");

        PayloadBuilder builder = new Telegram.Builder()
                .name(telegramName)
                .type(Telegram.Type.XML)
                .values(XmlValues.builder()
                        .framing(framing)
                        .correlation(correlation)
                        .encodingIgnore(false)
                        .build())
                .build();

        XmlPayloadBuilderFactory factory = new XmlPayloadBuilderFactory(builder, charset, totalSize);
        factory.delimiter = delimiter;
        return factory;
    }

    @Override
    public PayloadBuilder buildEventBuilder() {
        return builder;
    }

    /**
     * Constructs the physical XML message byte array.
     */
    @Override
    public byte[] buildMessage() {
        String txnId = createRandomString(20);

        // 1. Construct XML tags
        StringBuilder xmlBuilder = new StringBuilder();
        xmlBuilder.append("<request>\n");
        xmlBuilder.append("  <txnId>").append(txnId).append("</txnId>\n");

        for (int i = 0; i < 3; i++) {
            String tag = "field" + (i + 1);
            String value = createRandomString(10);
            xmlBuilder.append("  <").append(tag).append(">").append(value).append("</").append(tag).append(">\n");
        }
        xmlBuilder.append("</request>");

        byte[] xmlBytes = xmlBuilder.toString().getBytes(charset);
        int bodyLength = totalSize;

        if (xmlBytes.length > bodyLength) {
            throw new IllegalArgumentException("Generated XML exceeds the maximum body size defined.");
        }

        // 2. Body allocation and synchronization
        byte[] bodyBytes = new byte[bodyLength];
        System.arraycopy(xmlBytes, 0, bodyBytes, 0, xmlBytes.length);
        for (int i = xmlBytes.length; i < bodyBytes.length; i++) {
            bodyBytes[i] = ' ';
        }

        // 3. Automated delimiter merging
        byte[] delimBytes = delimiter.getBytes(charset);
        byte[] result = new byte[bodyBytes.length + delimBytes.length];
        System.arraycopy(bodyBytes, 0, result, 0, bodyBytes.length);
        System.arraycopy(delimBytes, 0, result, bodyBytes.length, delimBytes.length);

        return result;
    }
}
