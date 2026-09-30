package android.content;

import android.os.Bundle;
import android.os.IBinder;

public abstract class BroadcastReceiver {
    private boolean mOrdered;
    private int mResultCode;
    private String mResultData;
    private Bundle mResultExtras;
    private boolean mAbort;
    private boolean mInitialSticky;

    public BroadcastReceiver() {}

    public abstract void onReceive(Context context, Intent intent);

    public final PendingResult goAsync() { return new PendingResult(); }

    public IBinder peekService(Context myContext, Intent service) { return null; }
    public final void setResultCode(int code) { mResultCode = code; }
    public final int getResultCode() { return mResultCode; }
    public final void setResultData(String data) { mResultData = data; }
    public final String getResultData() { return mResultData; }
    public final void setResultExtras(Bundle extras) { mResultExtras = extras; }
    public final Bundle getResultExtras(boolean makeMap) {
        if (mResultExtras == null && makeMap) mResultExtras = new Bundle();
        return mResultExtras;
    }
    public final void setResult(int code, String data, Bundle extras) {
        mResultCode = code;
        mResultData = data;
        mResultExtras = extras;
    }
    public final boolean getAbortBroadcast() { return mAbort; }
    public final void abortBroadcast() { mAbort = true; }
    public final void clearAbortBroadcast() { mAbort = false; }
    public final boolean isOrderedBroadcast() { return mOrdered; }
    public final boolean isInitialStickyBroadcast() { return mInitialSticky; }
    public final void setOrderedHint(boolean isOrdered) { mOrdered = isOrdered; }
    public final void setDebugUnregister(boolean debug) {}
    public final boolean getDebugUnregister() { return false; }

    public static class PendingResult {
        public final void setResultCode(int code) {}
        public final int getResultCode() { return 0; }
        public final void setResultData(String data) {}
        public final String getResultData() { return null; }
        public final void setResultExtras(Bundle extras) {}
        public final Bundle getResultExtras(boolean makeMap) { return makeMap ? new Bundle() : null; }
        public final void setResult(int code, String data, Bundle extras) {}
        public final boolean getAbortBroadcast() { return false; }
        public final void abortBroadcast() {}
        public final void clearAbortBroadcast() {}
        public final void finish() {}
    }
}
