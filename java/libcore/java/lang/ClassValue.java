package java.lang;

import java.util.WeakHashMap;

/**
 * Lazily computes one value per class. computeValue runs outside the lock; if two threads race, the first value
 * stored wins and both get it, as the JDK guarantees.
 */
public abstract class ClassValue<T> {
    private final WeakHashMap<Class<?>, Object> values = new WeakHashMap<Class<?>, Object>();
    private static final Object NULL = new Object();
    private int version;

    protected ClassValue() {
    }

    protected abstract T computeValue(Class<?> type);

    @SuppressWarnings("unchecked")
    public T get(Class<?> type) {
        int v;
        synchronized (values) {
            Object o = values.get(type);
            if (o != null) {
                return o == NULL ? null : (T) o;
            }
            v = version;
        }
        T computed = computeValue(type);
        synchronized (values) {
            Object o = values.get(type);
            if (o != null) {
                return o == NULL ? null : (T) o;
            }
            if (v == version) {
                values.put(type, computed == null ? NULL : computed);
            }
            return computed;
        }
    }

    public void remove(Class<?> type) {
        synchronized (values) {
            values.remove(type);
            version++;
        }
    }
}
