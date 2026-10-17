package java.util.concurrent.atomic;

public class LongAdder extends Number implements java.io.Serializable {
    private static final long serialVersionUID = 7249069246863182397L;
    private long value;

    /** Java writes a LongAdder as this proxy holding its sum. */
    private static class SerializationProxy implements java.io.Serializable {
        private static final long serialVersionUID = 7249069246863182397L;
        private final long value;

        SerializationProxy(LongAdder a) {
            value = a.sum();
        }

        private Object readResolve() {
            LongAdder a = new LongAdder();
            a.value = value;
            return a;
        }
    }

    private Object writeReplace() {
        return new SerializationProxy(this);
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.InvalidObjectException {
        throw new java.io.InvalidObjectException("Proxy required");
    }

    public LongAdder() {
    }

    public synchronized void add(long x) {
        value += x;
    }

    public void increment() {
        add(1L);
    }

    public void decrement() {
        add(-1L);
    }

    public synchronized long sum() {
        return value;
    }

    public synchronized void reset() {
        value = 0;
    }

    public synchronized long sumThenReset() {
        long v = value;
        value = 0;
        return v;
    }

    public String toString() {
        return Long.toString(sum());
    }

    public long longValue() {
        return sum();
    }

    public int intValue() {
        return (int) sum();
    }

    public float floatValue() {
        return (float) sum();
    }

    public double doubleValue() {
        return (double) sum();
    }
}
