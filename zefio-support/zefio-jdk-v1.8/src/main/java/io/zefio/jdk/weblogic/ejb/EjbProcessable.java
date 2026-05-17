package io.zefio.jdk.weblogic.ejb;

/**
 * Internal interface to be implemented by Ingress Filters that support EJB processing.
 * Acts as a bridge between the EJB adapter and the core pipeline logic.
 */
public interface EjbProcessable {
    /**
     * Processes the request payload within the engine pipeline.
     *
     * @param requestPayload Data received via the EJB ingress adapter
     * @return Response data to be sent back to the external agency
     * @throws Exception If internal processing fails
     */
    String processService(String requestPayload) throws Exception;
}
