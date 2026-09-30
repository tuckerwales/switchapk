package android.os;

public final class CancellationSignal {
    private boolean mIsCanceled;
    private OnCancelListener mOnCancelListener;

    public boolean isCanceled() { synchronized (this) { return mIsCanceled; } }

    public void throwIfCanceled() {
        if (isCanceled()) throw new OperationCanceledException();
    }

    public void cancel() {
        OnCancelListener l;
        synchronized (this) {
            if (mIsCanceled) return;
            mIsCanceled = true;
            l = mOnCancelListener;
        }
        if (l != null) l.onCancel();
    }

    public void setOnCancelListener(OnCancelListener listener) {
        synchronized (this) {
            if (mOnCancelListener == listener) return;
            mOnCancelListener = listener;
            if (!mIsCanceled || listener == null) return;
        }
        listener.onCancel();
    }

    public interface OnCancelListener {
        void onCancel();
    }
}
