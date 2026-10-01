package android.app;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.SuperNotCalledException;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

/**
 * Legacy framework fragment (port of AOSP android.app.Fragment without
 * transitions and animators, which need WS5). AndroidX still uses this class:
 * ReportFragment is added to every ComponentActivity.
 */
@Deprecated
public class Fragment implements ComponentCallbacks2, View.OnCreateContextMenuListener {
    private static final HashMap<String, Class<?>> sClassMap = new HashMap<String, Class<?>>();

    static final int INVALID_STATE = -1;
    static final int INITIALIZING = 0;
    static final int CREATED = 1;
    static final int ACTIVITY_CREATED = 2;
    static final int STOPPED = 3;
    static final int STARTED = 4;
    static final int RESUMED = 5;

    int mState = INITIALIZING;
    Bundle mSavedFragmentState;
    SparseArray<Parcelable> mSavedViewState;
    int mIndex = -1;
    String mWho;
    Bundle mArguments;
    Fragment mTarget;
    int mTargetIndex = -1;
    int mTargetRequestCode;
    boolean mAdded;
    boolean mRemoving;
    boolean mFromLayout;
    boolean mInLayout;
    boolean mRestored;
    boolean mPerformedCreateView;
    int mBackStackNesting;
    FragmentManagerImpl mFragmentManager;
    FragmentHostCallback mHost;
    FragmentManagerImpl mChildFragmentManager;
    FragmentManagerNonConfig mChildNonConfig;
    Fragment mParentFragment;
    int mFragmentId;
    int mContainerId;
    String mTag;
    boolean mHidden;
    boolean mDetached;
    boolean mRetainInstance;
    boolean mRetaining;
    boolean mHasMenu;
    boolean mMenuVisible = true;
    boolean mCalled;
    ViewGroup mContainer;
    View mView;
    boolean mDeferStart;
    boolean mUserVisibleHint = true;
    LoaderManagerImpl mLoaderManager;
    boolean mLoadersStarted;
    boolean mCheckedForLoaderManager;
    boolean mIsCreated;
    boolean mHiddenChanged;
    LayoutInflater mLayoutInflater;
    boolean mIsNewlyAdded;
    boolean mHideReplaced;

    boolean isHideReplaced() { return mHideReplaced; }

    void setHideReplaced(boolean replaced) { mHideReplaced = replaced; }

    /** State saved by FragmentManager.saveFragmentInstanceState. */
    @Deprecated
    public static class SavedState implements Parcelable {
        final Bundle mState;

        SavedState(Bundle state) { mState = state; }

        SavedState(Parcel in, ClassLoader loader) {
            mState = in.readBundle();
            if (loader != null && mState != null) mState.setClassLoader(loader);
        }

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel dest, int flags) { dest.writeBundle(mState); }

        public static final Parcelable.ClassLoaderCreator<SavedState> CREATOR = new Parcelable.ClassLoaderCreator<SavedState>() {
            public SavedState createFromParcel(Parcel in) { return new SavedState(in, null); }

            public SavedState createFromParcel(Parcel in, ClassLoader loader) { return new SavedState(in, loader); }

            public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }

    /** Thrown when a fragment cannot be created by reflection. */
    @Deprecated
    public static class InstantiationException extends android.util.AndroidRuntimeException {
        public InstantiationException(String msg, Exception cause) { super(msg, cause); }
    }

    public Fragment() {}

    public static Fragment instantiate(Context context, String fname) { return instantiate(context, fname, null); }

    public static Fragment instantiate(Context context, String fname, Bundle args) {
        try {
            Class<?> clazz = sClassMap.get(fname);
            if (clazz == null) {
                clazz = context.getClassLoader().loadClass(fname);
                if (!Fragment.class.isAssignableFrom(clazz)) {
                    throw new InstantiationException("Trying to instantiate a class " + fname
                            + " that is not a Fragment", new ClassCastException());
                }
                sClassMap.put(fname, clazz);
            }
            Fragment f = (Fragment) clazz.getConstructor().newInstance();
            if (args != null) {
                args.setClassLoader(f.getClass().getClassLoader());
                f.setArguments(args);
            }
            return f;
        } catch (ClassNotFoundException e) {
            throw new InstantiationException("Unable to instantiate fragment " + fname
                    + ": make sure class name exists, is public, and has an empty constructor that is public", e);
        } catch (java.lang.InstantiationException e) {
            throw new InstantiationException("Unable to instantiate fragment " + fname
                    + ": make sure class name exists, is public, and has an empty constructor that is public", e);
        } catch (IllegalAccessException e) {
            throw new InstantiationException("Unable to instantiate fragment " + fname
                    + ": make sure class name exists, is public, and has an empty constructor that is public", e);
        } catch (NoSuchMethodException e) {
            throw new InstantiationException("Unable to instantiate fragment " + fname
                    + ": could not find Fragment constructor", e);
        } catch (InvocationTargetException e) {
            throw new InstantiationException("Unable to instantiate fragment " + fname
                    + ": calling Fragment constructor caused an exception", e);
        }
    }

