package java.util.concurrent.atomic;

/** A reference and a boolean mark updated together: an immutable pair swapped by compare-and-set, as in OpenJDK. */
public class AtomicMarkableReference<V> {
    private static final class Pair<T> {
        final T reference;
        final boolean mark;

        private Pair(T reference, boolean mark) {
            this.reference = reference;
            this.mark = mark;
        }

        static <T> Pair<T> of(T reference, boolean mark) { return new Pair<T>(reference, mark); }
    }

    private final AtomicReference<Pair<V>> pair;

    public AtomicMarkableReference(V initialRef, boolean initialMark) {
        pair = new AtomicReference<Pair<V>>(Pair.of(initialRef, initialMark));
    }

    public V getReference() { return pair.get().reference; }
    public boolean isMarked() { return pair.get().mark; }

    public V get(boolean[] markHolder) {
        Pair<V> p = pair.get();
        markHolder[0] = p.mark;
        return p.reference;
    }

    public boolean weakCompareAndSet(V expectedReference, V newReference, boolean expectedMark, boolean newMark) {
        return compareAndSet(expectedReference, newReference, expectedMark, newMark);
    }

    public boolean compareAndSet(V expectedReference, V newReference, boolean expectedMark, boolean newMark) {
        Pair<V> current = pair.get();
        return expectedReference == current.reference && expectedMark == current.mark
                && ((newReference == current.reference && newMark == current.mark)
                        || pair.compareAndSet(current, Pair.of(newReference, newMark)));
    }

    public void set(V newReference, boolean newMark) {
        Pair<V> current = pair.get();
        if (newReference != current.reference || newMark != current.mark) pair.set(Pair.of(newReference, newMark));
    }

    public boolean attemptMark(V expectedReference, boolean newMark) {
        Pair<V> current = pair.get();
        return expectedReference == current.reference
                && (newMark == current.mark || pair.compareAndSet(current, Pair.of(expectedReference, newMark)));
    }
}
