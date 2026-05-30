package io.zefio.gateway.quartz.base;

import io.zefio.core.ReactiveIngress;
import lombok.Data;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * Value Object wrapper packaged inside the Quartz JobDataMap context.
 * Forwards structural variables safely into decoupled isolated worker engine execution flows.
 */
@Data
public class QuartzIngressObject implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, Object> valueContext;
    private final ReactiveIngress ingress;
    private final Charset requestEncoding;

    public QuartzIngressObject(Map<String, Object> valueContext, ReactiveIngress ingress, Charset requestEncoding) {
        this.valueContext = valueContext;
        this.ingress = ingress;
        this.requestEncoding = requestEncoding;
    }
}
