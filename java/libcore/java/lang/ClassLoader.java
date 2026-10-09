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
            systemLoader = new SystemClassLoader();
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

    public URL getResource(String name) {
        return null;
    }

    public Enumeration<URL> getResources(String name) throws java.io.IOException {
        return Collections.emptyEnumeration();
    }

    public InputStream getResourceAsStream(String name) {
        byte[] data = getResourceBytes(name);
        return data == null ? null : new java.io.ByteArrayInputStream(data);
    }

    static native byte[] getResourceBytes(String name);

    public static InputStream getSystemResourceAsStream(String name) {
        return getSystemClassLoader().getResourceAsStream(name);
    }

    public static URL getSystemResource(String name) {
        return null;
    }

    protected String findLibrary(String libname) {
        return null;
    }

    private static final class UnsupportedOperationError extends ClassFormatError {
        UnsupportedOperationError(String msg) {
            super(msg);
        }
    }

    static final class SystemClassLoader extends ClassLoader {
        SystemClassLoader() {
            super(null);
        }

        public String toString() {
            return "dalvik.system.PathClassLoader[switchapk]";
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
