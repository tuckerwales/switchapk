package java.nio.channels.spi;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.IllegalSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;

public abstract class AbstractSelectableChannel extends SelectableChannel {
    private final SelectorProvider provider;
    private final ArrayList<SelectionKey> keys = new ArrayList<SelectionKey>();
    private final Object regLock = new Object();
    private final Object blockingLock = new Object();
    private volatile boolean blocking = true;

    protected AbstractSelectableChannel(SelectorProvider provider) {
        this.provider = provider;
    }

    public final SelectorProvider provider() {
        return provider;
    }

    void removeKey(SelectionKey k) {
        synchronized (regLock) {
            keys.remove(k);
        }
    }

    public final boolean isRegistered() {
        synchronized (regLock) {
            for (SelectionKey k : keys) {
                if (k.isValid()) {
                    return true;
                }
            }
            return false;
        }
    }

    public final SelectionKey keyFor(Selector sel) {
        synchronized (regLock) {
            for (SelectionKey k : keys) {
                if (k.selector() == sel && k.isValid()) {
                    return k;
                }
            }
            return null;
        }
    }

    public final SelectionKey register(Selector sel, int ops, Object att) throws ClosedChannelException {
        if ((ops & ~validOps()) != 0) {
            throw new IllegalArgumentException();
        }
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
        synchronized (regLock) {
            if (blocking) {
                throw new IllegalBlockingModeException();
            }
            SelectionKey k = keyFor(sel);
            if (k != null) {
                k.attach(att);
                k.interestOps(ops);
                return k;
            }
            if (!(sel instanceof AbstractSelector) || ((AbstractSelector) sel).provider() != provider) {
                throw new IllegalSelectorException();
            }
            k = ((AbstractSelector) sel).register(this, ops, att);
            keys.add(k);
            return k;
        }
    }

    protected final void implCloseChannel() throws IOException {
        implCloseSelectableChannel();
        ArrayList<SelectionKey> copy;
        synchronized (regLock) {
            copy = new ArrayList<SelectionKey>(keys);
        }
        for (SelectionKey k : copy) {
            k.cancel();
        }
    }

    protected abstract void implCloseSelectableChannel() throws IOException;

    public final boolean isBlocking() {
        return blocking;
    }

    public final Object blockingLock() {
        return blockingLock;
    }

    public final SelectableChannel configureBlocking(boolean block) throws IOException {
        synchronized (regLock) {
            if (!isOpen()) {
                throw new ClosedChannelException();
            }
            if (blocking == block) {
                return this;
            }
            if (block && isRegistered()) {
                throw new IllegalBlockingModeException();
            }
            implConfigureBlocking(block);
            blocking = block;
        }
        return this;
    }

    protected abstract void implConfigureBlocking(boolean block) throws IOException;
}
