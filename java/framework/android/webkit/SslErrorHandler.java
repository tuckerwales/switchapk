package android.webkit;

import android.os.Handler;

public class SslErrorHandler extends Handler {
    private final ValueCallback<Boolean> mResult;
    private boolean mDone;

    /** Hidden on Android; package-private here. The callback receives true on proceed, false on cancel. */
    SslErrorHandler(ValueCallback<Boolean> result) {
        super(CallbackUtil.looper());
        mResult = result;
    }

    public void proceed() {
        finish(true);
    }

    public void cancel() {
        finish(false);
    }

    private void finish(boolean proceed) {
        synchronized (this) {
            if (mDone) return;
            mDone = true;
        }
        if (mResult != null) mResult.onReceiveValue(proceed);
    }
}
