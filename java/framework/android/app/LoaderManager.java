package android.app;

import android.content.Loader;
import android.os.Bundle;
import android.util.SparseArray;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Manages Loaders for an activity or fragment (AOSP API). */
@Deprecated
public abstract class LoaderManager {
    @Deprecated
    public interface LoaderCallbacks<D> {
        Loader<D> onCreateLoader(int id, Bundle args);

        void onLoadFinished(Loader<D> loader, D data);

        void onLoaderReset(Loader<D> loader);
    }

    public LoaderManager() {}

    public abstract <D> Loader<D> initLoader(int id, Bundle args, LoaderCallbacks<D> callback);

    public abstract <D> Loader<D> restartLoader(int id, Bundle args, LoaderCallbacks<D> callback);

    public abstract void destroyLoader(int id);

    public abstract <D> Loader<D> getLoader(int id);

    public abstract void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args);

    public static void enableDebugLogging(boolean enabled) { LoaderManagerImpl.DEBUG = enabled; }

    /** Hidden AOSP API. */
    public FragmentHostCallback getFragmentHostCallback() { return null; }
}

/** Simplified port of AOSP LoaderManagerImpl (no pending-loader hand-off on restart). */
class LoaderManagerImpl extends LoaderManager {
    static boolean DEBUG = false;

    final SparseArray<LoaderInfo> mLoaders = new SparseArray<LoaderInfo>(0);
    final String mWho;
    boolean mStarted;
    boolean mRetaining;
    boolean mRetainingStarted;
    boolean mCreatingLoader;
    private FragmentHostCallback mHost;

    final class LoaderInfo implements Loader.OnLoadCompleteListener<Object>, Loader.OnLoadCanceledListener<Object> {
        final int mId;
        final Bundle mArgs;
        LoaderManager.LoaderCallbacks<Object> mCallbacks;
        Loader<Object> mLoader;
        boolean mHaveData;
        boolean mDeliveredData;
        Object mData;
        boolean mStarted;
        boolean mRetaining;
        boolean mRetainingStarted;
        boolean mReportNextStart;
        boolean mDestroyed;
        boolean mListenerRegistered;

        LoaderInfo(int id, Bundle args, LoaderManager.LoaderCallbacks<Object> callbacks) {
            mId = id;
            mArgs = args;
            mCallbacks = callbacks;
        }

        void start() {
            if (mRetaining && mRetainingStarted) {
                mStarted = true;
                return;
            }
            if (mStarted) return;
            mStarted = true;
            if (mLoader == null && mCallbacks != null) mLoader = mCallbacks.onCreateLoader(mId, mArgs);
            if (mLoader != null) {
                if (mLoader.getClass().isMemberClass() && !java.lang.reflect.Modifier.isStatic(mLoader.getClass().getModifiers())) {
                    throw new IllegalArgumentException("Object returned from onCreateLoader must not be a non-static inner member class: " + mLoader);
                }
                if (!mListenerRegistered) {
                    mLoader.registerListener(mId, this);
                    mLoader.registerOnLoadCanceledListener(this);
                    mListenerRegistered = true;
                }
                mLoader.startLoading();
            }
        }

        void retain() {
            mRetaining = true;
            mRetainingStarted = mStarted;
            mStarted = false;
            mCallbacks = null;
        }

        void finishRetain() {
            if (mRetaining) {
                mRetaining = false;
                if (mStarted != mRetainingStarted) {
                    if (!mStarted) stop();
                }
            }
            if (mStarted && mHaveData && !mReportNextStart) callOnLoadFinished(mLoader, mData);
        }

        void reportStart() {
            if (mStarted) {
                if (mReportNextStart) {
                    mReportNextStart = false;
                    if (mHaveData && !mRetaining) callOnLoadFinished(mLoader, mData);
                }
            }
        }

        void stop() {
            mStarted = false;
            if (!mRetaining) {
                if (mLoader != null && mListenerRegistered) {
                    mListenerRegistered = false;
                    mLoader.unregisterListener(this);
                    mLoader.unregisterOnLoadCanceledListener(this);
                    mLoader.stopLoading();
                }
            }
        }

        void destroy() {
            mDestroyed = true;
            boolean needReset = mDeliveredData;
            mDeliveredData = false;
            if (mCallbacks != null && mLoader != null && mHaveData && needReset) mCallbacks.onLoaderReset(mLoader);
            mCallbacks = null;
            mData = null;
            mHaveData = false;
            if (mLoader != null) {
                if (mListenerRegistered) {
                    mListenerRegistered = false;
                    mLoader.unregisterListener(this);
                    mLoader.unregisterOnLoadCanceledListener(this);
                }
                mLoader.reset();
            }
        }

        public void onLoadCanceled(Loader<Object> loader) {}

