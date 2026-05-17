package io.zefio.jdk.weblogic.ejb;

/**
 * Remote interface for agency service integration.
 * Acts as the entry point for external systems calling the engine via EJB protocol.
 */
public interface AgencyServiceRemote {

    /**
     * Processes the service request received from an external agency.
     *
     * @param requestPayload The data sent by the agency (typically a message string or byte array)
     * @return The processed result data
     * @throws Exception If a communication or business logic error occurs
     */
    String processService(String requestPayload) throws Exception;
}
