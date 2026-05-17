package io.zefio.jdk.weblogic.soap;

import javax.naming.NamingException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;

/**
 * Upstream SOAP client interface for dynamic service invocation.
 * Provides a standardized contract for calling external SOAP web services
 * using runtime WSDL discovery and reflection.
 */
public interface SoapClient {
    /**
     * Invokes a target method on an Upstream SOAP service.
     *
     * @param wsdlUrl      WSDL URL (e.g., http://host:port/services/Service?wsdl)
     * @param namespaceUri targetNamespace defined in the WSDL
     * @param serviceName  Name of the SOAP Service
     * @param portName     Name of the SOAP Port
     * @param methodName   Method name to be invoked
     * @param payload      Request payload (typically an XML string)
     * @return The response string from the service
     */
    String callService(String wsdlUrl,
                       String namespaceUri,
                       String serviceName,
                       String portName,
                       String methodName,
                       String payload) throws NamingException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException, MalformedURLException;
}
