import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class DiagAgent2 {
    public static void agentmain(String args, Instrumentation inst) {
        try (PrintWriter out = new PrintWriter(new FileWriter("/tmp/forge-diag2.txt"))) {
            try {
                Class<?> transUtils = null;
                for (Class<?> c : inst.getAllLoadedClasses()) {
                    if ("com.mdframe.forge.starter.trans.util.TransUtils".equals(c.getName())) { transUtils = c; break; }
                }
                if (transUtils == null) { out.println("TransUtils not loaded"); return; }
                Field tmField = transUtils.getDeclaredField("transManager");
                tmField.setAccessible(true);
                Object transManager = tmField.get(null);
                Field ctxField = transManager.getClass().getDeclaredField("applicationContext");
                ctxField.setAccessible(true);
                Object ctx = ctxField.get(transManager);
                Object env = ctx.getClass().getMethod("getEnvironment").invoke(ctx);
                Object sources = env.getClass().getMethod("getPropertySources").invoke(env);
                out.println("== main context env property sources ==");
                for (Object ps : (Iterable<?>) sources) {
                    String name = (String) ps.getClass().getMethod("getName").invoke(ps);
                    out.println("  - " + name + " (" + ps.getClass().getSimpleName() + ") @" + System.identityHashCode(ps));
                    if ("forgeCryptoBootstrap".equals(name) || "dbPropertySource".equals(name)) {
                        Object src = ps.getClass().getMethod("getSource").invoke(ps);
                        if (src instanceof Map<?, ?> m) {
                            for (Map.Entry<?, ?> e : m.entrySet()) {
                                String k = String.valueOf(e.getKey());
                                String v = String.valueOf(e.getValue());
                                boolean secret = k.contains("key") || k.contains("secret");
                                out.println("      " + k + " = " + (secret ? ("<len=" + v.length() + ">") : v));
                            }
                        }
                    }
                }
            } catch (Throwable t) { t.printStackTrace(out); }
        } catch (Exception ignore) {}
    }
}
