package java.lang;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

public abstract class ClassLoader {
    private static ClassLoader systemLoader;
    private final ClassLoader parent;

    protected ClassLoader() {
        this(null);
    }

    protected ClassLoader(ClassLoader parent) {
        this.parent = parent;
    }

    public static synchronized ClassLoader getSystemClassLoader() {
        if (systemLoader == null) {
            // A PathClassLoader, as on Android; the framework adds the app's native library paths to it.
            systemLoader = new dalvik.system.PathClassLoader("", null, null);
        }
        return systemLoader;
    }

    public final ClassLoader getParent() {
        return parent;
    }

    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }

    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return Class.classForName(name, false);
        } catch (ClassNotFoundException e) {
            return findClass(name);
        }
    }

    protected Class<?> findClass(String name) throws ClassNotFoundException {
        throw new ClassNotFoundException(name);
    }

    protected final Class<?> findLoadedClass(String name) {
        try {
            return Class.classForName(name, false);
        } catch (Throwable e) {
            return null;
        }
    }

    @Deprecated
    protected final Class<?> defineClass(byte[] b, int off, int len) throws ClassFormatError {
        throw new UnsupportedOperationException("can't load this type of class file");
    }

    protected final Class<?> defineClass(String name, byte[] b, int off, int len) throws ClassFormatError {
        throw new UnsupportedOperationError("can't load this type of class file");
    }

    /** As on Android: the parent's resource first (none from the boot class path here), then findResource. */
    public URL getResource(String name) {
        URL url = parent != null ? parent.getResource(name) : null;
        return url != null ? url : findResource(name);
    }

    public Enumeration<URL> getResources(String name) throws java.io.IOException {
        java.util.ArrayList<URL> urls = new java.util.ArrayList<URL>();
        if (parent != null) {
            urls.addAll(Collections.list(parent.getResources(name)));
        }
        urls.addAll(Collections.list(findResources(name)));
        return Collections.enumeration(urls);
    }

    public InputStream getResourceAsStream(String name) {
        URL url = getResource(name);
        if (url == null) {
            return null;
        }
        if (url.toExternalForm().equals(appResourceUrl(name))) {
            /* the app's own entry: read straight from the open APK */
            byte[] data = getResourceBytes(name);
            if (data != null) {
                return new java.io.ByteArrayInputStream(data);
            }
        }
        try {
            return url.openStream();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    static native byte[] getResourceBytes(String name);

    /** The APK the class path's resources come from (the running app's), or null. */
    static native String appApkPath();

    /** The URL Android's class loaders give an APK entry (BaseDexClassLoader builds the same). */
    static String appResourceUrl(String name) {
        String apk = appApkPath();
        return apk == null ? null : jarUrl(apk, name);
    }

    /** "jar:file:<apk>!/<name>" with both parts percent-encoded where a URI needs it (spaces, %, #, ?). */
    static String jarUrl(String apk, String name) {
        return "jar:file:" + quote(apk) + "!/" + quote(name);
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || "-._~/!$&'()*+,;=:@".indexOf(c) >= 0) {
                sb.append((char) c);
            } else {
                sb.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 15, 16)));
            }
        }
        return sb.toString();
    }

    public static InputStream getSystemResourceAsStream(String name) {
        return getSystemClassLoader().getResourceAsStream(name);
    }

    public static URL getSystemResource(String name) {
        return getSystemClassLoader().getResource(name);
    }

    protected String findLibrary(String libname) {
        return null;
    }

    private static final class UnsupportedOperationError extends ClassFormatError {
        UnsupportedOperationError(String msg) {
            super(msg);
        }
    }

    protected final Class<?> findSystemClass(String name) throws ClassNotFoundException {
        return Class.forName(name, false, getSystemClassLoader());
    }

    protected Package definePackage(String name, String specTitle, String specVersion, String specVendor,
            String implTitle, String implVersion, String implVendor, URL sealBase) throws IllegalArgumentException {
        return Package.getPackage(name);
    }

    protected Package getPackage(String name) {
        return Package.getPackage(name);
    }

    protected Package[] getPackages() {
        return new Package[0];
    }

    protected URL findResource(String name) {
        return null;
    }

    protected Enumeration<URL> findResources(String name) throws java.io.IOException {
        return Collections.emptyEnumeration();
    }

    protected static boolean registerAsParallelCapable() {
        return true;
    }

    protected final void resolveClass(Class<?> c) {
    }

    protected final void setSigners(Class<?> c, Object[] signers) {
    }

    public static Enumeration<URL> getSystemResources(String name) throws java.io.IOException {
        return getSystemClassLoader().getResources(name);
    }

    public void clearAssertionStatus() {
    }

    public void setClassAssertionStatus(String className, boolean enabled) {
    }

    public void setDefaultAssertionStatus(boolean enabled) {
    }

    public void setPackageAssertionStatus(String packageName, boolean enabled) {
    }
}
