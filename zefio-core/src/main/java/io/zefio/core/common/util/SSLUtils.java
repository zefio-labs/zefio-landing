package io.zefio.core.common.util;

import io.zefio.core.schema.dto.SslTlsOption;
import org.apache.commons.lang3.StringUtils;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;

/**
 * Utility class for managing SSL/TLS configurations. It provides functionality
 * to load KeyStores and TrustStores from various paths (including classpath)
 * and initializes SSLContext for secure communication.
 */
public class SSLUtils {

	private static final Logger log = LoggerFactory.getLogger(SSLUtils.class);

	private static String resolveFilePath(String filePath) {
		String tmpFilePath = filePath.replace("classpath:", "").replace("\\", "/");
		return tmpFilePath.startsWith("/") ? tmpFilePath.substring(1) : tmpFilePath;
	}

	public static Pair<KeyManagerFactory, TrustManagerFactory> buildKeyTrustStore(SslTlsOption option) throws Exception {
		KeyStore keyStore = null;
		log.debug("SSL initialization: Loading KeyStore...");

		if(!StringUtils.isAllEmpty(option.getKeyStoreType(), option.getKeyStore(), option.getKeyPassword())) {
			try (InputStream is = option.getKeyStore().startsWith("classpath:") ?
					SSLUtils.class.getClassLoader().getResourceAsStream(resolveFilePath(option.getKeyStore())) :
					Files.newInputStream(Paths.get(option.getKeyStore()))) {

				keyStore = KeyStore.getInstance(option.getKeyStoreType());
				keyStore.load(is, option.getKeyPassword().toCharArray());
			} catch (KeyStoreException | IOException e) {
				log.error("Failed to load KeyStore: {}", e.getMessage());
				throw e;
			}
		}

		KeyStore trustStore = null;
		log.debug("SSL initialization: Loading TrustStore...");

		if(!StringUtils.isAllEmpty(option.getTrustStoreType(), option.getTrustStore(), option.getTrustPassword())) {
			try (InputStream is = option.getTrustStore().startsWith("classpath:") ?
					SSLUtils.class.getClassLoader().getResourceAsStream(resolveFilePath(option.getTrustStore())) :
					Files.newInputStream(Paths.get(option.getTrustStore()))) {

				trustStore = KeyStore.getInstance(option.getTrustStoreType());
				trustStore.load(is, option.getTrustPassword().toCharArray());
			} catch (KeyStoreException | IOException e) {
				log.error("Failed to load TrustStore: {}", e.getMessage());
				throw e;
			}
		}

		TrustManagerFactory trustManagerFactory = null;
		KeyManagerFactory keyManagerFactory = null;
		try {
			trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			trustManagerFactory.init(trustStore);
			keyManagerFactory.init(keyStore, option.getKeyPassword().toCharArray());
		} catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
			log.error("Failed to initialize Manager Factories: {}", e.getMessage());
			throw e;
		}
		return Pair.with(keyManagerFactory, trustManagerFactory);
	}

	public static SSLContext createSSLContext(SslTlsOption option) throws Exception {
		Pair<KeyManagerFactory, TrustManagerFactory> managerFactory = buildKeyTrustStore(option);

		log.debug("SSL initialization: Creating SSLContext for protocol [{}]", option.getProtocol());
		SSLContext sslContext = null;
		try {
			sslContext = SSLContext.getInstance(option.getProtocol());
			sslContext.init(managerFactory.getValue0().getKeyManagers(), managerFactory.getValue1().getTrustManagers(), null);
		} catch (NoSuchAlgorithmException | KeyManagementException e) {
			log.error("Failed to create SSLContext: {}", e.getMessage());
			throw e;
		}
		log.debug("SSL initialized: SSLContext creation successful.");
		return sslContext;
	}
}