    final void restoreViewState(Bundle savedInstanceState) {
        if (mSavedViewState != null) {
            mView.restoreHierarchyState(mSavedViewState);
            mSavedViewState = null;
        }
        mCalled = false;
        onViewStateRestored(savedInstanceState);
        if (!mCalled) {
            throw new SuperNotCalledException("Fragment " + this + " did not call through to super.onViewStateRestored()");
        }
    }

    final void setIndex(int index, Fragment parent) {
        mIndex = index;
        if (parent != null) mWho = parent.mWho + ":" + mIndex;
        else mWho = "android:fragment:" + mIndex;
    }

    final boolean isInBackStack() { return mBackStackNesting > 0; }

    @Override
    public final boolean equals(Object o) { return super.equals(o); }

    @Override
    public final int hashCode() { return super.hashCode(); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append(getClass().getSimpleName());
        sb.append("{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        if (mIndex >= 0) {
            sb.append(" #");
            sb.append(mIndex);
        }
        if (mFragmentId != 0) {
            sb.append(" id=0x");
            sb.append(Integer.toHexString(mFragmentId));
        }
        if (mTag != null) {
            sb.append(" ");
            sb.append(mTag);
        }
        sb.append('}');
        return sb.toString();
    }

    public final int getId() { return mFragmentId; }

    public final String getTag() { return mTag; }

    public void setArguments(Bundle args) {
        if (mIndex >= 0 && isStateSaved()) throw new IllegalStateException("Fragment already active");
        mArguments = args;
    }

    public final Bundle getArguments() { return mArguments; }

    public final boolean isStateSaved() {
        if (mFragmentManager == null) return false;
        return mFragmentManager.isStateSaved();
    }

    public void setInitialSavedState(SavedState state) {
        if (mIndex >= 0) throw new IllegalStateException("Fragment already active");
        mSavedFragmentState = state != null && state.mState != null ? state.mState : null;
    }

    public void setTargetFragment(Fragment fragment, int requestCode) {
        mTarget = fragment;
        mTargetRequestCode = requestCode;
    }

    public final Fragment getTargetFragment() { return mTarget; }

    public final int getTargetRequestCode() { return mTargetRequestCode; }

    public Context getContext() { return mHost == null ? null : mHost.getContext(); }

    public final Activity getActivity() { return mHost == null ? null : mHost.getActivity(); }

    public final Object getHost() { return mHost == null ? null : mHost.onGetHost(); }

    public final Resources getResources() {
        if (mHost == null) throw new IllegalStateException("Fragment " + this + " not attached to Activity");
        return mHost.getContext().getResources();
    }

    public final CharSequence getText(int resId) { return getResources().getText(resId); }

    public final String getString(int resId) { return getResources().getString(resId); }

    public final String getString(int resId, Object... formatArgs) { return getResources().getString(resId, formatArgs); }

    public final FragmentManager getFragmentManager() { return mFragmentManager; }

    public final FragmentManager getChildFragmentManager() {
        if (mChildFragmentManager == null) {
            instantiateChildFragmentManager();
            if (mState >= RESUMED) mChildFragmentManager.dispatchResume();
            else if (mState >= STARTED) mChildFragmentManager.dispatchStart();
            else if (mState >= ACTIVITY_CREATED) mChildFragmentManager.dispatchActivityCreated();
            else if (mState >= CREATED) mChildFragmentManager.dispatchCreate();
        }
        return mChildFragmentManager;
    }

    FragmentManager peekChildFragmentManager() { return mChildFragmentManager; }

    public final Fragment getParentFragment() { return mParentFragment; }

    public final boolean isAdded() { return mHost != null && mAdded; }

    public final boolean isDetached() { return mDetached; }

    public final boolean isRemoving() { return mRemoving; }

    public final boolean isInLayout() { return mInLayout; }

