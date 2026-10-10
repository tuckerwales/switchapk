package sun.nio.ch;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import libcore.io.Net;

/**
 * A selector over poll(2). Waits are cut into slices so wakeup() and close() from another thread are seen
 * within one slice (the Switch has no pipes to wake a poll with).
 */
final class PollSelectorImpl extends AbstractSelector {
    private static final int SLICE_MS = 50;

    private final HashSet<SelectionKey> keys = new HashSet<SelectionKey>();
    private final Set<SelectionKey> publicKeys = Collections.unmodifiableSet(keys);
    private final HashSet<SelectionKey> selected = new HashSet<SelectionKey>();
    private volatile boolean wakeup;

    PollSelectorImpl(SelectorProvider provider) {
        super(provider);
    }

    private void ensureOpen() {
        if (!isOpen()) {
            throw new ClosedSelectorException();
        }
    }

    protected SelectionKey register(AbstractSelectableChannel ch, int ops, Object att) {
        if (!(ch instanceof SelChImpl)) {
            throw new java.nio.channels.IllegalSelectorException();
        }
        SelectionKeyImpl k = new SelectionKeyImpl(ch, this);
        k.attach(att);
        synchronized (keys) {
            ensureOpen();
            keys.add(k);
        }
        k.interestOps(ops);
        return k;
    }

    public Set<SelectionKey> keys() {
        ensureOpen();
        return publicKeys;
    }

    public Set<SelectionKey> selectedKeys() {
        ensureOpen();
        return selected;
    }

    private void processCancelled() {
        Set<SelectionKey> cancelled = cancelledKeys();
        synchronized (cancelled) {
            if (cancelled.isEmpty()) {
                return;
            }
            for (SelectionKey k : cancelled) {
                synchronized (keys) {
                    keys.remove(k);
                }
                selected.remove(k);
                deregister((SelectionKeyImpl) k);
            }
            cancelled.clear();
        }
    }

    /** timeoutMs: -1 no wait, 0 forever. */
    private int doSelect(long timeoutMs) throws IOException {
        ensureOpen();
        synchronized (this) {
            processCancelled();
            ArrayList<SelectionKeyImpl> watched = new ArrayList<SelectionKeyImpl>();
            synchronized (keys) {
                for (SelectionKey k : keys) {
                    watched.add((SelectionKeyImpl) k);
                }
            }
            int n = 0;
            int[] fds = new int[watched.size()];
            int[] events = new int[watched.size()];
            SelectionKeyImpl[] order = new SelectionKeyImpl[watched.size()];
            for (SelectionKeyImpl k : watched) {
                if (!k.isValid()) {
                    continue;
                }
                SelChImpl ch = (SelChImpl) k.channel();
                int fd = ch.selFd();
                int ev = ch.pollEvents(k.rawInterestOps());
                if (fd < 0 || ev == 0) {
                    continue;
                }
                fds[n] = fd;
                events[n] = ev;
                order[n] = k;
                n++;
            }
            int[] revents = new int[n];
            long deadline = timeoutMs > 0 ? System.currentTimeMillis() + timeoutMs : 0;
            int ready = 0;
            begin();
            try {
                for (;;) {
                    if (wakeup) {
                        break;
                    }
                    int slice;
                    if (timeoutMs < 0) {
                        slice = 0;
                    } else if (timeoutMs == 0) {
                        slice = SLICE_MS;
                    } else {
                        long left = deadline - System.currentTimeMillis();
                        if (left <= 0) {
                            break;
                        }
                        slice = (int) Math.min(SLICE_MS, left);
                    }
                    ready = Net.poll(fds, events, revents, n, slice);
                    if (ready > 0 || timeoutMs < 0 || !isOpen()) {
                        break;
                    }
                    if (!cancelledKeys().isEmpty()) {
                        break; // a key went away: let the caller look again
                    }
                }
            } finally {
                end();
                wakeup = false;
            }
            ensureOpen();
            processCancelled();
            int updated = 0;
            for (int i = 0; i < n && ready > 0; i++) {
                if (revents[i] == 0) {
                    continue;
                }
                SelectionKeyImpl k = order[i];
                if (!k.isValid()) {
                    continue;
                }
                SelChImpl ch = (SelChImpl) k.channel();
                int ops = ch.readyOps(revents[i], k.rawInterestOps());
                if (ops == 0) {
                    continue;
                }
                if (selected.contains(k)) {
                    int before = k.readyOps;
                    k.readyOps = before | ops;
                    if (k.readyOps != before) {
                        updated++;
                    }
                } else {
                    k.readyOps = ops;
                    selected.add(k);
                    updated++;
                }
            }
            return updated;
        }
    }

    public int selectNow() throws IOException {
        return doSelect(-1);
    }

    public int select(long timeout) throws IOException {
        if (timeout < 0) {
            throw new IllegalArgumentException("Negative timeout");
        }
        return doSelect(timeout);
    }

    public int select() throws IOException {
        return doSelect(0);
    }

    public Selector wakeup() {
        wakeup = true;
        return this;
    }

    protected void implCloseSelector() throws IOException {
        wakeup = true;
        synchronized (this) {
            ArrayList<SelectionKey> all;
            synchronized (keys) {
                all = new ArrayList<SelectionKey>(keys);
                keys.clear();
            }
            for (SelectionKey k : all) {
                k.cancel();
            }
            processCancelled();
            selected.clear();
        }
    }
}
