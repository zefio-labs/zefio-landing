package io.zefio.jdk.weblogic.soap;

import javax.xml.ws.Service;
import javax.xml.namespace.QName;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Implementation of the SoapClient using JAX-WS dynamic service creation.
 * Manages Upstream connectivity by dynamically resolving port types and
 * invoking remote procedures.
 */
public class SoapClientImpl implements SoapClient {

    /**
     * Executes the SOAP service call by dynamically creating a service proxy.
     */
    @Override
    public String callService(String wsdlUrl,
                              String namespaceUri,
                              String serviceName,
                              String portName,
                              String methodName,
                              String payload) throws MalformedURLException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        // Construct WSDL URL object
        URL wsdlURL = new URL(wsdlUrl);

        // Initialize QNames for the service and port
        QName serviceQName = new QName(namespaceUri, serviceName);
        QName portQName = new QName(namespaceUri, portName);

        // Create the JAX-WS Service instance
        Service service = Service.create(wsdlURL, serviceQName);

        // Retrieve the service port object
        Object port = service.getPort(portQName, Object.class);

        // Invoke the target method using reflection
        Method method = port.getClass().getMethod(methodName, String.class);
        Object result = method.invoke(port, payload);

        return result != null ? result.toString() : null;
    }
}
