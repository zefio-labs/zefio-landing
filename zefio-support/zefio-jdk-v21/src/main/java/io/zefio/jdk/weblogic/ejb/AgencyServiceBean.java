package io.zefio.jdk.weblogic.ejb;

import io.zefio.jdk.registry.ComponentRegistry;

/**
 * EJB Session Bean that delegates external requests to the Zefio Core engine.
 * Accesses the Ingress component directly via the ComponentRegistry.
 */
public class AgencyServiceBean implements AgencyServiceRemote {

    @Override
    public String processService(String requestPayload) throws Exception {
        // Retrieve the direct Ingress instance registered under the name 'from'
        Object ingressObj = ComponentRegistry.getIngress("from");

        if (ingressObj instanceof EjbProcessable) {
            return ((EjbProcessable) ingressObj).processService(requestPayload);
        } else {
            throw new RuntimeException("The Ingress component 'from' does not implement EjbProcessable.");
        }
    }
}
