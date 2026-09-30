package java.util.concurrent.atomic;

import java.lang.reflect.Field;

public abstract class AtomicReferenceFieldUpdater<T, V> {
    public static <U, W> AtomicReferenceFieldUpdater<U, W> newUpdater(Class<U> tclass, Class<W> vclass, String fieldName) {
        return new Impl<U, W>(tclass, fieldName);
    }

    protected AtomicReferenceFieldUpdater() {
    }

    public abstract boolean compareAndSet(T obj, V expect, V update);

    public boolean weakCompareAndSet(T obj, V expect, V update) {
        return compareAndSet(obj, expect, update);
    }

    public abstract void set(T obj, V newValue);

    public void lazySet(T obj, V newValue) {
        set(obj, newValue);
    }

    public abstract V get(T obj);

    public V getAndSet(T obj, V newValue) {
        synchronized (this) {
            V prev = get(obj);
            set(obj, newValue);
            return prev;
        }
    }

    private static final class Impl<T, V> extends AtomicReferenceFieldUpdater<T, V> {
        private final Field field;

        Impl(Class<T> tclass, String fieldName) {
            try {
                field = tclass.getDeclaredField(fieldName);
                field.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        public synchronized boolean compareAndSet(T obj, V expect, V update) {
            if (get(obj) == expect) {
                set(obj, update);
                return true;
            }
            return false;
        }

        public void set(T obj, V newValue) {
            try {
                field.set(obj, newValue);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("unchecked")
        public V get(T obj) {
            try {
                return (V) field.get(obj);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
