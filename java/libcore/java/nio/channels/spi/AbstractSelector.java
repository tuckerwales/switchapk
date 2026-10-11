package java.nio.channels.spi;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractSelector extends Selector {
    private final SelectorProvider provider;
    private final Set<SelectionKey> cancelledKeys = new HashSet<SelectionKey>();
    private volatile boolean open = true;

    protected AbstractSelector(SelectorProvider provider) {
        this.provider = provider;
    }

    void cancel(SelectionKey k) {
        synchronized (cancelledKeys) {
            cancelledKeys.add(k);
        }
    }

    public final void close() throws IOException {
        synchronized (this) {
            if (!open) {
                return;
            }
            open = false;
        }
        implCloseSelector();
    }

    protected abstract void implCloseSelector() throws IOException;

    public final boolean isOpen() {
        return open;
    }

    public final SelectorProvider provider() {
        return provider;
    }

    protected final Set<SelectionKey> cancelledKeys() {
        return cancelledKeys;
    }

    protected abstract SelectionKey register(AbstractSelectableChannel ch, int ops, Object att);

    protected final void deregister(AbstractSelectionKey key) {
        ((AbstractSelectableChannel) key.channel()).removeKey(key);
    }

    /** Thread.interrupt does not wake a select here; wakeup() does. */
    protected final void begin() {
    }

    protected final void end() {
    }
}
