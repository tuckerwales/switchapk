package java.lang;

import java.util.HashMap;

public class InheritableThreadLocal<T> extends ThreadLocal<T> {
    public InheritableThreadLocal() {
    }

    protected T childValue(T parentValue) {
        return parentValue;
    }

    HashMap<ThreadLocal<?>, Object> getMap(Thread t) {
        if (t.inheritableThreadLocals == null) {
            t.inheritableThreadLocals = new HashMap<ThreadLocal<?>, Object>();
        }
        return t.inheritableThreadLocals;
    }
}
