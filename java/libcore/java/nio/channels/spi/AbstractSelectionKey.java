package java.nio.channels.spi;

import java.nio.channels.SelectionKey;

public abstract class AbstractSelectionKey extends SelectionKey {
    private volatile boolean valid = true;

    protected AbstractSelectionKey() {
    }

    public final boolean isValid() {
        return valid;
    }

    void invalidate() {
        valid = false;
    }

    public final void cancel() {
        synchronized (this) {
            if (!valid) {
                return;
            }
            valid = false;
        }
        ((AbstractSelector) selector()).cancel(this);
    }
}
