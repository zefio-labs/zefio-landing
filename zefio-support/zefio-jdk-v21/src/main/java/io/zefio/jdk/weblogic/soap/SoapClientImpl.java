package io.zefio.jdk.weblogic.soap;

import jakarta.xml.ws.Service;
import javax.xml.namespace.QName;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Concrete implementation of the Upstream SOAP client.
 * Uses Jakarta XML Web Services to dynamically proxy service ports and
 * invoke methods via reflection.
 */
public class SoapClientImpl implements SoapClient {

    /**
     * Dynamic SOAP service invocation logic.
     *
     * Note: This implementation targets environments where the actual port
     * implementation type is resolved at runtime.
     */
    @Override
    public String callService(String wsdlUrl,
                              String namespaceUri,
                              String serviceName,
                              String portName,
                              String methodName,
                              String payload) throws MalformedURLException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        // Initialize WSDL URL
        URL wsdlURL = new URL(wsdlUrl);

        // Construct QNames for service and port resolution
        QName serviceQName = new QName(namespaceUri, serviceName);
        QName portQName = new QName(namespaceUri, portName);

        // Instantiate the JAX-WS service proxy
        Service service = Service.create(wsdlURL, serviceQName);

        // Retrieve the port instance (mapped to Object for dynamic invocation)
        Object port = service.getPort(portQName, Object.class);

        // Invoke the target method using reflection
        // Assumes the upstream service accepts a single String parameter for payload
        Method method = port.getClass().getMethod(methodName, String.class);
        Object result = method.invoke(port, payload);

        return result != null ? result.toString() : null;
    }
}
