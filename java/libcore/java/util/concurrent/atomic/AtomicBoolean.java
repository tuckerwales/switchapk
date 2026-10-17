package java.util.concurrent.atomic;

public class AtomicBoolean implements java.io.Serializable {
    private static final long serialVersionUID = 4654671469794556979L;
    /** Java stores the flag as an int, and that is what the serial form carries. */
    private static final java.io.ObjectStreamField[] serialPersistentFields = {
        new java.io.ObjectStreamField("value", Integer.TYPE),
    };
    private volatile boolean value;

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        java.io.ObjectOutputStream.PutField fields = s.putFields();
        fields.put("value", value ? 1 : 0);
        s.writeFields();
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        value = s.readFields().get("value", 0) != 0;
    }

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
