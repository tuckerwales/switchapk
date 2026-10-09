package java.lang;

public abstract class Process {
    public abstract java.io.OutputStream getOutputStream();

    public abstract java.io.InputStream getInputStream();

    public abstract java.io.InputStream getErrorStream();

    public abstract int waitFor() throws InterruptedException;

    public abstract int exitValue();

    public abstract void destroy();

    public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        long remaining = unit.toNanos(timeout);
        long deadline = System.nanoTime() + remaining;
        do {
            if (isAlive()) {
                Thread.sleep(Math.max(Math.min(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining) + 1, 100), 1));
            } else {
                return true;
            }
            remaining = deadline - System.nanoTime();
        } while (remaining > 0);
        return !isAlive();
    }

    public Process destroyForcibly() {
        destroy();
        return this;
    }

    public boolean isAlive() {
        try {
            exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }
}
