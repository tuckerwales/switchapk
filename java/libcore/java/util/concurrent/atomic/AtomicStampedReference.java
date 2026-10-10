package java.util.concurrent.atomic;

/** A reference and a int stamp updated together: an immutable pair swapped by compare-and-set, as in OpenJDK. */
public class AtomicStampedReference<V> {
    private static final class Pair<T> {
        final T reference;
        final int stamp;

        private Pair(T reference, int stamp) {
            this.reference = reference;
            this.stamp = stamp;
        }

        static <T> Pair<T> of(T reference, int stamp) { return new Pair<T>(reference, stamp); }
    }

    private final AtomicReference<Pair<V>> pair;

    public AtomicStampedReference(V initialRef, int initialtamped) {
        pair = new AtomicReference<Pair<V>>(Pair.of(initialRef, initialtamped));
    }

    public V getReference() { return pair.get().reference; }
    public int getStamp() { return pair.get().stamp; }

    public V get(int[] stampHolder) {
        Pair<V> p = pair.get();
        stampHolder[0] = p.stamp;
        return p.reference;
    }

    public boolean weakCompareAndSet(V expectedReference, V newReference, int expectedtamped, int newtamped) {
        return compareAndSet(expectedReference, newReference, expectedtamped, newtamped);
    }

    public boolean compareAndSet(V expectedReference, V newReference, int expectedtamped, int newtamped) {
        Pair<V> current = pair.get();
        return expectedReference == current.reference && expectedtamped == current.stamp
                && ((newReference == current.reference && newtamped == current.stamp)
                        || pair.compareAndSet(current, Pair.of(newReference, newtamped)));
    }

    public void set(V newReference, int newtamped) {
        Pair<V> current = pair.get();
        if (newReference != current.reference || newtamped != current.stamp) pair.set(Pair.of(newReference, newtamped));
    }

    public boolean attemptStamp(V expectedReference, int newtamped) {
        Pair<V> current = pair.get();
        return expectedReference == current.reference
                && (newtamped == current.stamp || pair.compareAndSet(current, Pair.of(expectedReference, newtamped)));
    }
}
