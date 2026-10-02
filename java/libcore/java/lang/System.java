package java.lang;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

public final class System {
    public static InputStream in;
    public static PrintStream out;
    public static PrintStream err;
    private static Properties props;

    static {
        props = new Properties();
        props.setProperty("java.vm.name", "Dalvik");
        props.setProperty("java.vm.version", "2.1.0");
        props.setProperty("java.vm.vendor", "switchapk");
        props.setProperty("java.version", "0");
        props.setProperty("java.vendor", "The Android Project");
        props.setProperty("java.specification.version", "0.9");
        props.setProperty("java.class.path", ".");
        props.setProperty("java.home", "/system");
        props.setProperty("java.io.tmpdir", "/data/local/tmp");
        props.setProperty("line.separator", "\n");
        props.setProperty("file.separator", "/");
        props.setProperty("path.separator", ":");
        props.setProperty("file.encoding", "UTF-8");
        props.setProperty("os.name", "Linux");
        props.setProperty("os.arch", nativeArch());
        props.setProperty("os.version", "4.9.0");
        props.setProperty("user.dir", "/");
        props.setProperty("user.home", "");
        props.setProperty("user.name", "app");
        props.setProperty("user.language", "en");
        props.setProperty("user.region", "US");
        props.setProperty("http.agent", "Dalvik/2.1.0 (Linux; U; Android 10; Nintendo Switch)");
        in = new FileInputStream(FileDescriptor.in);
        out = new PrintStream(new FileOutputStream(FileDescriptor.out), true);
        err = new PrintStream(new FileOutputStream(FileDescriptor.err), true);
    }

    private System() {
    }

    public static void setIn(InputStream newIn) {
        in = newIn;
    }

    public static void setOut(PrintStream newOut) {
        out = newOut;
    }

    public static void setErr(PrintStream newErr) {
        err = newErr;
    }

    public static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length);

    public static native long currentTimeMillis();

    public static native long nanoTime();

    public static native int identityHashCode(Object x);

    static native void nativeExit(int status);

    /** "aarch64" or "x86_64": the CPU native libraries are loaded for (Build.CPU_ABI follows it). */
    private static native String nativeArch();

    static native void logNative(int priority, String msg);

    public static native void gc();

    public static void runFinalization() {
    }

    public static void runFinalizersOnExit(boolean value) {
    }

    public static void exit(int status) {
        Runtime.getRuntime().exit(status);
    }

    public static String getProperty(String key) {
        return props.getProperty(key);
    }

    public static String getProperty(String key, String def) {
        return props.getProperty(key, def);
    }

    public static String setProperty(String key, String value) {
        return (String) props.setProperty(key, value);
    }

    public static String clearProperty(String key) {
        return (String) props.remove(key);
    }

    public static Properties getProperties() {
        return props;
    }

    public static void setProperties(Properties p) {
        if (p != null) {
            props = p;
        }
    }

    public static String lineSeparator() {
        return "\n";
    }

    public static String getenv(String name) {
        return null;
    }

    public static Map<String, String> getenv() {
        return Collections.emptyMap();
    }

    public static SecurityManager getSecurityManager() {
        return null;
    }

    public static void setSecurityManager(SecurityManager sm) {
    }

    public static void load(String filename) {
        Runtime.getRuntime().load(filename);
    }

    public static void loadLibrary(String libname) {
        Runtime.getRuntime().loadLibrary(libname);
    }

    public static String mapLibraryName(String libname) {
        return "lib" + libname + ".so";
    }

    public static java.io.Console console() {
        return null;
    }
}
