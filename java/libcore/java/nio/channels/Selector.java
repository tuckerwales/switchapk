package java.nio.channels;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;

public abstract class Selector implements Closeable {
    protected Selector() {
    }

    public static Selector open() throws IOException {
        return SelectorProvider.provider().openSelector();
    }

    public abstract boolean isOpen();

    public abstract SelectorProvider provider();

    public abstract Set<SelectionKey> keys();

    public abstract Set<SelectionKey> selectedKeys();

    public abstract int selectNow() throws IOException;

    public abstract int select(long timeout) throws IOException;

    public abstract int select() throws IOException;

    private int doSelect(Consumer<SelectionKey> action, long timeout) throws IOException {
        synchronized (this) {
            Set<SelectionKey> selected = selectedKeys();
            selected.clear();
            int n = timeout < 0 ? selectNow() : select(timeout);
            int count = 0;
            for (Iterator<SelectionKey> it = selected.iterator(); it.hasNext();) {
                SelectionKey k = it.next();
                it.remove();
                if (k.isValid()) {
                    action.accept(k);
                    count++;
                }
            }
            return count;
        }
    }

    public int select(Consumer<SelectionKey> action, long timeout) throws IOException {
        if (timeout < 0) {
            throw new IllegalArgumentException("Negative timeout");
        }
        return doSelect(action, timeout);
    }

    public int select(Consumer<SelectionKey> action) throws IOException {
        return doSelect(action, 0);
    }

    public int selectNow(Consumer<SelectionKey> action) throws IOException {
        return doSelect(action, -1);
    }

    public abstract Selector wakeup();

    public abstract void close() throws IOException;
}
