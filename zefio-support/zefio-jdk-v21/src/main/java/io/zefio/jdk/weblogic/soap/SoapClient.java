package io.zefio.jdk.weblogic.soap;

import javax.naming.NamingException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;

/**
 * Upstream interface for dynamic SOAP service invocation.
 * Facilitates communication with external SOAP-based systems by
 * resolving WSDL definitions at runtime.
 */
public interface SoapClient {
    /**
     * Executes a dynamic SOAP call to an external service.
     *
     * @param wsdlUrl      WSDL URL (e.g., http://host:port/services/Service?wsdl)
     * @param namespaceUri Target namespace defined in the WSDL
     * @param serviceName  Name of the SOAP service
     * @param portName     Name of the service port
     * @param methodName   The remote method to invoke
     * @param payload      The SOAP request payload (XML string)
     * @return The XML response string from the upstream service
     */
    String callService(String wsdlUrl,
                       String namespaceUri,
                       String serviceName,
                       String portName,
                       String methodName,
                       String payload) throws NamingException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException, MalformedURLException;
}
