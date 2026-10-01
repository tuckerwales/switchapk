package android.view;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

/**
 * Frame scheduler (AOSP Choreographer): callbacks are queued by type (input,
 * animation, insets animation, traversal, commit) and run in that order once
 * per frame. Frames are paced at the display refresh rate; on the Switch the
 * present after the commit phase also waits for vsync.
 */
public final class Choreographer {
    /** framework-internal (hidden in AOSP). */
    public static final int CALLBACK_INPUT = 0;
    /** framework-internal (hidden in AOSP). */
    public static final int CALLBACK_ANIMATION = 1;
    /** framework-internal (hidden in AOSP). */
    public static final int CALLBACK_INSETS_ANIMATION = 2;
    /** framework-internal (hidden in AOSP). */
    public static final int CALLBACK_TRAVERSAL = 3;
    /** framework-internal (hidden in AOSP). */
    public static final int CALLBACK_COMMIT = 4;
    private static final int CALLBACK_LAST = CALLBACK_COMMIT;

    private static final Object FRAME_CALLBACK_TOKEN = new Object();
    private static final int MSG_DO_FRAME = 0;
    private static final int MSG_DO_SCHEDULE_CALLBACK = 2;

    public interface FrameCallback {
        void doFrame(long frameTimeNanos);
    }

    private static final ThreadLocal<Choreographer> sThreadInstance = new ThreadLocal<Choreographer>() {
        @Override
        protected Choreographer initialValue() {
            Looper looper = Looper.myLooper();
            if (looper == null) throw new IllegalStateException("The current thread must have a looper!");
            return new Choreographer(looper);
        }
    };

    private final Looper mLooper;
    private final FrameHandler mHandler;
    private final CallbackQueue[] mCallbackQueues;
    private boolean mFrameScheduled;
    private boolean mCallbacksRunning;
    private long mLastFrameTimeNanos;
    private final long mFrameIntervalNanos;

    private Choreographer(Looper looper) {
        mLooper = looper;
        mHandler = new FrameHandler(looper);
        mCallbackQueues = new CallbackQueue[CALLBACK_LAST + 1];
        for (int i = 0; i <= CALLBACK_LAST; i++) mCallbackQueues[i] = new CallbackQueue();
        float refresh = Display.defaultDisplay().getRefreshRate();
        if (refresh < 10f || refresh > 240f) refresh = 60f;
        mFrameIntervalNanos = (long) (1000000000 / refresh);
    }

    public static Choreographer getInstance() { return sThreadInstance.get(); }

    /** framework-internal (hidden in AOSP). */
    public static Choreographer getMainThreadInstance() { return getInstance(); }

    /** framework-internal (hidden in AOSP). */
    public static long getFrameDelay() { return 10; }

    /** framework-internal (hidden in AOSP). */
    public static void setFrameDelay(long frameDelay) {}

    /** framework-internal (hidden in AOSP). */
    public static long subtractFrameDelay(long delayMillis) {
        final long frameDelay = 10;
        return delayMillis <= frameDelay ? 0 : delayMillis - frameDelay;
    }

    /** framework-internal (hidden in AOSP). */
    public long getFrameIntervalNanos() { return mFrameIntervalNanos; }

    /** framework-internal (hidden in AOSP). */
    public void postCallback(int callbackType, Runnable action, Object token) {
        postCallbackDelayed(callbackType, action, token, 0);
    }

    /** framework-internal (hidden in AOSP). */
    public void postCallbackDelayed(int callbackType, Runnable action, Object token, long delayMillis) {
        if (action == null) throw new IllegalArgumentException("action must not be null");
        if (callbackType < 0 || callbackType > CALLBACK_LAST) throw new IllegalArgumentException("callbackType is invalid");
        postCallbackDelayedInternal(callbackType, action, token, delayMillis);
    }

    private void postCallbackDelayedInternal(int callbackType, Object action, Object token, long delayMillis) {
        final long now = SystemClock.uptimeMillis();
        final long dueTime = now + delayMillis;
        mCallbackQueues[callbackType].addCallbackLocked(dueTime, action, token);
        if (dueTime <= now) {
            scheduleFrameLocked(now);
        } else {
            Message msg = mHandler.obtainMessage(MSG_DO_SCHEDULE_CALLBACK, action);
            msg.arg1 = callbackType;
            mHandler.sendMessageAtTime(msg, dueTime);
        }
    }

    /** framework-internal (hidden in AOSP). */
    public void removeCallbacks(int callbackType, Runnable action, Object token) {
        if (callbackType < 0 || callbackType > CALLBACK_LAST) throw new IllegalArgumentException("callbackType is invalid");
        mCallbackQueues[callbackType].removeCallbacksLocked(action, token);
        if (action != null && token == null) mHandler.removeMessages(MSG_DO_SCHEDULE_CALLBACK, action);
    }

    public void postFrameCallback(FrameCallback callback) { postFrameCallbackDelayed(callback, 0); }

    public void postFrameCallbackDelayed(FrameCallback callback, long delayMillis) {
        if (callback == null) throw new IllegalArgumentException("callback must not be null");
        postCallbackDelayedInternal(CALLBACK_ANIMATION, callback, FRAME_CALLBACK_TOKEN, delayMillis);
    }

