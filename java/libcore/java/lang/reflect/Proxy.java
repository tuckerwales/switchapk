package java.lang.reflect;

public class Proxy implements java.io.Serializable {
    protected InvocationHandler h;

    protected Proxy(InvocationHandler h) {
        this.h = h;
    }

    public static Object newProxyInstance(ClassLoader loader, Class<?>[] interfaces, InvocationHandler h) {
        if (h == null) {
            throw new NullPointerException("h == null");
        }
        Class<?> c = getProxyClass(loader, interfaces);
        return newProxyNative(c, h);
    }

    public static Class<?> getProxyClass(ClassLoader loader, Class<?>... interfaces) {
        for (Class<?> i : interfaces) {
            if (!i.isInterface()) {
                throw new IllegalArgumentException(i.getName() + " is not an interface");
            }
        }
        return generateProxy(interfaces);
    }

    private static native Class<?> generateProxy(Class<?>[] interfaces);

    private static native Object newProxyNative(Class<?> proxyClass, InvocationHandler h);

    public static boolean isProxyClass(Class<?> cl) {
        return Proxy.class.isAssignableFrom(cl) && cl != Proxy.class;
    }

    public static InvocationHandler getInvocationHandler(Object proxy) {
        if (!(proxy instanceof Proxy)) {
            throw new IllegalArgumentException("not a proxy instance");
        }
        return ((Proxy) proxy).h;
    }

    /** Called from generated proxy methods. */
    static Object invokeHandler(Proxy proxy, Method method, Object[] args) throws Throwable {
        return proxy.h.invoke(proxy, method, args);
    }
}
