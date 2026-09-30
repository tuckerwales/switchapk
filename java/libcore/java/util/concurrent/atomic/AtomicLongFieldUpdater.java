package java.util.concurrent.atomic;

import java.lang.reflect.Field;

public abstract class AtomicLongFieldUpdater<T> {
    public static <U> AtomicLongFieldUpdater<U> newUpdater(Class<U> tclass, String fieldName) {
        return new Impl<U>(tclass, fieldName);
    }

    protected AtomicLongFieldUpdater() {
    }

    public abstract boolean compareAndSet(T obj, long expect, long update);

    public boolean weakCompareAndSet(T obj, long expect, long update) {
        return compareAndSet(obj, expect, update);
    }

    public abstract void set(T obj, long newValue);

    public void lazySet(T obj, long newValue) {
        set(obj, newValue);
    }

    public abstract long get(T obj);

    public long getAndSet(T obj, long newValue) {
        synchronized (this) {
            long prev = get(obj);
            set(obj, newValue);
            return prev;
        }
    }

    public long getAndIncrement(T obj) {
        return getAndAdd(obj, 1);
    }

    public long getAndDecrement(T obj) {
        return getAndAdd(obj, -1);
    }

    public long getAndAdd(T obj, long delta) {
        synchronized (this) {
            long prev = get(obj);
            set(obj, prev + delta);
            return prev;
        }
    }

    public long incrementAndGet(T obj) {
        return addAndGet(obj, 1);
    }

    public long decrementAndGet(T obj) {
        return addAndGet(obj, -1);
    }

    public long addAndGet(T obj, long delta) {
        synchronized (this) {
            long next = get(obj) + delta;
            set(obj, next);
            return next;
        }
    }

    private static final class Impl<T> extends AtomicLongFieldUpdater<T> {
        private final Field field;

        Impl(Class<T> tclass, String fieldName) {
            try {
                field = tclass.getDeclaredField(fieldName);
                field.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        public synchronized boolean compareAndSet(T obj, long expect, long update) {
            if (get(obj) == expect) {
                set(obj, update);
                return true;
            }
            return false;
        }

        public void set(T obj, long newValue) {
            try {
                field.set(obj, newValue);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        public long get(T obj) {
            try {
                return ((Long) field.get(obj));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
