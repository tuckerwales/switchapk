package java.lang.ref;

public abstract class Reference<T> {
    volatile T referent;
    final ReferenceQueue<? super T> queue;

    Reference(T referent) {
        this(referent, null);
    }

    Reference(T referent, ReferenceQueue<? super T> queue) {
        this.referent = referent;
        this.queue = queue;
    }

    public T get() {
        return referent;
    }

    public void clear() {
        referent = null;
    }

    public boolean isEnqueued() {
        return false;
    }

    public boolean enqueue() {
        return false;
    }

    public final boolean refersTo(T obj) {
        return referent == obj;
    }
}
