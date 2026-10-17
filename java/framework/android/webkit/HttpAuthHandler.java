package android.webkit;

import android.os.Handler;

public class HttpAuthHandler extends Handler {
    private final ValueCallback<String[]> mResult;
    private final boolean mUseStored;
    private boolean mDone;

    /**
     * Hidden on Android; package-private here. The callback receives {username, password} on proceed and null
     * on cancel. useStored is what useHttpAuthUsernamePassword() returns.
     */
    HttpAuthHandler(ValueCallback<String[]> result, boolean useStored) {
        super(CallbackUtil.looper());
        mResult = result;
        mUseStored = useStored;
    }

    public boolean useHttpAuthUsernamePassword() {
        return mUseStored;
    }

    public void cancel() {
        finish(null);
    }

    public void proceed(String username, String password) {
        finish(new String[] {username, password});
    }

    private void finish(String[] credentials) {
        synchronized (this) {
            if (mDone) return;
            mDone = true;
        }
        if (mResult != null) mResult.onReceiveValue(credentials);
    }
}
