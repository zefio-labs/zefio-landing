package io.zefio.core.common.util;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for managing MyBatis SqlSessionFactory instances.
 * It provides a thread-safe cache for factories and supports dynamic configuration
 * injection via environment variables for database connections.
 */
public class MybatisUtils {
	private static final Logger log = LoggerFactory.getLogger(MybatisUtils.class);

	private static final Map<String, SqlSessionFactory> factoryCache = new ConcurrentHashMap<>();

	public static SqlSessionFactory getSessionFactory(String mybatisFile) throws IOException {
		if (ObjectUtils.isEmpty(mybatisFile)) {
			throw new IOException("MyBatis configuration file path is missing.");
		}

		return factoryCache.computeIfAbsent(mybatisFile, key -> {
			try {
				String driver = System.getenv("DB_DRIVER");
				String url = System.getenv("DB_URL");
				String username = System.getenv("DB_USERNAME");
				String password = System.getenv("DB_PASSWORD");

				InputStream inputStream = Resources.getResourceAsStream(mybatisFile);

				if (url == null || username == null || password == null) {
					log.debug("Loaded MyBatis configuration file {} without environment variables.", mybatisFile);
					return new SqlSessionFactoryBuilder().build(inputStream);
				}

				Properties props = new Properties();
				props.setProperty("DB_DRIVER", driver);
				props.setProperty("DB_URL", url);
				props.setProperty("DB_USERNAME", username);
				props.setProperty("DB_PASSWORD", password);

				log.debug("Dynamically loaded MyBatis file {} with environment variables. URL: {}, Username: {}, Password: {}",
						mybatisFile, url, username, password);

				return new SqlSessionFactoryBuilder().build(inputStream, props);

			} catch (IOException e) {
				throw new RuntimeException("Error creating SqlSessionFactory for file: " + mybatisFile, e);
			}
		});
	}
}