    public final boolean isResumed() { return mState >= RESUMED; }

    public final boolean isVisible() {
        return isAdded() && !isHidden() && mView != null && mView.getWindowToken() != null
                && mView.getVisibility() == View.VISIBLE;
    }

    public final boolean isHidden() { return mHidden; }

    public void onHiddenChanged(boolean hidden) {}

    public void setRetainInstance(boolean retain) {
        mRetainInstance = retain;
        if (mFragmentManager != null) {
            if (retain) mFragmentManager.addRetainedFragment(this);
            else mFragmentManager.removeRetainedFragment(this);
        } else {
            mRetainInstanceChangedWhileDetached = true;
        }
    }

    boolean mRetainInstanceChangedWhileDetached;

    public final boolean getRetainInstance() { return mRetainInstance; }

    public void setHasOptionsMenu(boolean hasMenu) {
        if (mHasMenu != hasMenu) {
            mHasMenu = hasMenu;
            if (isAdded() && !isHidden()) mFragmentManager.invalidateOptionsMenu();
        }
    }

    public void setMenuVisibility(boolean menuVisible) {
        if (mMenuVisible != menuVisible) {
            mMenuVisible = menuVisible;
            if (mHasMenu && isAdded() && !isHidden()) mFragmentManager.invalidateOptionsMenu();
        }
    }

    public void setUserVisibleHint(boolean isVisibleToUser) {
        if (!mUserVisibleHint && isVisibleToUser && mState < STARTED && mFragmentManager != null && isAdded()) {
            mFragmentManager.performPendingDeferredStart(this);
        }
        mUserVisibleHint = isVisibleToUser;
        mDeferStart = mState < STARTED && !isVisibleToUser;
    }

    public boolean getUserVisibleHint() { return mUserVisibleHint; }

    public LoaderManager getLoaderManager() {
        if (mLoaderManager != null) return mLoaderManager;
        if (mHost == null) throw new IllegalStateException("Fragment " + this + " not attached to Activity");
        mCheckedForLoaderManager = true;
        mLoaderManager = mHost.getLoaderManager(mWho, mLoadersStarted, true);
        return mLoaderManager;
    }

    public void startActivity(Intent intent) { startActivity(intent, null); }

    public void startActivity(Intent intent, Bundle options) {
        if (mHost == null) throw new IllegalStateException("Fragment " + this + " not attached to Activity");
        mHost.onStartActivityFromFragment(this, intent, -1, options);
    }

