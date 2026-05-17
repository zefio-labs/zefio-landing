package io.zefio.launcher;

import io.zefio.core.ZefioCoreService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.core.env.Environment;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Set;

/**
 * Monitors the configuration directory for file changes and triggers Hot-Deploy.
 * Uses a debouncing mechanism to prevent multiple triggers during rapid file saves.
 */
public class ConfigurationDirectoryWatcher implements Runnable {

	private final Logger log = LoggerFactory.getLogger(getClass());
	private final ZefioCoreService service;
	private final ContextRefresher contextRefresher;
	private final WatchService watchService;

    public ConfigurationDirectoryWatcher(Environment environment, ZefioCoreService service, ContextRefresher contextRefresher) throws IOException {
		this.service = service;
		this.contextRefresher = contextRefresher;

		// Locate the root configuration directory
		String configLocation = environment.getProperty("spring.config.location", "classpath:/application.yaml");
		String cleanLocation = configLocation.replace("classpath:/", "");

		File rootConfigFile = new File(Objects.requireNonNull(getClass().getClassLoader().getResource(cleanLocation)).getFile());
        Path configRootPath = rootConfigFile.getParentFile().toPath();

		this.watchService = FileSystems.getDefault().newWatchService();

		// Recursively register directories (flows, globals, etc.)
		registerAll(configRootPath);

		if (log.isInfoEnabled()) {
			log.info("[WatchDog] Registered Directory Watcher for: {}", configRootPath);
		}
	}

	private void registerAll(final Path start) throws IOException {
		Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	@Override
	public void run() {
		try {
			while (!Thread.currentThread().isInterrupted()) {
				WatchKey key = watchService.take();

				// Debouncing: Wait for file system stability after a save operation
				Thread.sleep(500);

				// Clear accumulated events to perform a single clean refresh
				for (WatchEvent<?> event : key.pollEvents()) {
					log.debug("Config file modified: {}", event.context());
				}

				executeHotDeploy();

				if (!key.reset()) {
					log.warn("[WatchDog] WatchKey reset failed. Terminating watcher.");
					break;
				}
			}
		} catch (InterruptedException e) {
			log.info("[WatchDog] Shutting down watcher thread.");
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			log.error("[WatchDog] Unexpected error in watcher loop", e);
		}
	}

	private void executeHotDeploy() {
		try {
			log.info("{}", StringUtils.center(" HOT DEPLOY TRIGGERED: REFRESHING FLOWS ", 70, "■"));

			// 1. Gracefully shutdown Ingress modules
			this.service.shutdownFlowsOnly();

			// 2. Refresh Spring Environment and Reload YAML configurations
			Set<String> refreshedKeys = this.contextRefresher.refresh();

			if (log.isInfoEnabled()) {
				log.info("[WatchDog] Hot-Deploy Context Refreshed. Refreshed Keys: {}", refreshedKeys);
			}

			// 3. Restart flows based on updated FlowSettingsBean
			this.service.startAllFlows();

		} catch (Exception e) {
			log.error("<WatchDog> HOT-DEPLOY FAILED: {}", e.getMessage());
		}
	}
}
