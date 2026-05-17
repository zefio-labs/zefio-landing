package io.zefio.core;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 * Example JMX Client for interacting with Zefio Core metrics and lifecycle operations.
 * Used to remotely monitor and control Ingress/Upstream components.
 */
public class JmxClientExample {

    public static void main(String[] args) throws Exception {

        // The target JVM must be started with JMX enabled.
        // Required JVM arguments:
        // -Dcom.sun.management.jmxremote
        // -Dcom.sun.management.jmxremote.port=9999
        // -Dcom.sun.management.jmxremote.authenticate=false
        // -Dcom.sun.management.jmxremote.ssl=false

        JMXServiceURL url = new JMXServiceURL(
                "service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi"
        );

        try (JMXConnector jmxc = JMXConnectorFactory.connect(url)) {

            MBeanServerConnection mbeanConn = jmxc.getMBeanServerConnection();

            // Target the specific Ingress component's MBean
            ObjectName name = new ObjectName(
                    "io.zefio.ingress-from:type=ingress"
            );

            // ======== Read Attributes ========
            Long received = (Long) mbeanConn.getAttribute(name, "EventReceivedCount");
            Long accepted = (Long) mbeanConn.getAttribute(name, "EventAcceptedCount");
            Long failed = (Long) mbeanConn.getAttribute(name, "EventFailedCount");
            Double avgExec = (Double) mbeanConn.getAttribute(name, "ExecutionAvg");
            Long maxExec = (Long) mbeanConn.getAttribute(name, "ExecutionMax");

            System.out.println("Received = " + received);
            System.out.println("Accepted = " + accepted);
            System.out.println("Failed   = " + failed);
            System.out.println("AvgExec  = " + avgExec);
            System.out.println("MaxExec  = " + maxExec);

            // ======== Invoke Operations ========
            System.out.println("Calling clear()...");
            mbeanConn.invoke(name, "clear", null, null);

            System.out.println("Calling start()...");
            mbeanConn.invoke(name, "start", null, null);

            System.out.println("Calling stop()...");
            mbeanConn.invoke(name, "stop", null, null);
        }
    }
}
