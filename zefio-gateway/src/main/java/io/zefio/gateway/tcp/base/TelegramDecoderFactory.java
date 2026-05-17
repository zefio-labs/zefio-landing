package io.zefio.gateway.tcp.base;

import io.netty.channel.ChannelPipeline;
import io.zefio.core.Ingress;
import io.zefio.core.GatewayPlugin;
import io.zefio.core.Upstream;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.FramingField;
import io.zefio.core.payload.builder.config.FramingType;
import io.zefio.gateway.netty.decoder.DelimiterFrameDecoder;
import io.zefio.gateway.netty.decoder.DelimiterPollingDecoder;
import io.zefio.gateway.netty.decoder.LengthFrameDecoder;
import io.zefio.gateway.netty.decoder.LengthPollingDecoder;
import io.zefio.gateway.netty.dto.NettyValues;
import io.zefio.gateway.tcp.dto.TcpUpstreamValues;
import io.zefio.gateway.tcp.dto.TcpIngressValues;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.Charset;

/**
 * Factory class that injects appropriate Netty Decoders into the pipeline
 * based on the Telegram FramingType configuration.
 */
public class TelegramDecoderFactory {

    /**
     * Analyzes Ingress or Upstream configurations to add data framing and polling decoders.
     *
     * @param flowName   The current flow identification
     * @param pipeline   The Netty ChannelPipeline
     * @param flowNode   The Ingress or Upstream instance
     * @param valuesObj  TCP-related configuration object
     */
    public static void addTelegramDecoders(String flowName, ChannelPipeline pipeline, GatewayPlugin flowNode, Object valuesObj) {
        Charset requestEncoding;
        Charset responseEncoding;
        NettyValues values;
        PayloadBuilder eventBuilder;

        // 1. Extract context and configuration based on the node type (Ingress or Upstream)
        if (flowNode instanceof Ingress) {
            eventBuilder = ((Ingress) flowNode).getEventBuilder();
            values = ((TcpIngressValues) valuesObj).getIngress();
            requestEncoding = ((TcpIngressValues) valuesObj).getRequestEncoding();
            responseEncoding = ((TcpIngressValues) valuesObj).getResponseEncoding();
        } else {
            eventBuilder = ((Upstream) flowNode).getEventBuilder();
            values = ((TcpUpstreamValues) valuesObj).getUpstream();
            requestEncoding = ((TcpUpstreamValues) valuesObj).getRequestEncoding();
            responseEncoding = ((TcpUpstreamValues) valuesObj).getResponseEncoding();
        }

        // 2. Determine if health-check polling is enabled
        boolean enablePolling = StringUtils.isNotBlank(values.getPolling().getRequest())
                && StringUtils.isNotBlank(values.getPolling().getResponse());

        // 3. Extract the framing strategy (Length, Delimiter, or EOF) from Telegram metadata
        FramingField framing = eventBuilder.getTelegram().getValues().getFraming();
        FramingType framingType = (framing != null) ? framing.getType() : FramingType.EOF;

        // 4. Arrange decoder handlers based on the strategy
        switch (framingType) {
            case Length:
                // Add decoder based on fixed or variable length headers
                pipeline.addLast(new LengthFrameDecoder(flowName, flowNode, values, eventBuilder, requestEncoding, responseEncoding));
                if (enablePolling) {
                    // Add heartbeat decoder for length-based protocols
                    pipeline.addLast(new LengthPollingDecoder(flowName, flowNode, values, eventBuilder, requestEncoding, responseEncoding));
                }
                break;

            case Delimiter:
                // Add decoder based on specific character sequences (e.g., \n)
                pipeline.addLast(new DelimiterFrameDecoder(flowName, flowNode, values, eventBuilder, requestEncoding, responseEncoding));
                if (enablePolling) {
                    // Add heartbeat decoder for delimiter-based protocols
                    pipeline.addLast(new DelimiterPollingDecoder(flowName, flowNode, values, eventBuilder, requestEncoding, responseEncoding));
                }
                break;

            case EOF:
                // Connections where closure indicates the end of the message; no specialized framing required
                break;

            default:
                throw new IllegalArgumentException("Unsupported Framing Type: " + framingType);
        }
    }
}
