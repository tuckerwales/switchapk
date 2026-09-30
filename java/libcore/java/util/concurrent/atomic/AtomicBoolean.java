package java.util.concurrent.atomic;

public class AtomicBoolean implements java.io.Serializable {
    private volatile boolean value;

    public AtomicBoolean(boolean initialValue) {
        value = initialValue;
    }

    public AtomicBoolean() {
    }

    public final boolean get() {
        return value;
    }

    public final synchronized boolean compareAndSet(boolean expect, boolean update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    public boolean weakCompareAndSet(boolean expect, boolean update) {
        return compareAndSet(expect, update);
    }

    public final void set(boolean newValue) {
        value = newValue;
    }

    public final void lazySet(boolean newValue) {
        value = newValue;
    }

    public final boolean getPlain() {
        return value;
    }

    public final void setPlain(boolean newValue) {
        value = newValue;
    }

    public final synchronized boolean getAndSet(boolean newValue) {
        boolean prev = value;
        value = newValue;
        return prev;
    }

    public String toString() {
        return Boolean.toString(get());
    }
}