    public void removeFrameCallback(FrameCallback callback) {
        if (callback == null) throw new IllegalArgumentException("callback must not be null");
        mCallbackQueues[CALLBACK_ANIMATION].removeCallbacksLocked(callback, FRAME_CALLBACK_TOKEN);
        mHandler.removeMessages(MSG_DO_SCHEDULE_CALLBACK, callback);
    }

    /** framework-internal (hidden in AOSP). */
    public long getFrameTime() { return getFrameTimeNanos() / 1000000L; }

    /** framework-internal (hidden in AOSP). */
    public long getFrameTimeNanos() {
        return mCallbacksRunning ? mLastFrameTimeNanos : SystemClock.uptimeMillis() * 1000000L;
    }

    /** framework-internal (hidden in AOSP). */
    public long getLastFrameTimeNanos() { return mLastFrameTimeNanos; }

    private void scheduleFrameLocked(long now) {
        if (mFrameScheduled) return;
        mFrameScheduled = true;
        long nextFrameMs = (mLastFrameTimeNanos + mFrameIntervalNanos) / 1000000L;
        Message msg = mHandler.obtainMessage(MSG_DO_FRAME);
        mHandler.sendMessageAtTime(msg, Math.max(now, nextFrameMs));
    }

    void doFrame() {
        if (!mFrameScheduled) return;
        mFrameScheduled = false;
        long frameTimeNanos = SystemClock.uptimeMillis() * 1000000L;
        mLastFrameTimeNanos = frameTimeNanos;
        doCallbacks(CALLBACK_INPUT, frameTimeNanos);
        doCallbacks(CALLBACK_ANIMATION, frameTimeNanos);
        doCallbacks(CALLBACK_INSETS_ANIMATION, frameTimeNanos);
        doCallbacks(CALLBACK_TRAVERSAL, frameTimeNanos);
        doCallbacks(CALLBACK_COMMIT, frameTimeNanos);
    }

    private void doCallbacks(int callbackType, long frameTimeNanos) {
        final long now = SystemClock.uptimeMillis();
        CallbackRecord callbacks = mCallbackQueues[callbackType].extractDueCallbacksLocked(now);
        if (callbacks == null) return;
        mCallbacksRunning = true;
        try {
            for (CallbackRecord c = callbacks; c != null; c = c.next) c.run(frameTimeNanos);
        } finally {
            mCallbacksRunning = false;
        }
    }

    void doScheduleCallback(int callbackType) {
        if (!mFrameScheduled) {
            final long now = SystemClock.uptimeMillis();
            if (mCallbackQueues[callbackType].hasDueCallbacksLocked(now)) scheduleFrameLocked(now);
        }
    }

    private final class FrameHandler extends Handler {
        FrameHandler(Looper looper) { super(looper); }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_DO_FRAME:
                    doFrame();
                    break;
                case MSG_DO_SCHEDULE_CALLBACK:
                    doScheduleCallback(msg.arg1);
                    break;
            }
        }
    }

    private static final class CallbackRecord {
        CallbackRecord next;
        long dueTime;
        Object action;
        Object token;

        void run(long frameTimeNanos) {
            if (token == FRAME_CALLBACK_TOKEN) ((FrameCallback) action).doFrame(frameTimeNanos);
            else ((Runnable) action).run();
        }
    }

    private static final class CallbackQueue {
        private CallbackRecord mHead;

        boolean hasDueCallbacksLocked(long now) { return mHead != null && mHead.dueTime <= now; }

        CallbackRecord extractDueCallbacksLocked(long now) {
            CallbackRecord callbacks = mHead;
            if (callbacks == null || callbacks.dueTime > now) return null;
            CallbackRecord last = callbacks;
            CallbackRecord next = last.next;
            while (next != null) {
                if (next.dueTime > now) {
                    last.next = null;
                    break;
                }
                last = next;
                next = next.next;
            }
            mHead = next;
            return callbacks;
        }

        void addCallbackLocked(long dueTime, Object action, Object token) {
            CallbackRecord callback = new CallbackRecord();
            callback.dueTime = dueTime;
            callback.action = action;
            callback.token = token;
            CallbackRecord entry = mHead;
            if (entry == null) {
                mHead = callback;
                return;
            }
            if (dueTime < entry.dueTime) {
                callback.next = entry;
                mHead = callback;
                return;
            }
            while (entry.next != null) {
                if (dueTime < entry.next.dueTime) {
                    callback.next = entry.next;
                    break;
                }
                entry = entry.next;
            }
            entry.next = callback;
        }

        void removeCallbacksLocked(Object action, Object token) {
            CallbackRecord predecessor = null;
            for (CallbackRecord callback = mHead; callback != null;) {
                final CallbackRecord next = callback.next;
                if ((action == null || callback.action == action) && (token == null || callback.token == token)) {
                    if (predecessor != null) predecessor.next = next;
                    else mHead = next;
                } else {
                    predecessor = callback;
                }
                callback = next;
            }
        }
    }
}
