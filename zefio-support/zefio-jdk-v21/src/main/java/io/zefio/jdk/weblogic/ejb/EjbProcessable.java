package io.zefio.jdk.weblogic.ejb;

/**
 * Contract for Ingress components that support EJB-to-Engine delegation.
 */
public interface EjbProcessable {
    String processService(String requestPayload) throws Exception;
}