    public void startActivityForResult(Intent intent, int requestCode) { startActivityForResult(intent, requestCode, null); }

    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (mHost == null) throw new IllegalStateException("Fragment " + this + " not attached to Activity");
        mHost.onStartActivityFromFragment(this, intent, requestCode, options);
    }

    public void startIntentSenderForResult(IntentSender intent, int requestCode, Intent fillInIntent, int flagsMask,
            int flagsValues, int extraFlags, Bundle options) throws IntentSender.SendIntentException {
        if (mHost == null) throw new IllegalStateException("Fragment " + this + " not attached to Activity");
        mHost.onStartIntentSenderFromFragment(this, intent, requestCode, fillInIntent, flagsMask, flagsValues,
                extraFlags, options);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {}

    public final void requestPermissions(String[] permissions, int requestCode) {
        if (mHost == null) throw new IllegalStateException("Fragment " + this + " not attached to Activity");
        mHost.onRequestPermissionsFromFragment(this, permissions, requestCode);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {}

    public boolean shouldShowRequestPermissionRationale(String permission) { return false; }

    public LayoutInflater onGetLayoutInflater(Bundle savedInstanceState) {
        if (mHost == null) throw new IllegalStateException("onGetLayoutInflater() cannot be executed until the Fragment is attached to the FragmentManager.");
        final LayoutInflater result = mHost.onGetLayoutInflater();
        if (mHost.onUseFragmentManagerInflaterFactory()) {
            getChildFragmentManager();
            result.setPrivateFactory(mChildFragmentManager.getLayoutInflaterFactory());
        }
        return result;
    }

    public final LayoutInflater getLayoutInflater() {
        if (mLayoutInflater == null) return performGetLayoutInflater(null);
        return mLayoutInflater;
    }

    LayoutInflater performGetLayoutInflater(Bundle savedInstanceState) {
        LayoutInflater layoutInflater = onGetLayoutInflater(savedInstanceState);
        mLayoutInflater = layoutInflater;
        return mLayoutInflater;
    }

    @Deprecated
    public void onInflate(AttributeSet attrs, Bundle savedInstanceState) { mCalled = true; }

    public void onInflate(Context context, AttributeSet attrs, Bundle savedInstanceState) {
        onInflate(attrs, savedInstanceState);
        mCalled = true;
        final Activity hostActivity = mHost == null ? null : mHost.getActivity();
        if (hostActivity != null) {
            mCalled = false;
            onInflate(hostActivity, attrs, savedInstanceState);
        }
    }

    @Deprecated
    public void onInflate(Activity activity, AttributeSet attrs, Bundle savedInstanceState) { mCalled = true; }

    public void onAttachFragment(Fragment childFragment) {}

    public void onAttach(Context context) {
        mCalled = true;
        final Activity hostActivity = mHost == null ? null : mHost.getActivity();
        if (hostActivity != null) {
            mCalled = false;
            onAttach(hostActivity);
        }
    }

    @Deprecated
    public void onAttach(Activity activity) { mCalled = true; }

    public void onCreate(Bundle savedInstanceState) {
        mCalled = true;
        restoreChildFragmentState(savedInstanceState, true);
        if (mChildFragmentManager != null && !mChildFragmentManager.isStateAtLeast(Fragment.CREATED)) {
            mChildFragmentManager.dispatchCreate();
        }
    }

    void restoreChildFragmentState(Bundle savedInstanceState, boolean provideNonConfig) {
        if (savedInstanceState != null) {
            Parcelable p = savedInstanceState.getParcelable(Activity.FRAGMENTS_TAG);
            if (p != null) {
                if (mChildFragmentManager == null) instantiateChildFragmentManager();
                mChildFragmentManager.restoreAllState(p, provideNonConfig ? mChildNonConfig : null);
                mChildNonConfig = null;
                mChildFragmentManager.dispatchCreate();
            }
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) { return null; }

    public void onViewCreated(View view, Bundle savedInstanceState) {}

    public View getView() { return mView; }

    public void onActivityCreated(Bundle savedInstanceState) { mCalled = true; }

    public void onViewStateRestored(Bundle savedInstanceState) { mCalled = true; }

    public void onStart() {
        mCalled = true;
        if (!mLoadersStarted) {
            mLoadersStarted = true;
            if (!mCheckedForLoaderManager) {
                mCheckedForLoaderManager = true;
                mLoaderManager = mHost.getLoaderManager(mWho, mLoadersStarted, false);
            } else if (mLoaderManager != null) {
                mLoaderManager.doStart();
            }
        }
    }

    public void onResume() { mCalled = true; }

    public void onSaveInstanceState(Bundle outState) {}

    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        onMultiWindowModeChanged(isInMultiWindowMode);
    }

    @Deprecated
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {}

    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        onPictureInPictureModeChanged(isInPictureInPictureMode);
    }

    @Deprecated
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {}

    public void onConfigurationChanged(Configuration newConfig) { mCalled = true; }

    public void onPause() { mCalled = true; }

    public void onStop() { mCalled = true; }

    public void onLowMemory() { mCalled = true; }

    public void onTrimMemory(int level) { mCalled = true; }

    public void onDestroyView() { mCalled = true; }

    public void onDestroy() {
        mCalled = true;
        if (!mCheckedForLoaderManager) {
            mCheckedForLoaderManager = true;
            mLoaderManager = mHost.getLoaderManager(mWho, mLoadersStarted, false);
        }
        if (mLoaderManager != null) mLoaderManager.doDestroy();
    }

    void initState() {
        mIndex = -1;
        mWho = null;
        mAdded = false;
        mRemoving = false;
        mFromLayout = false;
        mInLayout = false;
        mRestored = false;
        mBackStackNesting = 0;
        mFragmentManager = null;
        mChildFragmentManager = null;
        mHost = null;
        mFragmentId = 0;
        mContainerId = 0;
        mTag = null;
        mHidden = false;
        mDetached = false;
        mRetaining = false;
        mLoaderManager = null;
        mLoadersStarted = false;
        mCheckedForLoaderManager = false;
    }

    public void onDetach() { mCalled = true; }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {}

    public void onPrepareOptionsMenu(Menu menu) {}

    public void onDestroyOptionsMenu() {}

    public boolean onOptionsItemSelected(MenuItem item) { return false; }

    public void onOptionsMenuClosed(Menu menu) {}

    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        getActivity().onCreateContextMenu(menu, v, menuInfo);
    }

    public void registerForContextMenu(View view) { view.setOnCreateContextMenuListener(this); }

    public void unregisterForContextMenu(View view) { view.setOnCreateContextMenuListener(null); }

    public boolean onContextItemSelected(MenuItem item) { return false; }

    public void postponeEnterTransition() {}

    public void startPostponedEnterTransition() {}

    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.print(prefix);
        writer.print("mFragmentId=#");
        writer.print(Integer.toHexString(mFragmentId));
        writer.print(" mContainerId=#");
        writer.print(Integer.toHexString(mContainerId));
        writer.print(" mTag=");
        writer.println(mTag);
        writer.print(prefix);
        writer.print("mState=");
        writer.print(mState);
        writer.print(" mIndex=");
        writer.print(mIndex);
        writer.print(" mWho=");
        writer.print(mWho);
        writer.print(" mBackStackNesting=");
        writer.println(mBackStackNesting);
    }

    Fragment findFragmentByWho(String who) {
        if (who.equals(mWho)) return this;
        if (mChildFragmentManager != null) return mChildFragmentManager.findFragmentByWho(who);
        return null;
    }

    void instantiateChildFragmentManager() {
        mChildFragmentManager = new FragmentManagerImpl();
        mChildFragmentManager.attachController(mHost, new FragmentContainer() {
            @SuppressWarnings("unchecked")
            public <T extends View> T onFindViewById(int id) {
                if (mView == null) throw new IllegalStateException("Fragment does not have a view");
                return (T) mView.findViewById(id);
            }

            public boolean onHasView() { return (mView != null); }
        }, this);
    }

    void performCreate(Bundle savedInstanceState) {
        if (mChildFragmentManager != null) mChildFragmentManager.noteStateNotSaved();
        mState = CREATED;
        mCalled = false;
        onCreate(savedInstanceState);
        mIsCreated = true;
        if (!mCalled) throw new SuperNotCalledException("Fragment " + this + " did not call through to super.onCreate()");
    }

    View performCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (mChildFragmentManager != null) mChildFragmentManager.noteStateNotSaved();
        mPerformedCreateView = true;
        return onCreateView(inflater, container, savedInstanceState);
    }

    void performActivityCreated(Bundle savedInstanceState) {
        if (mChildFragmentManager != null) mChildFragmentManager.noteStateNotSaved();
        mState = ACTIVITY_CREATED;
        mCalled = false;
        onActivityCreated(savedInstanceState);
        if (!mCalled) {
            throw new SuperNotCalledException("Fragment " + this + " did not call through to super.onActivityCreated()");
        }
        if (mChildFragmentManager != null) mChildFragmentManager.dispatchActivityCreated();
    }

    void performStart() {
        if (mChildFragmentManager != null) {
            mChildFragmentManager.noteStateNotSaved();
            mChildFragmentManager.execPendingActions();
        }
        mState = STARTED;
        mCalled = false;
        onStart();
        if (!mCalled) throw new SuperNotCalledException("Fragment " + this + " did not call through to super.onStart()");
        if (mChildFragmentManager != null) mChildFragmentManager.dispatchStart();
        if (mLoaderManager != null) mLoaderManager.doReportStart();
    }

    void performResume() {
        if (mChildFragmentManager != null) {
            mChildFragmentManager.noteStateNotSaved();
            mChildFragmentManager.execPendingActions();
        }
        mState = RESUMED;
        mCalled = false;
        onResume();
        if (!mCalled) throw new SuperNotCalledException("Fragment " + this + " did not call through to super.onResume()");
        if (mChildFragmentManager != null) {
            mChildFragmentManager.dispatchResume();
            mChildFragmentManager.execPendingActions();
        }
    }

    void noteStateNotSaved() {
        if (mChildFragmentManager != null) mChildFragmentManager.noteStateNotSaved();
    }

    void performMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        if (mChildFragmentManager != null) {
            mChildFragmentManager.dispatchMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        }
    }

    void performPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (mChildFragmentManager != null) {
            mChildFragmentManager.dispatchPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        }
    }

    void performConfigurationChanged(Configuration newConfig) {
        onConfigurationChanged(newConfig);
        if (mChildFragmentManager != null) mChildFragmentManager.dispatchConfigurationChanged(newConfig);
    }

    void performLowMemory() {
        onLowMemory();
        if (mChildFragmentManager != null) mChildFragmentManager.dispatchLowMemory();
    }

    void performTrimMemory(int level) {
        onTrimMemory(level);
        if (mChildFragmentManager != null) mChildFragmentManager.dispatchTrimMemory(level);
    }

    boolean performCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        boolean show = false;
        if (!mHidden) {
            if (mHasMenu && mMenuVisible) {
                show = true;
                onCreateOptionsMenu(menu, inflater);
            }
            if (mChildFragmentManager != null) show |= mChildFragmentManager.dispatchCreateOptionsMenu(menu, inflater);
        }
        return show;
    }

    boolean performPrepareOptionsMenu(Menu menu) {
        boolean show = false;
        if (!mHidden) {
            if (mHasMenu && mMenuVisible) {
                show = true;
                onPrepareOptionsMenu(menu);
            }
            if (mChildFragmentManager != null) show |= mChildFragmentManager.dispatchPrepareOptionsMenu(menu);
        }
        return show;
    }

    boolean performOptionsItemSelected(MenuItem item) {
        if (!mHidden) {
            if (mHasMenu && mMenuVisible) {
                if (onOptionsItemSelected(item)) return true;
            }
            if (mChildFragmentManager != null) {
                if (mChildFragmentManager.dispatchOptionsItemSelected(item)) return true;
            }
        }
        return false;
    }

    boolean performContextItemSelected(MenuItem item) {
        if (!mHidden) {
            if (onContextItemSelected(item)) return true;
            if (mChildFragmentManager != null) {
                if (mChildFragmentManager.dispatchContextItemSelected(item)) return true;
            }
        }
        return false;
    }

    void performOptionsMenuClosed(Menu menu) {
        if (!mHidden) {
            if (mHasMenu && mMenuVisible) onOptionsMenuClosed(menu);
            if (mChildFragmentManager != null) mChildFragmentManager.dispatchOptionsMenuClosed(menu);
        }
    }

    void performSaveInstanceState(Bundle outState) {
        onSaveInstanceState(outState);
        if (mChildFragmentManager != null) {
            Parcelable p = mChildFragmentManager.saveAllState();
            if (p != null) outState.putParcelable(Activity.FRAGMENTS_TAG, p);
        }
    }

    void performPause() {
        if (mChildFragmentManager != null) mChildFragmentManager.dispatchPause();
        mState = STARTED;
        mCalled = false;
        onPause();
        if (!mCalled) throw new SuperNotCalledException("Fragment " + this + " did not call through to super.onPause()");
    }

    void performStop() {
        if (mChildFragmentManager != null) mChildFragmentManager.dispatchStop();
        mState = STOPPED;
        mCalled = false;
        onStop();
        if (!mCalled) throw new SuperNotCalledException("Fragment " + this + " did not call through to super.onStop()");
        if (mLoadersStarted) {
            mLoadersStarted = false;
            if (!mCheckedForLoaderManager) {
                mCheckedForLoaderManager = true;
                mLoaderManager = mHost.getLoaderManager(mWho, mLoadersStarted, false);
            }
            if (mLoaderManager != null) {
                if (mHost.getRetainLoaders()) mLoaderManager.doRetain();
                else mLoaderManager.doStop();
            }
        }
    }

    void performDestroyView() {
        if (mChildFragmentManager != null) mChildFragmentManager.dispatchDestroyView();
        mState = CREATED;
        mCalled = false;
        onDestroyView();
        if (!mCalled) throw new SuperNotCalledException("Fragment " + this + " did not call through to super.onDestroyView()");
        if (mLoaderManager != null) mLoaderManager.doReportNextStart();
        mPerformedCreateView = false;
    }

    void performDestroy() {
        if (mChildFragmentManager != null) mChildFragmentManager.dispatchDestroy();
        mState = INITIALIZING;
        mCalled = false;
        mIsCreated = false;
        onDestroy();
        if (!mCalled) throw new SuperNotCalledException("Fragment " + this + " did not call through to super.onDestroy()");
        mChildFragmentManager = null;
    }

    void performDetach() {
        mCalled = false;
        onDetach();
        mLayoutInflater = null;
        if (!mCalled) throw new SuperNotCalledException("Fragment " + this + " did not call through to super.onDetach()");
        if (mChildFragmentManager != null) {
            if (!mRetaining) {
                throw new IllegalStateException("Child FragmentManager of " + this + " was not destroyed and this fragment is not retaining instance");
            }
            mChildFragmentManager.dispatchDestroy();
            mChildFragmentManager = null;
        }
    }
}
