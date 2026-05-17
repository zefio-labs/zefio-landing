package io.zefio.testsupport.payload;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Base factory for generating simulated transaction payloads.
 * Provides utilities to create randomized alphanumeric strings and raw byte bodies
 * for Ingress protocol testing.
 */
public abstract class AbstractPayloadBuilderFactory {
    protected final ObjectMapper mapper = new ObjectMapper();
    protected final Random RANDOM = new Random();
    private final String suffix = "req";

    /**
     * Generates a random alphanumeric Transaction ID.
     *
     * @param length Desired length of the ID.
     * @return Alphanumeric string.
     */
    protected String createTxnId(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Fills a byte array with random multi-byte characters while preserving specific regions.
     *
     * The fill logic ensures the total length does not exceed:
     * $$L_{limit} = Body.length - Suffix.length$$
     *
     * @param body The target byte array.
     * @param txnStart Starting index of the transaction ID region (to skip).
     * @param txnLength Length of the transaction ID region.
     * @param encoding Target charset for byte conversion.
     */
    protected void fillBodyRandom(byte[] body, int txnStart, int txnLength, Charset encoding) {
        String charset = "가나다라마바사아자차카타파하" +
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                "abcdefghijklmnopqrstuvwxyz" +
                "0123456789" +
                "!@#$%^&*()-_=+[]{};:'\",.<>?/|\\~";

        byte[] reqBytes = suffix.getBytes(encoding);
        int fillLimit = body.length - reqBytes.length;

        for (int i = 0; i < fillLimit; ) {
            // Skip the Transaction ID reserved region
            if (i >= txnStart && i < txnStart + txnLength) {
                i++;
                continue;
            }

            char ch = charset.charAt(RANDOM.nextInt(charset.length()));
            byte[] bytes = String.valueOf(ch).getBytes(encoding);

            if (i + bytes.length > fillLimit) break;

            System.arraycopy(bytes, 0, body, i, bytes.length);
            i += bytes.length;
        }

        // Append request suffix at the end of the data region
        System.arraycopy(reqBytes, 0, body, fillLimit, reqBytes.length);
    }

    protected String createRandomString(int length) {
        String charset = "가나다라마바사아자차카타파하ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(charset.charAt(RANDOM.nextInt(charset.length())));
        }
        return sb.toString();
    }
}
