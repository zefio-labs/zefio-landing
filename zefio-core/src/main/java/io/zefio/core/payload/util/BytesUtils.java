package io.zefio.core.payload.util;

import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ZefioMessage;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;

/**
 * High-performance byte utility class for payload manipulation.
 * Optimized for low-latency processing by minimizing object allocation and
 * utilizing lazy transcoding strategies.
 */
public class BytesUtils {
	private final static Logger log = LoggerFactory.getLogger(BytesUtils.class);

	public static byte[] bytesOffsetCopy(byte[] source, int startOffset, int length) {
		byte[] target = new byte[length];
		System.arraycopy(source, startOffset, target, 0, target.length);
		return target;
	}

	public static byte[] bytesMerge(byte[] firstSource, byte[] secondSource) {
		byte[] copyBytes = new byte[firstSource.length + secondSource.length];
		System.arraycopy(firstSource, 0, copyBytes, 0, firstSource.length);
		System.arraycopy(secondSource, 0, copyBytes, firstSource.length, secondSource.length);
		return copyBytes;
	}

	/**
	 * Records an integer (Length) directly as ASCII bytes into the target array.
	 * This prevents GC pressure by avoiding intermediate String conversions.
	 */
	private static void formatLengthToAsciiBytes(int length, int lengthSize, byte[] target, int offset) {
		for (int i = lengthSize - 1; i >= 0; i--) {
			target[offset + i] = (byte) ('0' + (length % 10));
			length /= 10;
		}
	}

	/**
	 * Updates the existing length header within the provided body array.
	 * Used when the buffer already contains space for the header.
	 */
	public static byte[] updateLength(byte[] body, int lengthSize, boolean includeLength) {
		if (lengthSize <= 0 || body.length < lengthSize) return body;

		int targetLen = includeLength ? body.length : (body.length - lengthSize);

		byte[] result = new byte[body.length];
		// Copy existing data area, leaving the header space
		System.arraycopy(body, lengthSize, result, lengthSize, body.length - lengthSize);
		// Directly inject ASCII length bytes into the header space
		formatLengthToAsciiBytes(targetLen, lengthSize, result, 0);

		return result;
	}

	/**
	 * Appends a length header to the front of the body array.
	 * Allocates a single new array to combine the header and data.
	 */
	public static byte[] appendLength(byte[] body, int lengthSize, boolean includeLength) {
		if (lengthSize <= 0) return body;

		int targetLen = includeLength ? (body.length + lengthSize) : body.length;

		byte[] result = new byte[lengthSize + body.length];
		// Copy body data into the area following the header
		System.arraycopy(body, 0, result, lengthSize, body.length);
		// Inject length bytes at the start of the new buffer
		formatLengthToAsciiBytes(targetLen, lengthSize, result, 0);

		return result;
	}

	/**
	 * Physically converts bytes from one charset to another.
	 */
	public static byte[] changeEncoding(byte[] body, Charset source, Charset target){
		if (source != null && target != null && !source.equals(target)) {
			return new String(body, source).getBytes(target);
		} else {
			return body;
		}
	}

	/**
	 * Updates the payload's encoding metadata for lazy transcoding.
	 * In ZefioMessage, the actual byte conversion is deferred until getBody() is called,
	 * significantly improving efficiency during multi-stage processing.
	 */
	public static void changeEncoding(Payload payload, Charset target){
		Charset source = payload.getCurrentEncoding();

		if (ObjectUtils.allNotNull(source, target) && !source.equals(target)) {
			// Lazy Transcoding: Just update metadata and let ZefioMessage handle the 'Bake' later
			if (payload instanceof ZefioMessage) {
				payload.setCurrentEncoding(target);
				if (log.isDebugEnabled()) {
					log.debug("[Lazy Transcoding] Encoding metadata updated: [{}] -> [{}]", source, target);
				}
			} else {
				// Fallback for legacy Payload implementations: Physical conversion
				byte[] body = payload.getBody();
				if (body != null && body.length > 0) {
					byte[] convertedBody = changeEncoding(body, source, target);
					payload.setCurrentEncoding(target);
					payload.setBody(convertedBody);
					if (log.isDebugEnabled()) {
						log.debug("[Physical Transcoding] Bytes converted: [{}] -> [{}], Size: {}", source, target, convertedBody.length);
					}
				}
			}
		}
	}
}