        public void onLoadComplete(Loader<Object> loader, Object data) {
            if (mDestroyed) return;
            if (mLoaders.get(mId) != this) return;
            if (mData != data || !mHaveData) {
                mData = data;
                mHaveData = true;
                if (mStarted) callOnLoadFinished(loader, data);
            }
        }

        void callOnLoadFinished(Loader<Object> loader, Object data) {
            if (mCallbacks != null) {
                mCallbacks.onLoadFinished(loader, data);
                mDeliveredData = true;
            }
        }
    }

    LoaderManagerImpl(String who, FragmentHostCallback host, boolean started) {
        mWho = who;
        mHost = host;
        mStarted = started;
    }

    void updateHostController(FragmentHostCallback host) { mHost = host; }

    @Override
    public FragmentHostCallback getFragmentHostCallback() { return mHost; }

    @SuppressWarnings("unchecked")
    private LoaderInfo createAndInstallLoader(int id, Bundle args, LoaderManager.LoaderCallbacks<?> callback) {
        try {
            mCreatingLoader = true;
            LoaderInfo info = new LoaderInfo(id, args, (LoaderManager.LoaderCallbacks<Object>) callback);
            info.mLoader = (Loader<Object>) callback.onCreateLoader(id, args);
            mLoaders.put(info.mId, info);
            if (mStarted) info.start();
            return info;
        } finally {
            mCreatingLoader = false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <D> Loader<D> initLoader(int id, Bundle args, LoaderManager.LoaderCallbacks<D> callback) {
        if (mCreatingLoader) throw new IllegalStateException("Called while creating a loader");
        LoaderInfo info = mLoaders.get(id);
        if (info == null) {
            info = createAndInstallLoader(id, args, callback);
        } else {
            info.mCallbacks = (LoaderManager.LoaderCallbacks<Object>) callback;
        }
        if (info.mHaveData && mStarted) info.callOnLoadFinished(info.mLoader, info.mData);
        return (Loader<D>) info.mLoader;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <D> Loader<D> restartLoader(int id, Bundle args, LoaderManager.LoaderCallbacks<D> callback) {
        if (mCreatingLoader) throw new IllegalStateException("Called while creating a loader");
        LoaderInfo info = mLoaders.get(id);
        if (info != null) {
            mLoaders.remove(id);
            info.destroy();
        }
        info = createAndInstallLoader(id, args, callback);
        return (Loader<D>) info.mLoader;
    }

    @Override
    public void destroyLoader(int id) {
        if (mCreatingLoader) throw new IllegalStateException("Called while creating a loader");
        int idx = mLoaders.indexOfKey(id);
        if (idx >= 0) {
            LoaderInfo info = mLoaders.valueAt(idx);
            mLoaders.removeAt(idx);
            info.destroy();
        }
        if (mHost != null && !hasRunningLoaders()) mHost.mFragmentManager.startPendingDeferredFragments();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <D> Loader<D> getLoader(int id) {
        if (mCreatingLoader) throw new IllegalStateException("Called while creating a loader");
        LoaderInfo loaderInfo = mLoaders.get(id);
        return loaderInfo != null ? (Loader<D>) loaderInfo.mLoader : null;
    }

    void doStart() {
        if (mStarted) return;
        mStarted = true;
        for (int i = mLoaders.size() - 1; i >= 0; i--) mLoaders.valueAt(i).start();
    }

    void doStop() {
        if (!mStarted) return;
        for (int i = mLoaders.size() - 1; i >= 0; i--) mLoaders.valueAt(i).stop();
        mStarted = false;
    }

    void doRetain() {
        if (!mStarted) return;
        mRetaining = true;
        mStarted = false;
        for (int i = mLoaders.size() - 1; i >= 0; i--) mLoaders.valueAt(i).retain();
    }

    void finishRetain() {
        if (mRetaining) {
            mRetaining = false;
            for (int i = mLoaders.size() - 1; i >= 0; i--) mLoaders.valueAt(i).finishRetain();
        }
    }

    void doReportNextStart() {
        for (int i = mLoaders.size() - 1; i >= 0; i--) mLoaders.valueAt(i).mReportNextStart = true;
    }

    void doReportStart() {
        for (int i = mLoaders.size() - 1; i >= 0; i--) mLoaders.valueAt(i).reportStart();
    }

    void doDestroy() {
        if (!mRetaining) {
            for (int i = mLoaders.size() - 1; i >= 0; i--) mLoaders.valueAt(i).destroy();
            mLoaders.clear();
        }
    }

    boolean hasRunningLoaders() {
        boolean loadersRunning = false;
        final int count = mLoaders.size();
        for (int i = 0; i < count; i++) {
            final LoaderInfo li = mLoaders.valueAt(i);
            loadersRunning |= li.mStarted && !li.mDeliveredData;
        }
        return loadersRunning;
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.print(prefix);
        writer.print("Loaders: ");
        writer.println(mLoaders.size());
    }
}
