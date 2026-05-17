package io.zefio.jdk.weblogic.ejb;

/**
 * Remote interface for EJB-based agency services.
 */
public interface AgencyServiceRemote {
    /**
     * Processes the service request from external agencies.
     * @param requestPayload Raw request string or data packet.
     * @return Processed response data.
     * @throws Exception If business logic or communication fails.
     */
    String processService(String requestPayload) throws Exception;
}
