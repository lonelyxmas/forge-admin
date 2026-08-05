import com.sun.tools.attach.VirtualMachine;
public class AgentLoader {
    public static void main(String[] args) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try { vm.loadAgent(args[1]); } finally { vm.detach(); }
        System.out.println("agent loaded");
    }
}
