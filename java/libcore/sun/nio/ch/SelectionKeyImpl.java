package sun.nio.ch;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectionKey;

final class SelectionKeyImpl extends AbstractSelectionKey {
    private final SelectableChannel channel;
    private final PollSelectorImpl selector;
    private volatile int interestOps;
    volatile int readyOps;

    SelectionKeyImpl(SelectableChannel channel, PollSelectorImpl selector) {
        this.channel = channel;
        this.selector = selector;
    }

    public SelectableChannel channel() {
        return channel;
    }

    public Selector selector() {
        return selector;
    }

    private void ensureValid() {
        if (!isValid()) {
            throw new CancelledKeyException();
        }
    }

    public int interestOps() {
        ensureValid();
        return interestOps;
    }

    public SelectionKey interestOps(int ops) {
        ensureValid();
        if ((ops & ~channel.validOps()) != 0) {
            throw new IllegalArgumentException("Invalid interest ops " + ops);
        }
        interestOps = ops;
        return this;
    }

    int rawInterestOps() {
        return interestOps;
    }

    public int readyOps() {
        ensureValid();
        return readyOps;
    }
}
