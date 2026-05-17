package io.zefio.gateway.netty.chunked;

import io.zefio.core.GatewayPlugin;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.Upstream;
import io.zefio.gateway.netty.IngressSender;
import io.zefio.gateway.netty.chunked.dto.ChunkSplitterConfig;
import io.zefio.gateway.netty.dto.HandlerDefinition;

import java.nio.charset.Charset;

/**
 * Factory for creating ChunkedResponseEncoderStrategy instances.
 * Supports dynamic instantiation of custom encoder classes.
 */
public class ChunkedResponseEncoderFactory {
    public static ChunkedResponseEncoderStrategy create(
            HandlerDefinition handlerDef,
            GatewayPlugin plugin,
            Charset encoding,
            boolean keepAlive,
            IngressSender sender) {

        ChunkSplitterConfig splitter = handlerDef.getSplitter();
        String customClass = splitter.getCustomClass();

        try {
            Class<?> clazz = Class.forName(customClass);
            return (ChunkedResponseEncoderStrategy) clazz
                    .getConstructor(GatewayPlugin.class, ChunkSplitterConfig.class, Charset.class, boolean.class, IngressSender.class)
                    .newInstance(plugin, splitter, encoding, keepAlive, sender);
        } catch (Exception e) {
            throw new FlowException(e, FlowResultStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
