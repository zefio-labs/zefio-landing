package io.zefio.jdk.weblogic.ejb;

import io.zefio.jdk.registry.ComponentRegistry;

/**
 * EJB implementation serving as an Ingress Adapter.
 * It retrieves the internal Ingress component from the registry and
 * delegates the execution to the core pipeline.
 */
public class AgencyServiceBean implements AgencyServiceRemote {

    /**
     * Bridges the EJB call to the internal engine logic.
     *
     * Note: This implementation bypasses Spring Proxy overhead by accessing
     * the raw component from the ComponentRegistry.
     */
    @Override
    public String processService(String requestPayload) throws Exception {
        // Retrieve the Ingress module corresponding to the "from" filter name in the YAML configuration
        Object ingressObj = ComponentRegistry.getIngress("from");

        if (ingressObj instanceof EjbProcessable) {
            return ((EjbProcessable) ingressObj).processService(requestPayload);
        } else {
            throw new RuntimeException("The Ingress module 'from' does not implement EjbProcessable.");
        }
    }
}
