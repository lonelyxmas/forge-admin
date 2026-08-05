import com.sun.tools.attach.VirtualMachine;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class JmxProbe2 {
    public static void main(String[] args) throws Exception {
        String pid = args[0];
        VirtualMachine vm = VirtualMachine.attach(pid);
        String addr = vm.startLocalManagementAgent();
        try (JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(addr))) {
            MBeanServerConnection conn = connector.getMBeanServerConnection();
            ObjectName cp = new ObjectName("org.springframework.boot:type=Endpoint,name=Configprops");
            Object result = conn.invoke(cp, "configurationPropertiesWithPrefix",
                    new Object[]{"forge.crypto"}, new String[]{String.class.getName()});
            System.out.println(String.valueOf(result));
        } finally {
            vm.detach();
        }
    }
}
