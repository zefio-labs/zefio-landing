package io.zefio.core.common.util;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A collection of general-purpose utility methods providing thread factory creation,
 * resource file retrieval from the classpath, and reflection-based dynamic instance instantiation.
 */
public class CommonUtils {
	private static final Logger log = LoggerFactory.getLogger(CommonUtils.class);

	public static ThreadFactory getThreadFactory(String threadNamePrefix) {
		return new ThreadFactory()
		{
			private final AtomicInteger instanceCount = new AtomicInteger();
			@Override
			public Thread newThread(Runnable r)
			{
				Thread t = Executors.defaultThreadFactory().newThread(r);
				t.setDaemon(true);
				t.setName(String.format("%s-%d", threadNamePrefix, instanceCount.getAndIncrement()));
				return t;
			}
		};
	}

	public static File getFileFromResources(String configFilePath) throws IOException {
		try {
			return new ClassPathResource(configFilePath).getFile();
		} catch (IOException e) {
			Optional<String> optional = Arrays.stream(System.getProperty("java.class.path").split(":")).filter(s -> s.endsWith("resources")).findFirst();
			if(optional.isPresent()){
				Path configPath = Paths.get(optional.get(), configFilePath);
				if (Files.notExists(configPath)) Files.createFile(configPath);
				return configPath.toFile();
			}
			else{
				return null;
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T createInstance(String className, Class<?>[] argTypes, Object... args) {
		if (ObjectUtils.isEmpty(className)) {
			return null;
		}
		try {
			Class<?> clazz = Class.forName(className);
			Constructor<T> constructor = (Constructor<T>) clazz.getConstructor(argTypes);
			return constructor.newInstance(args);
		} catch (ClassNotFoundException e) {
			log.error("Reflection Error: Class not found: {}", className, e);
		} catch (NoSuchMethodException e) {
			log.error("Reflection Error: Constructor not found for class: {}", className, e);
		} catch (InvocationTargetException e) {
			log.error("Reflection Error: Constructor threw an exception for class: {}", className, e.getTargetException());
		} catch (InstantiationException e) {
			log.error("Reflection Error: Class cannot be instantiated: {}", className, e);
		} catch (IllegalAccessException e) {
			log.error("Reflection Error: Access denied for class or constructor: {}", className, e);
		} catch (Exception e) {
			log.error("Reflection Error: Unexpected error during instance creation for class: {}", className, e);
		}
		return null;
	}
}
