package java.util.concurrent.atomic;

import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

public class AtomicReference<V> implements java.io.Serializable {
    private volatile V value;

    public AtomicReference(V initialValue) {
        value = initialValue;
    }

    public AtomicReference() {
    }

    public final V get() {
        return value;
    }

    public final void set(V newValue) {
        value = newValue;
    }

    public final void lazySet(V newValue) {
        value = newValue;
    }

    public final V getPlain() {
        return value;
    }

    public final void setPlain(V newValue) {
        value = newValue;
    }

    public final V getAcquire() {
        return value;
    }

    public final void setRelease(V newValue) {
        value = newValue;
    }

    public final synchronized boolean compareAndSet(V expect, V update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    public final boolean weakCompareAndSet(V expect, V update) {
        return compareAndSet(expect, update);
    }

    public final synchronized V getAndSet(V newValue) {
        V prev = value;
        value = newValue;
        return prev;
    }

    public final synchronized V getAndUpdate(UnaryOperator<V> updateFunction) {
        V prev = value;
        value = updateFunction.apply(prev);
        return prev;
    }

    public final synchronized V updateAndGet(UnaryOperator<V> updateFunction) {
        value = updateFunction.apply(value);
        return value;
    }

    public final synchronized V getAndAccumulate(V x, BinaryOperator<V> accumulatorFunction) {
        V prev = value;
        value = accumulatorFunction.apply(prev, x);
        return prev;
    }

    public final synchronized V accumulateAndGet(V x, BinaryOperator<V> accumulatorFunction) {
        value = accumulatorFunction.apply(value, x);
        return value;
    }

    public String toString() {
        return String.valueOf(get());
    }
}
