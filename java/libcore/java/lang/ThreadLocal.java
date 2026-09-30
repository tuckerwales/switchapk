package java.lang;

import java.util.HashMap;
import java.util.function.Supplier;

public class ThreadLocal<T> {
    public ThreadLocal() {
    }

    protected T initialValue() {
        return null;
    }

    public static <S> ThreadLocal<S> withInitial(final Supplier<? extends S> supplier) {
        return new ThreadLocal<S>() {
            protected S initialValue() {
                return supplier.get();
            }
        };
    }

    HashMap<ThreadLocal<?>, Object> getMap(Thread t) {
        if (t.threadLocals == null) {
            t.threadLocals = new HashMap<ThreadLocal<?>, Object>();
        }
        return t.threadLocals;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        HashMap<ThreadLocal<?>, Object> map = getMap(Thread.currentThread());
        if (map.containsKey(this)) {
            return (T) map.get(this);
        }
        T v = initialValue();
        map.put(this, v);
        return v;
    }

    public void set(T value) {
        getMap(Thread.currentThread()).put(this, value);
    }

    public void remove() {
        getMap(Thread.currentThread()).remove(this);
    }
}
