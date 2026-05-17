package io.zefio.gateway.netty.util;

import io.netty.channel.ChannelHandler;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.gateway.netty.chunked.dto.ChunkSplitterConfig;
import io.zefio.gateway.netty.dto.ClientHandlerContext;
import io.zefio.gateway.netty.dto.HandlerDefinition;
import io.zefio.gateway.netty.dto.ServerHandlerContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory responsible for the dynamic instantiation of Netty handlers.
 * Uses reflection and a constructor cache to maintain high performance during pipeline assembly.
 */
public class HandlerFactory {

    /** List of resolved handler classes */
    private final List<Class<?>> handlerClasses = new ArrayList<>();

    /** List of handler definitions provided via configuration */
    private final List<HandlerDefinition> handlerDefs = new ArrayList<>();

    /** Static cache for constructors to minimize reflection overhead */
    private static final Map<Class<?>, Constructor<?>> constructorCache = new ConcurrentHashMap<>();

    public HandlerFactory(List<HandlerDefinition> handlers, Class<?> defaultHandlerClass) {
        List<HandlerDefinition> initialized = initializeHandlers(handlers, defaultHandlerClass);
        this.handlerDefs.addAll(initialized);

        for (HandlerDefinition h : initialized) {
            if (h.getClazz() != null && !h.getClazz().trim().isEmpty()) {
                try {
                    this.handlerClasses.add(Class.forName(h.getClazz()));
                } catch (ClassNotFoundException e) {
                    throw new FlowException(FlowResultStatus.INTERNAL_SERVER_ERROR,
                            "Failed to load handler class: " + h.getClazz());
                }
            }
        }
    }

    public List<Class<?>> getHandlerClasses() {
        return Collections.unmodifiableList(handlerClasses);
    }

    /**
     * Retrieves or caches the public constructor for the specified handler class.
     * Enforces the architectural rule that each handler must have exactly one public constructor.
     */
    private Constructor<?> getConstructor(Class<?> clazz) {
        return constructorCache.computeIfAbsent(clazz, k -> {
            Constructor<?>[] constructors = clazz.getConstructors();
            if (constructors.length != 1) {
                throw new FlowException(FlowResultStatus.INTERNAL_SERVER_ERROR,
                        "Handler class must have exactly one public constructor: " + clazz.getName());
            }
            return constructors[0];
        });
    }

    /**
     * Instantiates the list of handlers for an Ingress (Server) connection.
     */
    public List<ChannelHandler> createServerHandler(ServerHandlerContext context) {
        List<ChannelHandler> result = new ArrayList<>();
        try {
            for (int i = 0; i < handlerDefs.size(); i++) {
                Class<?> clazz = handlerClasses.get(i);
                HandlerDefinition def = handlerDefs.get(i);
                Constructor<?> constructor = getConstructor(clazz);

                // Pass context and definition into the constructor
                ChannelHandler handler = (ChannelHandler) constructor.newInstance(context, def);
                result.add(handler);
            }
        } catch (Exception e) {
            throw new FlowException(FlowResultStatus.INTERNAL_SERVER_ERROR,
                    "Critical error during Ingress handler instantiation.");
        }
        return result;
    }

    /**
     * Instantiates the list of handlers for an Upstream (Client) connection.
     */
    public List<ChannelHandler> createClientHandler(ClientHandlerContext context) {
        List<ChannelHandler> result = new ArrayList<>();
        try {
            for (int i = 0; i < handlerDefs.size(); i++) {
                Class<?> clazz = handlerClasses.get(i);
                HandlerDefinition def = handlerDefs.get(i);
                Constructor<?> constructor = getConstructor(clazz);

                ChannelHandler handler = (ChannelHandler) constructor.newInstance(context, def);
                result.add(handler);
            }
        } catch (Exception e) {
            throw new FlowException(FlowResultStatus.INTERNAL_SERVER_ERROR,
                    "Critical error during Upstream handler instantiation.");
        }
        return result;
    }

    /**
     * Ensures the handler list is properly initialized and includes the default handler
     * if none are explicitly defined in the configuration.
     */
    private List<HandlerDefinition> initializeHandlers(List<HandlerDefinition> handlers,
                                                       Class<?> defaultHandlerClass) {
        if (handlers == null) {
            handlers = new ArrayList<>();
        }

        // Check for duplicates to prevent the default handler from being added twice
        boolean hasDefaultHandler = handlers.stream()
                .anyMatch(h -> defaultHandlerClass.getName().equals(h.getClazz()));

        if (!hasDefaultHandler) {
            HandlerDefinition defaultHandler = new HandlerDefinition();
            defaultHandler.setClazz(defaultHandlerClass.getName());
            defaultHandler.setSplitter(new ChunkSplitterConfig());
            handlers.add(defaultHandler);
        }
        return handlers;
    }

    /**
     * Functional interface for dynamic handler creation logic.
     */
    @FunctionalInterface
    public interface HandlerCreator<T> {
        T create(Class<?> handlerClass) throws NoSuchMethodException, IllegalAccessException,
                InvocationTargetException, InstantiationException;
    }

    /**
     * Generic utility to create a custom list of handlers using a specific creator logic.
     */
    public <T> List<T> createHandlers(HandlerCreator<T> creator) {
        List<T> result = new ArrayList<>();
        for (Class<?> clazz : handlerClasses) {
            try {
                result.add(creator.create(clazz));
            } catch (Exception e) {
                throw new FlowException(FlowResultStatus.INTERNAL_SERVER_ERROR,
                        "Generic handler creation failed for: " + clazz.getName());
            }
        }
        return result;
    }
}
