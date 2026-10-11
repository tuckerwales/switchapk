package java.nio.channels.spi;

import java.io.IOException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Channel;
import java.nio.channels.InterruptibleChannel;

/* No Thread.interrupt wakeups: begin/end only report a close that happened during the operation. */
public abstract class AbstractInterruptibleChannel implements Channel, InterruptibleChannel {
    private final Object closeLock = new Object();
    private volatile boolean closed;

    protected AbstractInterruptibleChannel() {
    }

    public final void close() throws IOException {
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closed = true;
            implCloseChannel();
        }
    }

    protected abstract void implCloseChannel() throws IOException;

    public final boolean isOpen() {
        return !closed;
    }

    protected final void begin() {
    }

    protected final void end(boolean completed) throws AsynchronousCloseException {
        if (!completed && closed) {
            throw new AsynchronousCloseException();
        }
    }
}
