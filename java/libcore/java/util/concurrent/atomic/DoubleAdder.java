package java.util.concurrent.atomic;

public class DoubleAdder extends Number implements java.io.Serializable {
    private static final long serialVersionUID = 7249069246863182397L;
    private double value;

    public DoubleAdder() {
    }

    public synchronized void add(double x) {
        value += x;
    }

    public synchronized double sum() {
        return value;
    }

    public synchronized void reset() {
        value = 0.0;
    }

    public synchronized double sumThenReset() {
        double v = value;
        value = 0.0;
        return v;
    }

    public String toString() {
        return Double.toString(sum());
    }

    public double doubleValue() {
        return sum();
    }

    public long longValue() {
        return (long) sum();
    }

    public int intValue() {
        return (int) sum();
    }

    public float floatValue() {
        return (float) sum();
    }
}
