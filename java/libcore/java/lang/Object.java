package java.lang;

public class Object {
    public Object() {
    }

    public final native Class<?> getClass();

    public native int hashCode();

    public boolean equals(Object obj) {
        return this == obj;
    }

    protected Object clone() throws CloneNotSupportedException {
        return internalClone();
    }

    private native Object internalClone();

    public String toString() {
        return getClass().getName() + "@" + Integer.toHexString(hashCode());
    }

    public final native void notify();

    public final native void notifyAll();

    public final void wait() throws InterruptedException {
        wait(0, 0);
    }

    public final void wait(long millis) throws InterruptedException {
        wait(millis, 0);
    }

    public final native void wait(long millis, int nanos) throws InterruptedException;

    protected void finalize() throws Throwable {
    }
}
