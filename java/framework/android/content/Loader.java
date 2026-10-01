package android.content;

import android.database.ContentObserver;
import android.os.Handler;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Asynchronous data loader (port of AOSP android.content.Loader). */
@Deprecated
public class Loader<D> {
    int mId;
    OnLoadCompleteListener<D> mListener;
    OnLoadCanceledListener<D> mOnLoadCanceledListener;
    Context mContext;
    boolean mStarted = false;
    boolean mAbandoned = false;
    boolean mReset = true;
    boolean mContentChanged = false;
    boolean mProcessingChange = false;

    @Deprecated
    public final class ForceLoadContentObserver extends ContentObserver {
        public ForceLoadContentObserver() { super(new Handler()); }

        @Override
        public boolean deliverSelfNotifications() { return true; }

        @Override
        public void onChange(boolean selfChange) { onContentChanged(); }
    }

    @Deprecated
    public interface OnLoadCompleteListener<D> {
        void onLoadComplete(Loader<D> loader, D data);
    }

    @Deprecated
    public interface OnLoadCanceledListener<D> {
        void onLoadCanceled(Loader<D> loader);
    }

    public Loader(Context context) { mContext = context.getApplicationContext(); }

    public void deliverResult(D data) {
        if (mListener != null) mListener.onLoadComplete(this, data);
    }

    public void deliverCancellation() {
        if (mOnLoadCanceledListener != null) mOnLoadCanceledListener.onLoadCanceled(this);
    }

    public Context getContext() { return mContext; }

    public int getId() { return mId; }

    public void registerListener(int id, OnLoadCompleteListener<D> listener) {
        if (mListener != null) throw new IllegalStateException("There is already a listener registered");
        mListener = listener;
        mId = id;
    }

    public void unregisterListener(OnLoadCompleteListener<D> listener) {
        if (mListener == null) throw new IllegalStateException("No listener register");
        if (mListener != listener) throw new IllegalArgumentException("Attempting to unregister the wrong listener");
        mListener = null;
    }

    public void registerOnLoadCanceledListener(OnLoadCanceledListener<D> listener) {
        if (mOnLoadCanceledListener != null) throw new IllegalStateException("There is already a listener registered");
        mOnLoadCanceledListener = listener;
    }

    public void unregisterOnLoadCanceledListener(OnLoadCanceledListener<D> listener) {
        if (mOnLoadCanceledListener == null) throw new IllegalStateException("No listener register");
        if (mOnLoadCanceledListener != listener) {
            throw new IllegalArgumentException("Attempting to unregister the wrong listener");
        }
        mOnLoadCanceledListener = null;
    }

    public boolean isStarted() { return mStarted; }

    public boolean isAbandoned() { return mAbandoned; }

    public boolean isReset() { return mReset; }

    public final void startLoading() {
        mStarted = true;
        mReset = false;
        mAbandoned = false;
        onStartLoading();
    }

    protected void onStartLoading() {}

    public boolean cancelLoad() { return onCancelLoad(); }

    protected boolean onCancelLoad() { return false; }

    public void forceLoad() { onForceLoad(); }

    protected void onForceLoad() {}

    public void stopLoading() {
        mStarted = false;
        onStopLoading();
    }

    protected void onStopLoading() {}

    public void abandon() {
        mAbandoned = true;
        onAbandon();
    }

    protected void onAbandon() {}

    public void reset() {
        onReset();
        mReset = true;
        mStarted = false;
        mAbandoned = false;
        mContentChanged = false;
        mProcessingChange = false;
    }

    protected void onReset() {}

    public boolean takeContentChanged() {
        boolean res = mContentChanged;
        mContentChanged = false;
        mProcessingChange |= res;
        return res;
    }

    public void commitContentChanged() { mProcessingChange = false; }

    public void rollbackContentChanged() {
        if (mProcessingChange) onContentChanged();
    }

    public void onContentChanged() {
        if (mStarted) forceLoad();
        else mContentChanged = true;
    }

    public String dataToString(D data) {
        StringBuilder sb = new StringBuilder(64);
        if (data == null) {
            sb.append("null");
        } else {
            Class<?> cls = data.getClass();
            sb.append(cls.getSimpleName());
            sb.append("{");
            sb.append(Integer.toHexString(System.identityHashCode(data)));
            sb.append("}");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append(getClass().getSimpleName());
        sb.append("{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        sb.append(" id=");
        sb.append(mId);
        sb.append("}");
        return sb.toString();
    }

    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.print(prefix);
        writer.print("mId=");
        writer.print(mId);
        writer.print(" mListener=");
        writer.println(mListener);
        writer.print(prefix);
        writer.print("mStarted=");
        writer.print(mStarted);
        writer.print(" mContentChanged=");
        writer.print(mContentChanged);
        writer.print(" mAbandoned=");
        writer.print(mAbandoned);
        writer.print(" mReset=");
        writer.println(mReset);
    }
}
