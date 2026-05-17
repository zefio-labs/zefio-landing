package io.zefio.core.beans;

import io.zefio.core.common.base.PluginMeta;
import io.zefio.core.common.base.PluginMetaList;
import io.zefio.core.common.base.PluginType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Dynamically scans and loads plugin metadata from YAML files located in the 'META-INF/plugins' directory.
 * It supports both local development environments (file systems) and deployment environments (executable JARs),
 * caching the extracted metadata into specialized maps based on plugin types for efficient retrieval.
 */
@Component
public class DynamicSchemaLoader implements InitializingBean {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final String yamlLocationDir = "META-INF/plugins";

	private final Map<String, PluginMeta> ingressCache = new TreeMap<>();
	private final Map<String, PluginMeta> upstreamCache = new TreeMap<>();
	private final Map<String, PluginMeta> interceptorCache = new TreeMap<>();
	private final Map<String, PluginMeta> faultHandlerCache = new TreeMap<>();

	@Override
	public void afterPropertiesSet() throws Exception {
		loadYamlFromClasspathDirs();
		loadYamlFromClasspathJars();
	}

	public void loadYamlFromClasspathDirs() {
		LoaderOptions options = new LoaderOptions();
		Constructor constructor = new Constructor(PluginMetaList.class, options);
		Yaml yaml = new Yaml(constructor);

		try {
			Enumeration<URL> resources = Thread.currentThread()
					.getContextClassLoader()
					.getResources(yamlLocationDir);

			while (resources.hasMoreElements()) {
				URL url = resources.nextElement();
				if ("file".equals(url.getProtocol())) {
					File dir = new File(url.toURI());
					File[] yamlFiles = dir.listFiles((d, name) -> name.endsWith(".yml"));
					if (yamlFiles != null) {
						for (File file : yamlFiles) {
							log.info("[classpath-yaml] Loading YAML: {}", file.getAbsolutePath());
							try (InputStream input = Files.newInputStream(file.toPath())) {
								parseYaml(input, yaml);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			log.error("Error loading YAML from classpath dirs", e);
		}
	}

	public void loadYamlFromClasspathJars() {
		LoaderOptions options = new LoaderOptions();
		Constructor constructor = new Constructor(PluginMetaList.class, options);
		Yaml yaml = new Yaml(constructor);

		String classpath = System.getProperty("java.class.path");
		String[] paths = classpath.split(File.pathSeparator);

		for (String path : paths) {
			File file = new File(path);
			if (file.isFile() && file.getName().endsWith(".jar")) {
				log.debug("[classpath-scan] Scanning JAR: {}", file.getAbsolutePath());
				try (JarFile jar = new JarFile(file)) {
					Enumeration<JarEntry> entries = jar.entries();
					while (entries.hasMoreElements()) {
						JarEntry entry = entries.nextElement();
						String entryName = entry.getName();

						if (entryName.startsWith(yamlLocationDir) && entryName.endsWith(".yml")) {
							log.info("[classpath-jar] Loading YAML from JAR: {} -> {}", file.getName(), entryName);
							try (InputStream input = jar.getInputStream(entry)) {
								parseYaml(input, yaml);
							}
						}
					}
				} catch (IOException e) {
					log.error("Failed to scan JAR: {}", file.getAbsolutePath(), e);
				}
			}
		}
	}

	private void parseYaml(InputStream input, Yaml yaml) {
		PluginMetaList wrapper = yaml.load(input);
		if (wrapper != null && wrapper.getModules() != null) {
			for (PluginMeta meta : wrapper.getModules()) {
				switch (meta.getType()) {
					case interceptor:
						interceptorCache.put(meta.getName(), meta);
						break;
					case faultHandler:
						faultHandlerCache.put(meta.getName(), meta);
						break;
					case ingress:
						ingressCache.put(meta.getName(), meta);
						break;
					case upstream:
						upstreamCache.put(meta.getName(), meta);
						break;
				}
			}

		}
	}

	public PluginMeta getIngress(String key) {
		return ingressCache.get(key);
	}
	public PluginMeta getUpstream(String key) {
		return upstreamCache.get(key);
	}
	public PluginMeta getInterceptor(String key) {
		return interceptorCache.get(key);
	}
	public PluginMeta getFaultHandler(String key) {
		return faultHandlerCache.get(key);
	}

	public Collection<PluginMeta> getByType(PluginType type) {
		switch (type) {
			case ingress:
				return ingressCache.values();
			case upstream:
				return upstreamCache.values();
			case faultHandler:
				return faultHandlerCache.values();
			default:
				return interceptorCache.values();
		}
	}

	public Map<String, PluginMeta> getAllFilters() {
		Map<String, PluginMeta> all = new TreeMap<>();
		all.putAll(ingressCache);
		all.putAll(upstreamCache);
		all.putAll(interceptorCache);
		all.putAll(faultHandlerCache);
		return Collections.unmodifiableMap(all);
	}
	public PluginMeta get(String key) {
		return getAllFilters().get(key);
	}
}
