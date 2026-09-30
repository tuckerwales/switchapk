package android.os;

import java.util.ArrayList;

/**
 * Message queue. The main thread's queue also waits on the platform (input,
 * text entry, lifecycle) so a single loop drives the whole app, as on Android.
 */
public final class MessageQueue {
    private final boolean mQuitAllowed;
    final boolean mIsMain;
    Message mMessages;
    private final ArrayList<IdleHandler> mIdleHandlers = new ArrayList<IdleHandler>();
    private IdleHandler[] mPendingIdleHandlers;
    private boolean mQuitting;
    private boolean mBlocked;
    private int mNextBarrierToken;

    /** Called on the main thread after a platform wake-up; drains input events. */
    static Runnable sPlatformDispatcher;

    public static void setPlatformDispatcher(Runnable r) { sPlatformDispatcher = r; }

    MessageQueue(boolean quitAllowed, boolean isMain) {
        mQuitAllowed = quitAllowed;
        mIsMain = isMain;
    }

    public boolean isIdle() {
        synchronized (this) {
            final long now = SystemClock.uptimeMillis();
            return mMessages == null || now < mMessages.when;
        }
    }

    public void addIdleHandler(IdleHandler handler) {
        if (handler == null) throw new NullPointerException("Can't add a null IdleHandler");
        synchronized (this) { mIdleHandlers.add(handler); }
    }

    public void removeIdleHandler(IdleHandler handler) {
        synchronized (this) { mIdleHandlers.remove(handler); }
    }

    public boolean isPolling() { return mBlocked; }

    Message next() {
        int pendingIdleHandlerCount = -1;
        long nextPollTimeoutMillis = 0;
        for (;;) {
            if (nextPollTimeoutMillis != 0 || mIsMain) pollOnce(nextPollTimeoutMillis);
            synchronized (this) {
                final long now = SystemClock.uptimeMillis();
                Message prevMsg = null;
                Message msg = mMessages;
                if (msg != null && msg.target == null) {
                    // barrier: find the next asynchronous message
                    do {
                        prevMsg = msg;
                        msg = msg.next;
                    } while (msg != null && !msg.isAsynchronous());
                }
                if (msg != null) {
                    if (now < msg.when) {
                        nextPollTimeoutMillis = Math.min(msg.when - now, Integer.MAX_VALUE);
                    } else {
                        mBlocked = false;
                        if (prevMsg != null) prevMsg.next = msg.next;
                        else mMessages = msg.next;
                        msg.next = null;
                        msg.markInUse();
                        return msg;
                    }
                } else {
                    nextPollTimeoutMillis = -1;
                }
                if (mQuitting) return null;
                if (pendingIdleHandlerCount < 0 && (mMessages == null || now < mMessages.when)) {
                    pendingIdleHandlerCount = mIdleHandlers.size();
                }
                if (pendingIdleHandlerCount <= 0) {
                    mBlocked = true;
                    continue;
                }
                if (mPendingIdleHandlers == null) mPendingIdleHandlers = new IdleHandler[Math.max(pendingIdleHandlerCount, 4)];
                mPendingIdleHandlers = mIdleHandlers.toArray(mPendingIdleHandlers);
            }
            for (int i = 0; i < pendingIdleHandlerCount; i++) {
                final IdleHandler idler = mPendingIdleHandlers[i];
                mPendingIdleHandlers[i] = null;
                boolean keep = false;
                try {
                    keep = idler.queueIdle();
                } catch (Throwable t) {
                    android.util.Log.wtf("MessageQueue", "IdleHandler threw exception", t);
                }
                if (!keep) {
                    synchronized (this) { mIdleHandlers.remove(idler); }
                }
            }
            pendingIdleHandlerCount = 0;
            nextPollTimeoutMillis = 0;
        }
    }

