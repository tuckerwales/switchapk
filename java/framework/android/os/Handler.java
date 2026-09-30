package android.os;

import android.util.Printer;

public class Handler {
    public interface Callback {
        boolean handleMessage(Message msg);
    }

    final Looper mLooper;
    final MessageQueue mQueue;
    final Callback mCallback;
    final boolean mAsynchronous;

    public void handleMessage(Message msg) {}

    public void dispatchMessage(Message msg) {
        if (msg.callback != null) {
            msg.callback.run();
        } else {
            if (mCallback != null && mCallback.handleMessage(msg)) return;
            handleMessage(msg);
        }
    }

    @Deprecated
    public Handler() { this(null, false); }

    @Deprecated
    public Handler(Callback callback) { this(callback, false); }

    public Handler(Looper looper) { this(looper, null, false); }
    public Handler(Looper looper, Callback callback) { this(looper, callback, false); }

    public Handler(boolean async) { this(null, async); }

    public Handler(Callback callback, boolean async) {
        mLooper = Looper.myLooper();
        if (mLooper == null)
            throw new RuntimeException("Can't create handler inside thread " + Thread.currentThread() + " that has not called Looper.prepare()");
        mQueue = mLooper.mQueue;
        mCallback = callback;
        mAsynchronous = async;
    }

    public Handler(Looper looper, Callback callback, boolean async) {
        if (looper == null) throw new NullPointerException("looper");
        mLooper = looper;
        mQueue = looper.mQueue;
        mCallback = callback;
        mAsynchronous = async;
    }

    public static Handler createAsync(Looper looper) { return new Handler(looper, null, true); }
    public static Handler createAsync(Looper looper, Callback callback) { return new Handler(looper, callback, true); }

    public String getMessageName(Message message) {
        if (message.callback != null) return message.callback.getClass().getName();
        return "0x" + Integer.toHexString(message.what);
    }

    public final Message obtainMessage() { return Message.obtain(this); }
    public final Message obtainMessage(int what) { return Message.obtain(this, what); }
    public final Message obtainMessage(int what, Object obj) { return Message.obtain(this, what, obj); }
    public final Message obtainMessage(int what, int arg1, int arg2) { return Message.obtain(this, what, arg1, arg2); }
    public final Message obtainMessage(int what, int arg1, int arg2, Object obj) { return Message.obtain(this, what, arg1, arg2, obj); }

    public final boolean post(Runnable r) { return sendMessageDelayed(getPostMessage(r), 0); }
    public final boolean postAtTime(Runnable r, long uptimeMillis) { return sendMessageAtTime(getPostMessage(r), uptimeMillis); }
    public final boolean postAtTime(Runnable r, Object token, long uptimeMillis) { return sendMessageAtTime(getPostMessage(r, token), uptimeMillis); }
    public final boolean postDelayed(Runnable r, long delayMillis) { return sendMessageDelayed(getPostMessage(r), delayMillis); }
    public final boolean postDelayed(Runnable r, Object token, long delayMillis) { return sendMessageDelayed(getPostMessage(r, token), delayMillis); }
    public final boolean postAtFrontOfQueue(Runnable r) { return sendMessageAtFrontOfQueue(getPostMessage(r)); }

    public final boolean runWithScissors(final Runnable r, long timeout) {
        if (Looper.myLooper() == mLooper) {
            r.run();
            return true;
        }
        final boolean[] done = new boolean[1];
        post(new Runnable() {
            public void run() {
                try {
                    r.run();
                } finally {
                    synchronized (done) {
                        done[0] = true;
                        done.notifyAll();
                    }
                }
            }
        });
        synchronized (done) {
            long end = timeout > 0 ? SystemClock.uptimeMillis() + timeout : 0;
            while (!done[0]) {
                try {
                    if (timeout > 0) {
                        long d = end - SystemClock.uptimeMillis();
                        if (d <= 0) return false;
                        done.wait(d);
                    } else {
                        done.wait();
                    }
                } catch (InterruptedException ignored) {}
            }
        }
        return true;
    }

    public final void removeCallbacks(Runnable r) { mQueue.removeMessages(this, r, null); }
    public final void removeCallbacks(Runnable r, Object token) { mQueue.removeMessages(this, r, token); }

    public final boolean sendMessage(Message msg) { return sendMessageDelayed(msg, 0); }
    public final boolean sendEmptyMessage(int what) { return sendEmptyMessageDelayed(what, 0); }

    public final boolean sendEmptyMessageDelayed(int what, long delayMillis) {
        Message msg = Message.obtain();
        msg.what = what;
        return sendMessageDelayed(msg, delayMillis);
    }

    public final boolean sendEmptyMessageAtTime(int what, long uptimeMillis) {
        Message msg = Message.obtain();
        msg.what = what;
        return sendMessageAtTime(msg, uptimeMillis);
    }

    public final boolean sendMessageDelayed(Message msg, long delayMillis) {
        if (delayMillis < 0) delayMillis = 0;
        return sendMessageAtTime(msg, SystemClock.uptimeMillis() + delayMillis);
    }

    public boolean sendMessageAtTime(Message msg, long uptimeMillis) { return enqueueMessage(mQueue, msg, uptimeMillis); }

    public final boolean sendMessageAtFrontOfQueue(Message msg) { return enqueueMessage(mQueue, msg, 0); }

    public final boolean executeOrSendMessage(Message msg) {
        if (mLooper == Looper.myLooper()) {
            dispatchMessage(msg);
            return true;
        }
        return sendMessage(msg);
    }

    private boolean enqueueMessage(MessageQueue queue, Message msg, long uptimeMillis) {
        msg.target = this;
        if (mAsynchronous) msg.setAsynchronous(true);
        return queue.enqueueMessage(msg, uptimeMillis);
    }

    public final void removeMessages(int what) { mQueue.removeMessages(this, what, null); }
    public final void removeMessages(int what, Object object) { mQueue.removeMessages(this, what, object); }
    public final void removeCallbacksAndMessages(Object token) { mQueue.removeCallbacksAndMessages(this, token); }
    public final boolean hasMessages(int what) { return mQueue.hasMessages(this, what, null); }
    public final boolean hasMessages(int what, Object object) { return mQueue.hasMessages(this, what, object); }
    public final boolean hasCallbacks(Runnable r) { return mQueue.hasMessages(this, r, null); }
    public final boolean hasMessagesOrCallbacks() { return mQueue.hasMessages(this); }
    public final Looper getLooper() { return mLooper; }

    public final void dump(Printer pw, String prefix) { pw.println(prefix + this); }

    @Override
    public String toString() { return "Handler (" + getClass().getName() + ") {" + Integer.toHexString(System.identityHashCode(this)) + "}"; }

    private static Message getPostMessage(Runnable r) {
        Message m = Message.obtain();
        m.callback = r;
        return m;
    }

    private static Message getPostMessage(Runnable r, Object token) {
        Message m = Message.obtain();
        m.obj = token;
        m.callback = r;
        return m;
    }
}
