package java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;

public class ReentrantReadWriteLock implements ReadWriteLock, java.io.Serializable {
    private final ReentrantLock lock = new ReentrantLock();
    private final ReadLock readerLock = new ReadLock(this);
    private final WriteLock writerLock = new WriteLock(this);

    public ReentrantReadWriteLock() {
    }

    public ReentrantReadWriteLock(boolean fair) {
    }

    public ReadLock readLock() {
        return readerLock;
    }

    public WriteLock writeLock() {
        return writerLock;
    }

    public boolean isWriteLocked() {
        return lock.isLocked();
    }

    public boolean isWriteLockedByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }

    public int getReadLockCount() {
        return 0;
    }

    public static class ReadLock implements Lock, java.io.Serializable {
        private final ReentrantLock l;

        protected ReadLock(ReentrantReadWriteLock lock) {
            l = lock.lock;
        }

        public void lock() {
            l.lock();
        }

        public void lockInterruptibly() throws InterruptedException {
            l.lockInterruptibly();
        }

        public boolean tryLock() {
            return l.tryLock();
        }

        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            return l.tryLock(timeout, unit);
        }

        public void unlock() {
            l.unlock();
        }

        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    public static class WriteLock implements Lock, java.io.Serializable {
        private final ReentrantLock l;

        protected WriteLock(ReentrantReadWriteLock lock) {
            l = lock.lock;
        }

        public void lock() {
            l.lock();
        }

        public void lockInterruptibly() throws InterruptedException {
            l.lockInterruptibly();
        }

        public boolean tryLock() {
            return l.tryLock();
        }

        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            return l.tryLock(timeout, unit);
        }

        public void unlock() {
            l.unlock();
        }

        public Condition newCondition() {
            return l.newCondition();
        }

        public boolean isHeldByCurrentThread() {
            return l.isHeldByCurrentThread();
        }
    }
}