    private void pollOnce(long timeoutMillis) {
        if (mIsMain) {
            // The platform wait also returns when enqueueMessage() calls nativeWake().
            nativePollOnce((int) Math.min(timeoutMillis, Integer.MAX_VALUE));
            if (sPlatformDispatcher != null) sPlatformDispatcher.run();
        } else {
            synchronized (this) {
                if (mMessages != null) {
                    long delay = mMessages.when - SystemClock.uptimeMillis();
                    if (delay <= 0 && mMessages.target != null) return;
                    if (timeoutMillis < 0 || delay < timeoutMillis) timeoutMillis = Math.max(delay, 1);
                }
                if (mQuitting) return;
                try {
                    if (timeoutMillis < 0) wait();
                    else if (timeoutMillis > 0) wait(timeoutMillis);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private void wake() {
        if (mIsMain) nativeWake();
        else notifyAll();
    }

    void quit(boolean safe) {
        if (!mQuitAllowed) throw new IllegalStateException("Main thread not allowed to quit.");
        synchronized (this) {
            if (mQuitting) return;
            mQuitting = true;
            if (safe) removeAllFutureMessagesLocked();
            else removeAllMessagesLocked();
            wake();
        }
    }

    public int postSyncBarrier() { return postSyncBarrier(SystemClock.uptimeMillis()); }

    private int postSyncBarrier(long when) {
        synchronized (this) {
            final int token = mNextBarrierToken++;
            final Message msg = Message.obtain();
            msg.markInUse();
            msg.when = when;
            msg.arg1 = token;
            Message prev = null;
            Message p = mMessages;
            if (when != 0) {
                while (p != null && p.when <= when) {
                    prev = p;
                    p = p.next;
                }
            }
            if (prev != null) {
                msg.next = p;
                prev.next = msg;
            } else {
                msg.next = p;
                mMessages = msg;
            }
            return token;
        }
    }

    public void removeSyncBarrier(int token) {
        synchronized (this) {
            Message prev = null;
            Message p = mMessages;
            while (p != null && (p.target != null || p.arg1 != token)) {
                prev = p;
                p = p.next;
            }
            if (p == null) return;
            if (prev != null) prev.next = p.next;
            else mMessages = p.next;
            p.recycleUnchecked();
            wake();
        }
    }

    boolean enqueueMessage(Message msg, long when) {
        if (msg.target == null) throw new IllegalArgumentException("Message must have a target.");
        synchronized (this) {
            if (msg.isInUse()) throw new IllegalStateException(msg + " This message is already in use.");
            if (mQuitting) {
                msg.recycle();
                return false;
            }
            msg.markInUse();
            msg.when = when;
            Message p = mMessages;
            if (p == null || when == 0 || when < p.when) {
                msg.next = p;
                mMessages = msg;
            } else {
                Message prev;
                for (;;) {
                    prev = p;
                    p = p.next;
                    if (p == null || when < p.when) break;
                }
                msg.next = p;
                prev.next = msg;
            }
            wake();
        }
        return true;
    }

    boolean hasMessages(Handler h, int what, Object object) {
        if (h == null) return false;
        synchronized (this) {
            for (Message p = mMessages; p != null; p = p.next)
                if (p.target == h && p.what == what && (object == null || p.obj == object)) return true;
            return false;
        }
    }

    boolean hasMessages(Handler h, Runnable r, Object object) {
        if (h == null) return false;
        synchronized (this) {
            for (Message p = mMessages; p != null; p = p.next)
                if (p.target == h && p.callback == r && (object == null || p.obj == object)) return true;
            return false;
        }
    }

    boolean hasMessages(Handler h) {
        if (h == null) return false;
        synchronized (this) {
            for (Message p = mMessages; p != null; p = p.next) if (p.target == h) return true;
            return false;
        }
    }

    private interface Matcher {
        boolean match(Message m);
    }

    private void removeMatching(Matcher m) {
        synchronized (this) {
            Message prev = null;
            Message p = mMessages;
            while (p != null) {
                Message n = p.next;
                if (m.match(p)) {
                    if (prev == null) mMessages = n;
                    else prev.next = n;
                    p.recycleUnchecked();
                } else {
                    prev = p;
                }
                p = n;
            }
        }
    }

    void removeMessages(final Handler h, final int what, final Object object) {
        if (h == null) return;
        removeMatching(new Matcher() {
            public boolean match(Message p) { return p.target == h && p.what == what && (object == null || p.obj == object); }
        });
    }

    void removeMessages(final Handler h, final Runnable r, final Object object) {
        if (h == null || r == null) return;
        removeMatching(new Matcher() {
            public boolean match(Message p) { return p.target == h && p.callback == r && (object == null || p.obj == object); }
        });
    }

    void removeCallbacksAndMessages(final Handler h, final Object object) {
        if (h == null) return;
        removeMatching(new Matcher() {
            public boolean match(Message p) { return p.target == h && (object == null || p.obj == object); }
        });
    }

    private void removeAllMessagesLocked() {
        Message p = mMessages;
        while (p != null) {
            Message n = p.next;
            p.recycleUnchecked();
            p = n;
        }
        mMessages = null;
    }

    private void removeAllFutureMessagesLocked() {
        final long now = SystemClock.uptimeMillis();
        Message p = mMessages;
        if (p != null) {
            if (p.when > now) {
                removeAllMessagesLocked();
            } else {
                Message n;
                for (;;) {
                    n = p.next;
                    if (n == null) return;
                    if (n.when > now) break;
                    p = n;
                }
                p.next = null;
                do {
                    p = n;
                    n = p.next;
                    p.recycleUnchecked();
                } while (n != null);
            }
        }
    }

    public interface IdleHandler {
        boolean queueIdle();
    }

    public interface OnFileDescriptorEventListener {
        int EVENT_INPUT = 1;
        int EVENT_OUTPUT = 2;
        int EVENT_ERROR = 4;
        int onFileDescriptorEvents(java.io.FileDescriptor fd, int events);
    }

    private static native void nativePollOnce(int timeoutMillis);
    private static native void nativeWake();
}
