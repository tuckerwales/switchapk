package android.content;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

/**
 * Follows the AOSP design: the result of the broadcast being delivered lives in a
 * PendingResult that the framework sets before onReceive and finishes after it,
 * unless the receiver took it with goAsync.
 */
public abstract class BroadcastReceiver {
    private PendingResult mPendingResult;
    private boolean mDebugUnregister;

    public BroadcastReceiver() {}

    public abstract void onReceive(Context context, Intent intent);

    public final PendingResult goAsync() {
        PendingResult res = mPendingResult;
        mPendingResult = null;
        return res;
    }

    public IBinder peekService(Context myContext, Intent service) {
        return android.app.ActivityThread.peekService(service);
    }

    public final void setResultCode(int code) {
        checkSynchronousHint();
        mPendingResult.mResultCode = code;
    }

    public final int getResultCode() { return mPendingResult != null ? mPendingResult.mResultCode : 0; }

    public final void setResultData(String data) {
        checkSynchronousHint();
        mPendingResult.mResultData = data;
    }

    public final String getResultData() { return mPendingResult != null ? mPendingResult.mResultData : null; }

    public final void setResultExtras(Bundle extras) {
        checkSynchronousHint();
        mPendingResult.mResultExtras = extras;
    }

    public final Bundle getResultExtras(boolean makeMap) {
        if (mPendingResult == null) return null;
        Bundle e = mPendingResult.mResultExtras;
        if (!makeMap) return e;
        if (e == null) mPendingResult.mResultExtras = e = new Bundle();
        return e;
    }

    public final void setResult(int code, String data, Bundle extras) {
        checkSynchronousHint();
        mPendingResult.mResultCode = code;
        mPendingResult.mResultData = data;
        mPendingResult.mResultExtras = extras;
    }

    public final boolean getAbortBroadcast() { return mPendingResult != null && mPendingResult.mAbortBroadcast; }

    public final void abortBroadcast() {
        checkSynchronousHint();
        mPendingResult.mAbortBroadcast = true;
    }

    public final void clearAbortBroadcast() {
        if (mPendingResult != null) mPendingResult.mAbortBroadcast = false;
    }

    public final boolean isOrderedBroadcast() { return mPendingResult != null && mPendingResult.mOrderedHint; }

    public final boolean isInitialStickyBroadcast() { return mPendingResult != null && mPendingResult.mInitialStickyHint; }

    public final void setOrderedHint(boolean isOrdered) {}

    public int getSentFromUid() { return Process.myUid(); }

    public String getSentFromPackage() { return null; }

    public final void setDebugUnregister(boolean debug) { mDebugUnregister = debug; }

    public final boolean getDebugUnregister() { return mDebugUnregister; }

    /** framework-internal. */
    public final void setPendingResult(PendingResult result) { mPendingResult = result; }

    /** framework-internal. */
    public final PendingResult getPendingResult() { return mPendingResult; }

    private void checkSynchronousHint() {
        if (mPendingResult == null) throw new IllegalStateException("Call while result is not pending");
        if (mPendingResult.mOrderedHint || mPendingResult.mInitialStickyHint) return;
        RuntimeException e = new RuntimeException("BroadcastReceiver trying to return result during a non-ordered broadcast");
        Log.e("BroadcastReceiver", e.getMessage(), e);
    }

    public static class PendingResult {
        int mResultCode;
        String mResultData;
        Bundle mResultExtras;
        boolean mAbortBroadcast;
        final boolean mOrderedHint;
        final boolean mInitialStickyHint;
        private final Runnable mOnFinish;
        private boolean mFinished;

        /** framework-internal. onFinish runs once, from finish(), on any thread. */
        public PendingResult(int resultCode, String resultData, Bundle resultExtras, boolean ordered, boolean sticky,
                Runnable onFinish) {
            mResultCode = resultCode;
            mResultData = resultData;
            mResultExtras = resultExtras;
            mOrderedHint = ordered;
            mInitialStickyHint = sticky;
            mOnFinish = onFinish;
        }

        public final void setResultCode(int code) {
            checkSynchronousHint();
            mResultCode = code;
        }

        public final int getResultCode() { return mResultCode; }

        public final void setResultData(String data) {
            checkSynchronousHint();
            mResultData = data;
        }

        public final String getResultData() { return mResultData; }

        public final void setResultExtras(Bundle extras) {
            checkSynchronousHint();
            mResultExtras = extras;
        }

        public final Bundle getResultExtras(boolean makeMap) {
            Bundle e = mResultExtras;
            if (!makeMap) return e;
            if (e == null) mResultExtras = e = new Bundle();
            return e;
        }

        public final void setResult(int code, String data, Bundle extras) {
            checkSynchronousHint();
            mResultCode = code;
            mResultData = data;
            mResultExtras = extras;
        }

        public final boolean getAbortBroadcast() { return mAbortBroadcast; }

        public final void abortBroadcast() {
            checkSynchronousHint();
            mAbortBroadcast = true;
        }

        public final void clearAbortBroadcast() { mAbortBroadcast = false; }

        public final void finish() {
            synchronized (this) {
                if (mFinished) throw new IllegalStateException("Broadcast already finished");
                mFinished = true;
            }
            if (mOnFinish != null) mOnFinish.run();
        }

        private void checkSynchronousHint() {
            if (mOrderedHint || mInitialStickyHint) return;
            RuntimeException e = new RuntimeException("BroadcastReceiver trying to return result during a non-ordered broadcast");
            Log.e("BroadcastReceiver", e.getMessage(), e);
        }
    }
}
