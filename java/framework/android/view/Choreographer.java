package android.view;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;

/** Posts frame callbacks onto the thread's looper. There is no vsync yet. TODO(WS1) */
public final class Choreographer {
    public interface FrameCallback {
        void doFrame(long frameTimeNanos);
    }

    private static final ThreadLocal<Choreographer> sInstance = new ThreadLocal<Choreographer>() {
        @Override
        protected Choreographer initialValue() {
            Looper looper = Looper.myLooper();
            if (looper == null) throw new IllegalStateException("The current thread must have a looper!");
            return new Choreographer(looper);
        }
    };

    private final Handler mHandler;
    private final ArrayList<FrameCallback> mPending = new ArrayList<FrameCallback>();

    private Choreographer(Looper looper) { mHandler = new Handler(looper); }

    public static Choreographer getInstance() { return sInstance.get(); }

    public void postFrameCallback(FrameCallback callback) { postFrameCallbackDelayed(callback, 0); }

    public void postFrameCallbackDelayed(final FrameCallback callback, long delayMillis) {
        if (callback == null) return;
        mPending.add(callback);
        mHandler.postDelayed(new Runnable() {
            public void run() {
                if (mPending.remove(callback)) callback.doFrame(SystemClock.uptimeNanos());
            }
        }, delayMillis);
    }

    public void removeFrameCallback(FrameCallback callback) {
        while (mPending.remove(callback)) {}
    }
}
