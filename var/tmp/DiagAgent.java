import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class DiagAgent {
    public static void agentmain(String args, Instrumentation inst) {
        try (PrintWriter out = new PrintWriter(new FileWriter("/tmp/forge-diag.txt"))) {
            try {
                Class<?> transUtils = null;
                for (Class<?> c : inst.getAllLoadedClasses()) {
                    if ("com.mdframe.forge.starter.trans.util.TransUtils".equals(c.getName())) { transUtils = c; break; }
                }
                if (transUtils == null) { out.println("TransUtils not loaded"); return; }
                Field tmField = transUtils.getDeclaredField("transManager");
                tmField.setAccessible(true);
                Object transManager = tmField.get(null);
                if (transManager == null) { out.println("transManager null"); return; }
                Field ctxField = transManager.getClass().getDeclaredField("applicationContext");
                ctxField.setAccessible(true);
                Object ctx = ctxField.get(transManager);
                out.println("ctx = " + ctx.getClass().getName());
                Method getEnv = ctx.getClass().getMethod("getEnvironment");
                Object env = getEnv.invoke(ctx);
                Method getProp = env.getClass().getMethod("getProperty", String.class);
                for (String k : new String[]{"forge.crypto.enabled","forge.crypto.persistence.write-versioned","forge.crypto.persistence.enabled","forge.crypto.persistence.legacy-read-enabled"}) {
                    out.println("ENV " + k + " = " + getProp.invoke(env, k));
                }
                Object sk = getProp.invoke(env, "forge.crypto.secret-key");
                out.println("ENV secret-key.len = " + (sk == null ? "null" : String.valueOf(sk).length()));
                Object ak = getProp.invoke(env, "forge.crypto.persistence.active-key");
                out.println("ENV active-key.len = " + (ak == null ? "null" : String.valueOf(ak).length()));
                out.println("ENV active-key-id = " + getProp.invoke(env, "forge.crypto.persistence.active-key-id"));
                Method getBean = ctx.getClass().getMethod("getBean", String.class);
                Object bean = getBean.invoke(ctx, "cryptoProperties");
                out.println("bean = " + bean.getClass().getName() + " @" + System.identityHashCode(bean));
                Object enabled = bean.getClass().getMethod("getEnabled").invoke(bean);
                Object secretKey = bean.getClass().getMethod("getSecretKey").invoke(bean);
                Object persistence = bean.getClass().getMethod("getPersistence").invoke(bean);
                out.println("BEAN enabled = " + enabled);
                out.println("BEAN secretKey.len = " + (secretKey == null ? "null" : String.valueOf(secretKey).length()));
                out.println("BEAN persistence @" + System.identityHashCode(persistence));
                Class<?> pc = persistence.getClass();
                out.println("BEAN persistence.enabled = " + pc.getMethod("getEnabled").invoke(persistence));
                out.println("BEAN persistence.writeVersioned = " + pc.getMethod("getWriteVersioned").invoke(persistence));
                out.println("BEAN persistence.legacyReadEnabled = " + pc.getMethod("getLegacyReadEnabled").invoke(persistence));
                out.println("BEAN persistence.activeKeyId = [" + pc.getMethod("getActiveKeyId").invoke(persistence) + "]");
                Object akey = pc.getMethod("getActiveKey").invoke(persistence);
                out.println("BEAN persistence.activeKey.len = " + (akey == null ? "null" : String.valueOf(akey).length()));
                Object lkey = pc.getMethod("getLegacyKey").invoke(persistence);
                out.println("BEAN persistence.legacyKey.len = " + (lkey == null ? "null" : String.valueOf(lkey).length()));
            } catch (Throwable t) { t.printStackTrace(out); }
        } catch (Exception ignore) {}
    }
}
