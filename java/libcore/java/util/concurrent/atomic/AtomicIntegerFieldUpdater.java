package java.util.concurrent.atomic;

import java.lang.reflect.Field;

public abstract class AtomicIntegerFieldUpdater<T> {
    public static <U> AtomicIntegerFieldUpdater<U> newUpdater(Class<U> tclass, String fieldName) {
        return new Impl<U>(tclass, fieldName);
    }

    protected AtomicIntegerFieldUpdater() {
    }

    public abstract boolean compareAndSet(T obj, int expect, int update);

    public boolean weakCompareAndSet(T obj, int expect, int update) {
        return compareAndSet(obj, expect, update);
    }

    public abstract void set(T obj, int newValue);

    public void lazySet(T obj, int newValue) {
        set(obj, newValue);
    }

    public abstract int get(T obj);

    public int getAndSet(T obj, int newValue) {
        synchronized (this) {
            int prev = get(obj);
            set(obj, newValue);
            return prev;
        }
    }

    public int getAndIncrement(T obj) {
        return getAndAdd(obj, 1);
    }

    public int getAndDecrement(T obj) {
        return getAndAdd(obj, -1);
    }

    public int getAndAdd(T obj, int delta) {
        synchronized (this) {
            int prev = get(obj);
            set(obj, prev + delta);
            return prev;
        }
    }

    public int incrementAndGet(T obj) {
        return addAndGet(obj, 1);
    }

    public int decrementAndGet(T obj) {
        return addAndGet(obj, -1);
    }

    public int addAndGet(T obj, int delta) {
        synchronized (this) {
            int next = get(obj) + delta;
            set(obj, next);
            return next;
        }
    }

    private static final class Impl<T> extends AtomicIntegerFieldUpdater<T> {
        private final Field field;

        Impl(Class<T> tclass, String fieldName) {
            try {
                field = tclass.getDeclaredField(fieldName);
                field.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        public synchronized boolean compareAndSet(T obj, int expect, int update) {
            if (get(obj) == expect) {
                set(obj, update);
                return true;
            }
            return false;
        }

        public void set(T obj, int newValue) {
            try {
                field.set(obj, newValue);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        public int get(T obj) {
            try {
                return ((Integer) field.get(obj));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
