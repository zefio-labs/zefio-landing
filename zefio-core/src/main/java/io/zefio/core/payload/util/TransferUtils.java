package io.zefio.core.payload.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zefio.core.common.util.TimeUtils;
import io.zefio.core.payload.Payload;
import org.apache.commons.lang3.ObjectUtils;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for format transformation and template string replacement.
 * Supports converting between XML, JSON, and Map structures, as well as
 * resolving placeholders for hostnames, transaction IDs, and dynamic date patterns.
 */
public class TransferUtils {

	private static final Logger log = LoggerFactory.getLogger(TransferUtils.class);
	private static final ObjectMapper mapper = new ObjectMapper();

	private static final String PLACEHOLDER_HOSTNAME = "{{hostname}}";
	private static final String PLACEHOLDER_SPACE = "{{space}}";
	private static final String PLACEHOLDER_CORRELATION_ID = "{{CorrelationID}}";
	private static final Pattern DATE_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

	/**
	 * Resolves placeholders in a template string using payload data and system properties.
	 * Supports {{hostname}}, {{space}}, {{CorrelationID}}, and various date patterns.
	 */
	public static String formConvertor(String value, Payload payload) throws UnknownHostException {
		if (value == null) return null;

		// 1. Static placeholder replacement
		if (value.contains(PLACEHOLDER_HOSTNAME)) {
			value = value.replace(PLACEHOLDER_HOSTNAME, InetAddress.getLocalHost().getHostName());
		}
		if (value.contains(PLACEHOLDER_SPACE)) {
			value = value.replace(PLACEHOLDER_SPACE, " ");
		}
		if (ObjectUtils.isNotEmpty(payload.getTrxID()) && value.contains(PLACEHOLDER_CORRELATION_ID)) {
			value = value.replace(PLACEHOLDER_CORRELATION_ID, payload.getTrxID());
		}

		// 2. Dynamic date pattern replacement (e.g., {{SYSDATE-YYYYMMDD}} or {{HHmmss}})
		StringBuilder sb = new StringBuilder();
		Matcher matcher = DATE_PATTERN.matcher(value);
		int lastIndex = 0;

		while (matcher.find()) {
			sb.append(value, lastIndex, matcher.start());
			String pattern = matcher.group(1).trim();

			String replacement;
			if (pattern.startsWith("SYSDATE-")) {
				replacement = TimeUtils.timestamp(pattern.substring("SYSDATE-".length()));
			} else {
				replacement = TimeUtils.timestamp(pattern);
			}

			sb.append(replacement);
			lastIndex = matcher.end();
		}
		sb.append(value.substring(lastIndex));

		return sb.toString();
	}

	public static Map<String, Object> xml2Map(String xml) throws Exception {
		JSONObject xmlJSONObj = XML.toJSONObject(xml);
		return json2Map(xmlJSONObj.toString());
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> json2Map(String json) throws Exception {
		return mapper.readValue(json, HashMap.class);
	}

	public static <K, V> String map2Json(Map<K, V> map) throws Exception {
		return mapper.writeValueAsString(map);
	}

	public static <K, V> String map2Xml(Map<K, V> map) throws Exception {
		String jsonData = map2Json(map);
		return XML.toString(new JSONObject(jsonData));
	}

	public static String json2Xml(String json) throws Exception {
		JSONObject jsonData = new JSONObject(json);
		return XML.toString(jsonData);
	}

	/**
	 * Navigates a nested Map structure using a dot-notated key path.
	 * Example: findValueFromMap(map, "header.common.trxId")
	 */
	@SuppressWarnings("unchecked")
	public static Object findValueFromMap(Map<String, Object> objectMap, String keyPath) {
		if (objectMap == null || keyPath == null || keyPath.isEmpty()) return null;

		String[] keys = keyPath.split("\\.");
		Object current = objectMap;

		for (String key : keys) {
			if (current instanceof Map) {
				current = ((Map<String, Object>) current).get(key);
			} else if (current instanceof List) {
				// Returns the list itself if the path points to a list element
				return current;
			} else {
				return null;
			}
		}

		return current;
	}
}
