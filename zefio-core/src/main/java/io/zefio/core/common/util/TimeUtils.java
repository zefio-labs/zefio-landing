package io.zefio.core.common.util;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * High-performance time utility class. It provides thread-safe DateTimeFormatters
 * for logging and database operations using modern Java Time API, along with
 * legacy support for SimpleDateFormat-based timestamping.
 */
public class TimeUtils {

	public static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

	public static final DateTimeFormatter DB_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneId.systemDefault());

	public static String formatLogTime(long epochMillis) {
		return LOG_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
	}

	public static String formatDbTime(long epochMillis) {
		return DB_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
	}

	public static String timestamp() {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
		Date time = new Date();
		return format.format(time);
	}

	public static String timestamp(String timeFormat) {
		SimpleDateFormat format = new SimpleDateFormat(timeFormat);
		Date time = new Date();
		return format.format(time);
	}

	public static String timestamp(String timeFormat, long time) {
		SimpleDateFormat format = new SimpleDateFormat(timeFormat);
		return format.format(new Date(time));
	}

	public static String timestampYesterday(String timeFormat) {
		SimpleDateFormat format = new SimpleDateFormat(timeFormat);
		Date currentTime = new Date();
		Date yesterdayTime = new Date(currentTime.getTime() + (1000 * 60 * 60 * 24 * -1));
		return format.format(yesterdayTime);
	}

	public static String formatElapsedTimeWithMillis(long elapsedMillis) {
		long hours = elapsedMillis / 3600000;
		long minutes = (elapsedMillis % 3600000) / 60000;
		long seconds = (elapsedMillis % 60000) / 1000;
		long millis = elapsedMillis % 1000;

		return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
	}
}
