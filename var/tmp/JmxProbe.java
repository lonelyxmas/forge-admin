import com.sun.tools.attach.VirtualMachine;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class JmxProbe {
    public static void main(String[] args) throws Exception {
        String pid = args[0];
        VirtualMachine vm = VirtualMachine.attach(pid);
        String addr = vm.startLocalManagementAgent();
        try (JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(addr))) {
            MBeanServerConnection conn = connector.getMBeanServerConnection();
            ObjectName env = new ObjectName("org.springframework.boot:type=Endpoint,name=Env");
            for (String key : new String[]{
                    "forge.crypto.persistence.write-versioned",
                    "forge.crypto.secret-key",
                    "forge.crypto.enabled",
                    "forge.crypto.persistence.enabled"}) {
                Object result = conn.invoke(env, "environmentEntry",
                        new Object[]{key}, new String[]{String.class.getName()});
                System.out.println("=== " + key + " ===");
                System.out.println(String.valueOf(result));
                System.out.println();
            }
        } finally {
            vm.detach();
        }
    }
}
