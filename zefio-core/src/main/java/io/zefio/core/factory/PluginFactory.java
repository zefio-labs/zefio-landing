package io.zefio.core.factory;

import io.zefio.core.FaultHandler;
import io.zefio.core.GatewayInterceptor;
import io.zefio.core.Ingress;
import io.zefio.core.Upstream;
import io.zefio.core.beans.DynamicSchemaLoader;
import io.zefio.jdk.registry.ComponentRegistry;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Factory responsible for creating and managing the lifecycle of engine components.
 * It utilizes a registry to ensure instances are reused where appropriate (e.g., sharing
 * connection pools in Upstream components) and provides auto-detection logic to
 * resolve component types from configurations or class hierarchies.
 */
@Component
public class PluginFactory {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private DynamicSchemaLoader dynamicSchemaLoader;

    // =================================================================
    // 1. Public Creation Methods
    // =================================================================

    public Ingress createIngress(String type, String clazz, PluginContext context) {
        String name = context.getPluginName();
        // Reuse existing ingress component if already registered
        if (ComponentRegistry.getIngress(name) != null) {
            log.info("[Factory] Reusing existing ingress component: {}", name);
            return (Ingress) ComponentRegistry.getIngress(name);
        }
        Ingress ingress = createModule(type, clazz, context, Ingress.class);
        ComponentRegistry.registerIngress(name, ingress);
        return ingress;
    }

    public Upstream createUpstream(String type, String clazz, PluginContext context) {
        String name = context.getPluginName();
        // Reuse existing upstream instance to facilitate connection pool sharing
        if (ComponentRegistry.getUpstream(name) != null) {
            log.info("[Factory] Reusing existing Upstream component for Pool Sharing: {}", name);
            return (Upstream) ComponentRegistry.getUpstream(name);
        }
        Upstream upstream = createModule(type, clazz, context, Upstream.class);
        ComponentRegistry.registerUpstream(name, upstream);
        return upstream;
    }

    public GatewayInterceptor createInterceptor(String type, String clazz, PluginContext context) {
        String name = context.getPluginName();
        if (ComponentRegistry.getInterceptor(name) != null) {
            log.info("[Factory] Reusing existing Interceptor component: {}", name);
            return (GatewayInterceptor) ComponentRegistry.getInterceptor(name);
        }
        GatewayInterceptor interceptor = createModule(type, clazz, context, GatewayInterceptor.class);
        ComponentRegistry.registerInterceptor(name, interceptor);
        return interceptor;
    }

    public FaultHandler createError(String type, String clazz, PluginContext context) {
        String name = context.getPluginName();
        if (ComponentRegistry.getError(name) != null) {
            log.info("[Factory] Reusing existing Error component: {}", name);
            return (FaultHandler) ComponentRegistry.getError(name);
        }
        FaultHandler error = createModule(type, clazz, context, FaultHandler.class);
        ComponentRegistry.registerError(name, error);
        return error;
    }

    /**
     * Attempts to resolve and create a component based on type or class name.
     * Prioritizes existing cached instances in the registry to avoid redundant instantiation.
     */
    public GatewayInterceptor createByAutoDetection(String type, String clazzName, PluginContext context) {
        String name = context.getPluginName();

        // If a component is already cached, return it immediately regardless of type/class specification
        if (ComponentRegistry.getUpstream(name) != null) {
            log.info("[Factory] Auto-detect: Reusing existing Upstream for: {}", name);
            return (GatewayInterceptor) ComponentRegistry.getUpstream(name);
        }
        if (ComponentRegistry.getInterceptor(name) != null) {
            log.info("[Factory] Auto-detect: Reusing existing Interceptor for: {}", name);
            return (GatewayInterceptor) ComponentRegistry.getInterceptor(name);
        }
        if (ComponentRegistry.getError(name) != null) {
            log.info("[Factory] Auto-detect: Reusing existing Error for: {}", name);
            return (GatewayInterceptor) ComponentRegistry.getError(name);
        }

        if (ObjectUtils.isNotEmpty(type)) {
            if (dynamicSchemaLoader.getUpstream(type) != null) return createUpstream(type, clazzName, context);
            if (dynamicSchemaLoader.getInterceptor(type) != null) return createInterceptor(type, clazzName, context);
            if (dynamicSchemaLoader.getFaultHandler(type) != null) return createError(type, clazzName, context);
            return createInterceptor(type, clazzName, context);
        }

        if (ObjectUtils.isNotEmpty(clazzName)) {
            try {
                Class<?> loadedClass = Class.forName(clazzName);
                if (Upstream.class.isAssignableFrom(loadedClass)) return createUpstream(type, clazzName, context);
                if (FaultHandler.class.isAssignableFrom(loadedClass)) return createError(type, clazzName, context);
                if (GatewayInterceptor.class.isAssignableFrom(loadedClass)) return createInterceptor(type, clazzName, context);
                throw new IllegalArgumentException("Class must implement GatewayInterceptor or Upstream: " + clazzName);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Class not found during auto-detection: " + clazzName, e);
            }
        }
        throw new IllegalArgumentException("Cannot auto-detect component type for: " + context.getPluginName());
    }

    @SuppressWarnings("unchecked")
    private <T> T createModule(String type, String clazz, PluginContext context, Class<T> expectedType) {
        String className = null;

        // Resolve class name via DynamicSchemaLoader based on the provided type
        if (type != null) {
            if (dynamicSchemaLoader.getIngress(type) != null) className = dynamicSchemaLoader.getIngress(type).getClassName();
            else if (dynamicSchemaLoader.getUpstream(type) != null) className = dynamicSchemaLoader.getUpstream(type).getClassName();
            else if (dynamicSchemaLoader.getInterceptor(type) != null) className = dynamicSchemaLoader.getInterceptor(type).getClassName();
            else if (dynamicSchemaLoader.getFaultHandler(type) != null) className = dynamicSchemaLoader.getFaultHandler(type).getClassName();
        }

        if (className == null) className = clazz;
        if (className == null) throw new RuntimeException("Cannot find class name for component: " + context.getPluginName());

        try {
            Class<?> loadedClazz = Class.forName(className);
            if (!expectedType.isAssignableFrom(loadedClazz)) {
                throw new IllegalArgumentException("Class " + className + " does not implement " + expectedType.getSimpleName());
            }
            // All components must provide a constructor accepting PluginContext
            return (T) loadedClazz.getConstructor(PluginContext.class).newInstance(context);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate component: " + context.getPluginName() + " [" + className + "]", e);
        }
    }
}
