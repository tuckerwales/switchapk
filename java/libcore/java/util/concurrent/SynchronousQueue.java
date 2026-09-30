package java.util.concurrent;

public class SynchronousQueue<E> extends LinkedBlockingDeque<E> {
    public SynchronousQueue() {
        super(1);
    }

    public SynchronousQueue(boolean fair) {
        super(1);
    }

    public boolean offer(E e) {
        // no waiting consumers can be detected cheaply; behave as zero-capacity for executors
        return false;
    }
}
