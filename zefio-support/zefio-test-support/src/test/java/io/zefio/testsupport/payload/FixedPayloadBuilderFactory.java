package io.zefio.testsupport.payload;

import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.*;
import io.zefio.core.payload.util.TelegramFactory;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Factory for creating fixed-length test payloads.
 * Automatically handles length-prefix headers and TrxID placement based on defined offsets.
 */
public class FixedPayloadBuilderFactory extends AbstractPayloadBuilderFactory implements IPayloadBuilderFactory {

    private final PayloadBuilder builder;
    private final Telegram telegram;
    private final Charset charset;
    private int totalSize = 500;

    public FixedPayloadBuilderFactory(PayloadBuilder builder, Charset charset) {
        // Register the builder to the framework registry upon instantiation
        TelegramFactory.register(builder.getTelegram().getName(), builder);
        this.builder = builder;
        this.telegram = builder.getTelegram();
        this.charset = charset != null ? charset : StandardCharsets.UTF_8;
    }

    public FixedPayloadBuilderFactory(PayloadBuilder builder, Charset charset, int totalSize) {
        this(builder, charset);
        this.totalSize = totalSize;
    }

    /**
     * Static helper to create a standard Fixed-length factory with default layout.
     */
    public static FixedPayloadBuilderFactory createStandardFactory(String telegramName, Charset charset, int totalSize)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {

        // 1. Configure standard Framing (4-byte length, including header)
        FramingField framing = new FramingField();
        framing.setType(FramingType.Length);
        framing.setLengthDataSize(4);
        framing.setLengthDataInclude(true);
        framing.setLengthDataUpdate(true);

        // 2. Configure standard Correlation (Offset 43, Length 20)
        CorrelationField correlation = new CorrelationField(CorrelationIdType.Offset);
        correlation.setStart(43);
        correlation.setLength(20);

        // 3. Assemble Telegram and PayloadBuilder
        PayloadBuilder builder = new Telegram.Builder()
                .name(telegramName)
                .type(Telegram.Type.Fixed)
                .values(FixedValues.builder()
                        .framing(framing)
                        .correlation(correlation)
                        .layout(createDefaultLayout())
                        .encodingIgnore(false)
                        .build())
                .build();

        return new FixedPayloadBuilderFactory(builder, charset, totalSize);
    }

    private static List<FixedValues.FixedField> createDefaultLayout() {
        List<FixedValues.FixedField> fields = new ArrayList<>();

        FixedValues.FixedField prefix = new FixedValues.FixedField();
        prefix.setName("PREFIX");
        prefix.setLength(3);
        fields.add(prefix);

        FixedValues.FixedField data = new FixedValues.FixedField();
        data.setName("DATA_AREA");
        data.setLength(40);
        fields.add(data);

        FixedValues.FixedField trxIdField = new FixedValues.FixedField();
        trxIdField.setName("GLOBAL_TRX_ID");
        trxIdField.setLength(20);
        fields.add(trxIdField);

        FixedValues.FixedField tail = new FixedValues.FixedField();
        tail.setName("TAIL_REMAINDER");
        tail.setLength(433);
        fields.add(tail);

        return fields;
    }

    @Override
    public PayloadBuilder buildEventBuilder() {
        return builder;
    }

    /**
     * Constructs the actual physical byte message for Ingress source simulation.
     */
    @Override
    public byte[] buildMessage() {
        TelegramValues telegramValues = telegram.getValues();
        CorrelationField correlation = telegramValues.getCorrelation();
        FramingField framing = telegramValues.getFraming();

        // 1. Calculate Header (Framing) parameters
        int lengthDataSize = 0;
        boolean lengthDataInclude = false;

        if (framing != null && framing.getType() == FramingType.Length) {
            lengthDataSize = framing.getLengthDataSize() != null ? framing.getLengthDataSize() : 0;
            lengthDataInclude = Boolean.TRUE.equals(framing.getLengthDataInclude());
        }

        // 2. Calculate Correlation (TrxID) parameters
        int txnIdStart;
        int txnIdLength;
        if (correlation.getType() == CorrelationIdType.Offset) {
            txnIdStart = correlation.getStart();
            txnIdLength = correlation.getLength();
        } else {
            txnIdStart = 30; // Default fallback
            txnIdLength = 20;
        }

        // 3. Initialize Physical Packet array
        int bodyLength = lengthDataInclude ? totalSize - lengthDataSize : totalSize;
        byte[] result = new byte[lengthDataSize + bodyLength];

        // 4. Fill buffer with safe ASCII characters
        fillSafeAscii(result);

        // 5. Generate and insert Length Header
        int totalLengthValue = lengthDataInclude ? result.length : bodyLength;
        String lengthStr = String.format("%0" + lengthDataSize + "d", totalLengthValue);
        byte[] lengthBytes = lengthStr.getBytes(charset);
        System.arraycopy(lengthBytes, 0, result, 0, Math.min(lengthBytes.length, lengthDataSize));

        // 6. Calculate physical offset for TrxID and insert
        int physicalTxnIdStart = lengthDataSize + txnIdStart;

        String txnId = createTxnId(txnIdLength);
        byte[] txnIdBytes = txnId.getBytes(charset);

        if (physicalTxnIdStart + txnIdLength <= result.length) {
            System.arraycopy(txnIdBytes, 0, result, physicalTxnIdStart, Math.min(txnIdBytes.length, txnIdLength));
        } else {
            throw new IllegalArgumentException(
                    String.format("ID placement exceeds message size. [Header:%d + Offset:%d + Len:%d > Total:%d]",
                            lengthDataSize, txnIdStart, txnIdLength, result.length));
        }

        return result;
    }

    /**
     * Fills the byte array with readable ASCII characters to simulate business data.
     */
    private void fillSafeAscii(byte[] data) {
        String pool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+";
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) pool.charAt(random.nextInt(pool.length()));
        }
    }
}
