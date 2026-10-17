package android.webkit;

public class JsResult {
    /** Hidden on Android: notified when the app answers the dialog. */
    interface ResultReceiver {
        void onJsResultComplete(JsResult result);
    }

    private final ResultReceiver mReceiver;
    private boolean mResult;

    /** Hidden on Android; package-private here. */
    JsResult(ResultReceiver receiver) {
        mReceiver = receiver;
    }

    public final void cancel() {
        mResult = false;
        wakeUp();
    }

    public final void confirm() {
        mResult = true;
        wakeUp();
    }

    /** Hidden on Android: true when confirmed. */
    final boolean getResult() {
        return mResult;
    }

    private void wakeUp() {
        if (mReceiver != null) mReceiver.onJsResultComplete(this);
    }
}
