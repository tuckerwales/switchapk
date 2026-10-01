package android.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.Handler;
import android.util.ArrayMap;
import android.view.LayoutInflater;
import android.view.View;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Integration points between a fragment host (usually an Activity) and its fragments (AOSP FragmentHostCallback). */
@Deprecated
public abstract class FragmentHostCallback<E> extends FragmentContainer {
    private final Activity mActivity;
    final Context mContext;
    private final Handler mHandler;
    final int mWindowAnimations;
    final FragmentManagerImpl mFragmentManager = new FragmentManagerImpl();
    private ArrayMap<String, LoaderManager> mAllLoaderManagers;
    private boolean mRetainLoaders;
    private LoaderManagerImpl mLoaderManager;
    private boolean mCheckedForLoaderManager;
    private boolean mLoadersStarted;

    public FragmentHostCallback(Context context, Handler handler, int windowAnimations) {
        this(context instanceof Activity ? (Activity) context : null, context, chooseHandler(context, handler),
                windowAnimations);
    }

    FragmentHostCallback(Activity activity) { this(activity, activity, activity.mHandler, 0); }

    FragmentHostCallback(Activity activity, Context context, Handler handler, int windowAnimations) {
        mActivity = activity;
        mContext = context;
        mHandler = handler;
        mWindowAnimations = windowAnimations;
    }

    private static Handler chooseHandler(Context context, Handler handler) {
        if (handler == null && context instanceof Activity) return ((Activity) context).mHandler;
        return handler;
    }

    public void onDump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {}

    public boolean onShouldSaveFragmentState(Fragment fragment) { return true; }

    public LayoutInflater onGetLayoutInflater() {
        return (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public boolean onUseFragmentManagerInflaterFactory() { return false; }

    public abstract E onGetHost();

    public void onInvalidateOptionsMenu() {}

    public void onStartActivityFromFragment(Fragment fragment, Intent intent, int requestCode, Bundle options) {
        if (requestCode != -1) {
            throw new IllegalStateException("Starting activity with a requestCode requires a FragmentActivity host");
        }
        mContext.startActivity(intent);
    }

    public void onStartIntentSenderFromFragment(Fragment fragment, IntentSender intent, int requestCode,
            Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options)
            throws IntentSender.SendIntentException {
        if (requestCode != -1) {
            throw new IllegalStateException("Starting intent sender with a requestCode requires a FragmentActivity host");
        }
        throw new IntentSender.SendIntentException("Intent senders are not supported");
    }

    public void onRequestPermissionsFromFragment(Fragment fragment, String[] permissions, int requestCode) {}

    public boolean onHasWindowAnimations() { return true; }

    public int onGetWindowAnimations() { return mWindowAnimations; }

    public void onAttachFragment(Fragment fragment) {}

    @Override
    public <T extends View> T onFindViewById(int id) { return null; }

    @Override
    public boolean onHasView() { return true; }

    boolean getRetainLoaders() { return mRetainLoaders; }

    Activity getActivity() { return mActivity; }

    Context getContext() { return mContext; }

    Handler getHandler() { return mHandler; }

    FragmentManagerImpl getFragmentManagerImpl() { return mFragmentManager; }

    LoaderManagerImpl getLoaderManagerImpl() {
        if (mLoaderManager != null) return mLoaderManager;
        mCheckedForLoaderManager = true;
        mLoaderManager = getLoaderManager("(root)", mLoadersStarted, true);
        return mLoaderManager;
    }

    void inactivateFragment(String who) {
        if (mAllLoaderManagers != null) {
            LoaderManagerImpl lm = (LoaderManagerImpl) mAllLoaderManagers.get(who);
            if (lm != null && !lm.mRetaining) {
                lm.doDestroy();
                mAllLoaderManagers.remove(who);
            }
        }
    }

    void doLoaderStart() {
        if (mLoadersStarted) return;
        mLoadersStarted = true;
        if (mLoaderManager != null) {
            mLoaderManager.doStart();
        } else if (!mCheckedForLoaderManager) {
            mLoaderManager = getLoaderManager("(root)", mLoadersStarted, false);
        }
        mCheckedForLoaderManager = true;
    }

    void doLoaderStop(boolean retain) {
        mRetainLoaders = retain;
        if (mLoaderManager == null) return;
        if (!mLoadersStarted) return;
        mLoadersStarted = false;
        if (retain) mLoaderManager.doRetain();
        else mLoaderManager.doStop();
    }

    void doLoaderRetain() {
        if (mLoaderManager == null) return;
        mLoaderManager.doRetain();
    }

    void doLoaderDestroy() {
        if (mLoaderManager == null) return;
        mLoaderManager.doDestroy();
    }

    void reportLoaderStart() {
        if (mAllLoaderManagers != null) {
            final int N = mAllLoaderManagers.size();
            LoaderManagerImpl[] loaders = new LoaderManagerImpl[N];
            for (int i = N - 1; i >= 0; i--) loaders[i] = (LoaderManagerImpl) mAllLoaderManagers.valueAt(i);
            for (int i = 0; i < N; i++) {
                LoaderManagerImpl lm = loaders[i];
                lm.finishRetain();
                lm.doReportStart();
            }
        }
    }

    LoaderManagerImpl getLoaderManager(String who, boolean started, boolean create) {
        if (mAllLoaderManagers == null) mAllLoaderManagers = new ArrayMap<String, LoaderManager>();
        LoaderManagerImpl lm = (LoaderManagerImpl) mAllLoaderManagers.get(who);
        if (lm == null && create) {
            lm = new LoaderManagerImpl(who, this, started);
            mAllLoaderManagers.put(who, lm);
        } else if (started && lm != null && !lm.mStarted) {
            lm.doStart();
        }
        return lm;
    }

    ArrayMap<String, LoaderManager> retainLoaderNonConfig() {
        boolean retainLoaders = false;
        if (mAllLoaderManagers != null) {
            final int N = mAllLoaderManagers.size();
            LoaderManagerImpl[] loaders = new LoaderManagerImpl[N];
            for (int i = N - 1; i >= 0; i--) loaders[i] = (LoaderManagerImpl) mAllLoaderManagers.valueAt(i);
            final boolean doRetainLoaders = getRetainLoaders();
            for (int i = 0; i < N; i++) {
                LoaderManagerImpl lm = loaders[i];
                if (!lm.mRetaining && doRetainLoaders) {
                    if (!lm.mStarted) lm.doStart();
                    lm.doRetain();
                }
                if (lm.mRetaining) retainLoaders = true;
                else {
                    lm.doDestroy();
                    mAllLoaderManagers.remove(lm.mWho);
                }
            }
        }
        if (retainLoaders) return mAllLoaderManagers;
        return null;
    }

    void restoreLoaderNonConfig(ArrayMap<String, LoaderManager> loaderManagers) {
        if (loaderManagers != null) {
            for (int i = 0, N = loaderManagers.size(); i < N; i++) {
                ((LoaderManagerImpl) loaderManagers.valueAt(i)).updateHostController(this);
            }
        }
        mAllLoaderManagers = loaderManagers;
    }
}
