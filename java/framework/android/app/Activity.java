package android.app;

import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.util.SuperNotCalledException;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerImpl;
import android.view.accessibility.AccessibilityEvent;
import com.android.internal.policy.PhoneWindow;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * An activity (AOSP Activity structure): lifecycle with the pre/post callbacks
 * AndroidX relies on, saved instance state, legacy fragments, managed dialogs,
 * menus, results and recreation on configuration change. ActivityThread
 * drives the perform* methods.
 */
public class Activity extends ContextThemeWrapper implements LayoutInflater.Factory2, Window.Callback,
        KeyEvent.Callback, View.OnCreateContextMenuListener, ComponentCallbacks2 {
    private static final String TAG = "Activity";

    public static final int RESULT_CANCELED = 0;
    public static final int RESULT_OK = -1;
    public static final int RESULT_FIRST_USER = 1;

    public static final int DEFAULT_KEYS_DISABLE = 0;
    public static final int DEFAULT_KEYS_DIALER = 1;
    public static final int DEFAULT_KEYS_SHORTCUT = 2;
    public static final int DEFAULT_KEYS_SEARCH_LOCAL = 3;
    public static final int DEFAULT_KEYS_SEARCH_GLOBAL = 4;

    public static final int FULLSCREEN_MODE_REQUEST_EXIT = 0;
    public static final int FULLSCREEN_MODE_REQUEST_ENTER = 1;
    public static final int OVERRIDE_TRANSITION_OPEN = 0;
    public static final int OVERRIDE_TRANSITION_CLOSE = 1;

    protected static final int[] FOCUSED_STATE_SET = {android.R.attr.state_focused};

    static final String FRAGMENTS_TAG = "android:fragments";
    private static final String WINDOW_HIERARCHY_TAG = "android:viewHierarchyState";
    private static final String SAVED_DIALOG_IDS_KEY = "android:savedDialogIds";
    private static final String SAVED_DIALOGS_TAG = "android:savedDialogs";
    private static final String SAVED_DIALOG_KEY_PREFIX = "android:dialog_";
    private static final String SAVED_DIALOG_ARGS_KEY_PREFIX = "android:dialog_args_";

    private Application mApplication;
    Intent mIntent;
    ActivityInfo mActivityInfo;
    private ComponentName mComponent;
    private Window mWindow;
    private WindowManager mWindowManager;
    View mDecor;
    boolean mWindowAdded;
    boolean mVisibleFromServer;
    boolean mVisibleFromClient = true;
    boolean mFinished;
    boolean mDestroyed;
    boolean mStopped;
    boolean mResumed;
    boolean mCreated;
    boolean mCalled;
    boolean mChangingConfigurations;
    int mConfigChangeFlags;
    int mResultCode = RESULT_CANCELED;
    Intent mResultData;
    private CharSequence mTitle;
    private int mTitleColor = 0;
    private boolean mTitleReady;
    private int mDefaultKeyMode = DEFAULT_KEYS_DISABLE;
    private int mRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private MenuInflater mMenuInflater;
    private SearchEvent mSearchEvent;
    private ActionMode mActionMode;
    private ActionBar mActionBar;
    private boolean mEnableDefaultActionBarUp;
    private int mActionModeTypeStarting = ActionMode.TYPE_PRIMARY;
    Configuration mCurrentConfig;
    NonConfigurationInstances mLastNonConfigurationInstances;
    private SparseArray<ManagedDialog> mManagedDialogs;
    private final ArrayList<Application.ActivityLifecycleCallbacks> mActivityLifecycleCallbacks =
            new ArrayList<Application.ActivityLifecycleCallbacks>();

    final Handler mHandler = new Handler(Looper.getMainLooper());
    final FragmentController mFragments = FragmentController.createController(new HostCallbacks());

    static final class NonConfigurationInstances {
        Object activity;
        HashMap<String, Object> children;
        FragmentManagerNonConfig fragments;
        ArrayMap<String, LoaderManager> loaders;
    }

    private static class ManagedDialog {
        Dialog mDialog;
        Bundle mArgs;
    }

    public Activity() { super(); }

    // ---------------------------------------------------------------- attach (ActivityThread)

    final void attach(Context context, ActivityInfo info, Intent intent, Application application,
            NonConfigurationInstances lastNonConfigurationInstances, Configuration config) {
        attachBaseContext(context);
        mFragments.attachHost(null);
        mApplication = application;
        mActivityInfo = info;
        mIntent = intent != null ? intent : new Intent();
        mComponent = info != null ? new ComponentName(info.packageName, info.name) : null;
        mLastNonConfigurationInstances = lastNonConfigurationInstances;
        mCurrentConfig = config != null ? new Configuration(config) : null;
        if (info != null) {
            int theme = info.getThemeResource();
            if (theme != 0) setTheme(theme);
            mRequestedOrientation = info.screenOrientation;
        }
        mWindow = new PhoneWindow(this);
        mWindow.setWindowManager(WindowManagerImpl.getDefault(), null, info != null ? info.name : null);
        mWindow.setCallback(this);
        mWindow.getLayoutInflater().setPrivateFactory(this);
        if (info != null && info.softInputMode != 0) mWindow.setSoftInputMode(info.softInputMode);
        if (info != null && info.uiOptions != 0) mWindow.setUiOptions(info.uiOptions);
        mWindowManager = mWindow.getWindowManager();
        if (info != null) {
            CharSequence label = info.loadLabel(getPackageManager());
            if (label != null) mTitle = label;
        }
    }

    // ---------------------------------------------------------------- basic accessors

    public final Application getApplication() { return mApplication; }

    public final boolean isChild() { return false; }

    public final Activity getParent() { return null; }

    public Intent getIntent() { return mIntent; }

    public void setIntent(Intent newIntent) { mIntent = newIntent; }

    public WindowManager getWindowManager() { return mWindowManager; }

    public Window getWindow() { return mWindow; }

    public LoaderManager getLoaderManager() { return mFragments.getLoaderManager(); }

    public View getCurrentFocus() { return mWindow != null ? mWindow.getCurrentFocus() : null; }

    public ComponentName getComponentName() { return mComponent; }

    public String getLocalClassName() {
        final String pkg = getPackageName();
        final String cls = mComponent != null ? mComponent.getClassName() : getClass().getName();
        int packageLen = pkg.length();
        if (!cls.startsWith(pkg) || cls.length() <= packageLen || cls.charAt(packageLen) != '.') return cls;
        return cls.substring(packageLen + 1);
    }

    public SharedPreferences getPreferences(int mode) { return getSharedPreferences(getLocalClassName(), mode); }

    public FragmentManager getFragmentManager() { return mFragments.getFragmentManager(); }

    public void onAttachFragment(Fragment fragment) {}

    public ActionBar getActionBar() {
        initWindowDecorActionBar();
        return mActionBar;
    }

    public void setActionBar(android.widget.Toolbar toolbar) {
        final ActionBar ab = getActionBar();
        if (ab instanceof com.android.internal.app.WindowDecorActionBar) {
            throw new IllegalStateException("This Activity already has an action bar supplied "
                    + "by the window decor. Do not request Window.FEATURE_ACTION_BAR and set "
                    + "android:windowActionBar to false in your theme to use a Toolbar instead.");
        }
        // The menu inflater is themed by the action bar; drop it so the next one uses the new bar.
        mMenuInflater = null;
        if (ab != null) ab.onDestroy();
        if (toolbar != null) {
            final com.android.internal.app.ToolbarActionBar tbab =
                    new com.android.internal.app.ToolbarActionBar(toolbar, getTitle(), this);
            mActionBar = tbab;
            mWindow.setCallback(tbab.getWrappedWindowCallback());
        } else {
            mActionBar = null;
            mWindow.setCallback(this);
        }
        invalidateOptionsMenu();
    }

    /** Creates the window decor's action bar once the decor exists (AOSP initWindowDecorActionBar). */
    private void initWindowDecorActionBar() {
        final Window window = getWindow();
        if (window == null) return;
        // Initializing the window decor can change window feature flags; do it before checking them.
        window.getDecorView();
        if (isChild() || !window.hasFeature(Window.FEATURE_ACTION_BAR) || mActionBar != null) return;
        mActionBar = new com.android.internal.app.WindowDecorActionBar(this);
        mActionBar.setDefaultDisplayHomeAsUpEnabled(mEnableDefaultActionBarUp);
        if (mActivityInfo != null) {
            window.setDefaultIcon(mActivityInfo.getIconResource());
            window.setDefaultLogo(mActivityInfo.getLogoResource());
        }
    }

    public boolean isChangingConfigurations() { return mChangingConfigurations; }

    public int getChangingConfigurations() { return mConfigChangeFlags; }

    public Object getLastNonConfigurationInstance() {
        return mLastNonConfigurationInstances != null ? mLastNonConfigurationInstances.activity : null;
    }

    public Object onRetainNonConfigurationInstance() { return null; }

    /** Hidden AOSP API. */
    HashMap<String, Object> getLastNonConfigurationChildInstances() {
        return mLastNonConfigurationInstances != null ? mLastNonConfigurationInstances.children : null;
    }

    /** Hidden AOSP API. */
    HashMap<String, Object> onRetainNonConfigurationChildInstances() { return null; }

    NonConfigurationInstances retainNonConfigurationInstances() {
        Object activity = onRetainNonConfigurationInstance();
        HashMap<String, Object> children = onRetainNonConfigurationChildInstances();
        FragmentManagerNonConfig fragments = mFragments.retainNestedNonConfig();
        mFragments.doLoaderStart();
        mFragments.doLoaderStop(true);
        ArrayMap<String, LoaderManager> loaders = mFragments.retainLoaderNonConfig();
        if (activity == null && children == null && fragments == null && loaders == null) return null;
        NonConfigurationInstances nci = new NonConfigurationInstances();
        nci.activity = activity;
        nci.children = children;
        nci.fragments = fragments;
        nci.loaders = loaders;
        return nci;
    }

    // ---------------------------------------------------------------- lifecycle callbacks

    public void registerActivityLifecycleCallbacks(Application.ActivityLifecycleCallbacks callback) {
        synchronized (mActivityLifecycleCallbacks) {
            mActivityLifecycleCallbacks.add(callback);
        }
    }

    public void unregisterActivityLifecycleCallbacks(Application.ActivityLifecycleCallbacks callback) {
        synchronized (mActivityLifecycleCallbacks) {
            mActivityLifecycleCallbacks.remove(callback);
        }
    }

    private Application.ActivityLifecycleCallbacks[] collectActivityLifecycleCallbacks() {
        synchronized (mActivityLifecycleCallbacks) {
            return mActivityLifecycleCallbacks.toArray(
                    new Application.ActivityLifecycleCallbacks[mActivityLifecycleCallbacks.size()]);
        }
    }

    private void dispatchActivityPreCreated(Bundle savedInstanceState) {
        if (mApplication != null) mApplication.dispatchActivityPreCreated(this, savedInstanceState);
        for (Application.ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) {
            cb.onActivityPreCreated(this, savedInstanceState);
        }
    }

    private void dispatchActivityCreated(Bundle savedInstanceState) {
        if (mApplication != null) mApplication.dispatchActivityCreated(this, savedInstanceState);
        for (Application.ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) {
            cb.onActivityCreated(this, savedInstanceState);
        }
    }

    private void dispatchActivityPostCreated(Bundle savedInstanceState) {
        for (Application.ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) {
            cb.onActivityPostCreated(this, savedInstanceState);
        }
        if (mApplication != null) mApplication.dispatchActivityPostCreated(this, savedInstanceState);
    }

    private void dispatchActivityPreStarted() {
        if (mApplication != null) mApplication.dispatchActivityPreStarted(this);
        for (Application.ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) cb.onActivityPreStarted(this);
    }

    private void dispatchActivityStarted() {
        if (mApplication != null) mApplication.dispatchActivityStarted(this);
        for (Application.ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) cb.onActivityStarted(this);
    }

    private void dispatchActivityPostStarted() {
        for (Application.ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) cb.onActivityPostStarted(this);
        if (mApplication != null) mApplication.dispatchActivityPostStarted(this);
    }

    private void dispatchActivityPreResumed() {
        if (mApplication != null) mApplication.dispatchActivityPreResumed(this);
        for (Application.ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) cb.onActivityPreResumed(this);
    }

    private void dispatchActivityResumed() {
        if (mApplication != null) mApplication.dispatchActivityResumed(this);
        for (Application.ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) cb.onActivityResumed(this);
    }

    private void dispatchActivityPostResumed() {
        for (Application.ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) cb.onActivityPostResumed(this);
        if (mApplication != null) mApplication.dispatchActivityPostResumed(this);
    }

    private void dispatchActivityPrePaused() {
        Application.ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPrePaused(this);
        if (mApplication != null) mApplication.dispatchActivityPrePaused(this);
    }

    private void dispatchActivityPaused() {
        Application.ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPaused(this);
        if (mApplication != null) mApplication.dispatchActivityPaused(this);
    }

    private void dispatchActivityPostPaused() {
        if (mApplication != null) mApplication.dispatchActivityPostPaused(this);
        Application.ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPostPaused(this);
    }

    private void dispatchActivityPreStopped() {
        Application.ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPreStopped(this);
        if (mApplication != null) mApplication.dispatchActivityPreStopped(this);
    }

    private void dispatchActivityStopped() {
        Application.ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityStopped(this);
        if (mApplication != null) mApplication.dispatchActivityStopped(this);
    }

    private void dispatchActivityPostStopped() {
        if (mApplication != null) mApplication.dispatchActivityPostStopped(this);
        Application.ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPostStopped(this);
    }

    private void dispatchActivityPreSaveInstanceState(Bundle outState) {
        Application.ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPreSaveInstanceState(this, outState);
        if (mApplication != null) mApplication.dispatchActivityPreSaveInstanceState(this, outState);
    }

    private void dispatchActivitySaveInstanceState(Bundle outState) {
        Application.ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivitySaveInstanceState(this, outState);
        if (mApplication != null) mApplication.dispatchActivitySaveInstanceState(this, outState);
    }

    private void dispatchActivityPostSaveInstanceState(Bundle outState) {
        if (mApplication != null) mApplication.dispatchActivityPostSaveInstanceState(this, outState);
        Application.ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPostSaveInstanceState(this, outState);
    }

    private void dispatchActivityPreDestroyed() {
        Application.ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPreDestroyed(this);
        if (mApplication != null) mApplication.dispatchActivityPreDestroyed(this);
    }

    private void dispatchActivityDestroyed() {
        Application.ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityDestroyed(this);
        if (mApplication != null) mApplication.dispatchActivityDestroyed(this);
    }

    private void dispatchActivityPostDestroyed() {
        if (mApplication != null) mApplication.dispatchActivityPostDestroyed(this);
        Application.ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPostDestroyed(this);
    }

    // ---------------------------------------------------------------- lifecycle (app overrides)

    protected void onCreate(Bundle savedInstanceState) {
        if (mLastNonConfigurationInstances != null) {
            mFragments.restoreLoaderNonConfig(mLastNonConfigurationInstances.loaders);
        }
        if (savedInstanceState != null) {
            Parcelable p = savedInstanceState.getParcelable(FRAGMENTS_TAG);
            mFragments.restoreAllState(p, mLastNonConfigurationInstances != null
                    ? mLastNonConfigurationInstances.fragments : null);
        }
        mFragments.dispatchCreate();
        dispatchActivityCreated(savedInstanceState);
        mCalled = true;
    }

    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) { onCreate(savedInstanceState); }

    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        if (mWindow != null) {
            Bundle windowState = savedInstanceState.getBundle(WINDOW_HIERARCHY_TAG);
            if (windowState != null) mWindow.restoreHierarchyState(windowState);
        }
    }

    public void onRestoreInstanceState(Bundle savedInstanceState, PersistableBundle persistentState) {
        if (savedInstanceState != null) onRestoreInstanceState(savedInstanceState);
    }

    protected void onPostCreate(Bundle savedInstanceState) {
        if (!isChild()) {
            mTitleReady = true;
            onTitleChanged(getTitle(), getTitleColor());
        }
        mCalled = true;
    }

    public void onPostCreate(Bundle savedInstanceState, PersistableBundle persistentState) { onPostCreate(savedInstanceState); }

    protected void onStart() {
        mCalled = true;
        mFragments.doLoaderStart();
        dispatchActivityStarted();
    }

    protected void onRestart() { mCalled = true; }

    public void onStateNotSaved() {}

    protected void onResume() {
        dispatchActivityResumed();
        mCalled = true;
    }

    protected void onPostResume() {
        final Window win = getWindow();
        if (win != null) win.makeActive();
        if (mActionBar != null) mActionBar.setShowHideAnimationEnabled(true);
        mCalled = true;
    }

    public void onTopResumedActivityChanged(boolean isTopResumedActivity) {}

    protected void onNewIntent(Intent intent) {}

    protected void onSaveInstanceState(Bundle outState) {
        outState.putBundle(WINDOW_HIERARCHY_TAG, mWindow.saveHierarchyState());
        Parcelable p = mFragments.saveAllState();
        if (p != null) outState.putParcelable(FRAGMENTS_TAG, p);
        dispatchActivitySaveInstanceState(outState);
    }

    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) { onSaveInstanceState(outState); }

    protected void onPause() {
        dispatchActivityPaused();
        mCalled = true;
    }

    protected void onUserLeaveHint() {}

    public boolean onCreateThumbnail(android.graphics.Bitmap outBitmap, android.graphics.Canvas canvas) { return false; }

    public CharSequence onCreateDescription() { return null; }

    public void onProvideAssistData(Bundle data) {}

    protected void onStop() {
        if (mActionBar != null) mActionBar.setShowHideAnimationEnabled(false);
        mFragments.doLoaderStop(false);
        dispatchActivityStopped();
        mCalled = true;
    }

    protected void onDestroy() {
        mCalled = true;
        if (mManagedDialogs != null) {
            final int numDialogs = mManagedDialogs.size();
            for (int i = 0; i < numDialogs; i++) {
                final ManagedDialog md = mManagedDialogs.valueAt(i);
                if (md.mDialog.isShowing()) md.mDialog.dismiss();
            }
            mManagedDialogs = null;
        }
        if (mActionBar != null) mActionBar.onDestroy();
        dispatchActivityDestroyed();
    }

    public void onConfigurationChanged(Configuration newConfig) {
        mCalled = true;
        mFragments.dispatchConfigurationChanged(newConfig);
        if (mWindow != null) mWindow.onConfigurationChanged(newConfig);
        if (mActionBar != null) mActionBar.onConfigurationChanged(newConfig);
    }

    public void onLowMemory() {
        mCalled = true;
        mFragments.dispatchLowMemory();
    }

    public void onTrimMemory(int level) {
        mCalled = true;
        mFragments.dispatchTrimMemory(level);
    }

    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        onMultiWindowModeChanged(isInMultiWindowMode);
    }

    @Deprecated
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {}

    public boolean isInMultiWindowMode() { return false; }

    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        onPictureInPictureModeChanged(isInPictureInPictureMode);
    }

    @Deprecated
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {}

    public boolean isInPictureInPictureMode() { return false; }

    @Deprecated
    public void enterPictureInPictureMode() {}

    public int getMaxNumPictureInPictureActions() { return 0; }

    public boolean onPictureInPictureRequested() { return false; }

    // ---------------------------------------------------------------- perform* (ActivityThread)

    final void performCreate(Bundle icicle) {
        dispatchActivityPreCreated(icicle);
        mCalled = false;
        onCreate(icicle);
        if (!mCalled) {
            throw new SuperNotCalledException("Activity " + mComponent.toShortString()
                    + " did not call through to super.onCreate()");
        }
        mCreated = true;
        mFragments.dispatchActivityCreated();
        dispatchActivityPostCreated(icicle);
    }

    final void performStart() {
        dispatchActivityPreStarted();
        mFragments.noteStateNotSaved();
        mCalled = false;
        mFragments.execPendingActions();
        onStart();
        if (!mCalled) {
            throw new SuperNotCalledException("Activity " + mComponent.toShortString()
                    + " did not call through to super.onStart()");
        }
        mFragments.dispatchStart();
        mFragments.reportLoaderStart();
        mStopped = false;
        dispatchActivityPostStarted();
    }

    final void performRestart() {
        mFragments.noteStateNotSaved();
        if (mStopped) {
            mCalled = false;
            onRestart();
            if (!mCalled) {
                throw new SuperNotCalledException("Activity " + mComponent.toShortString()
                        + " did not call through to super.onRestart()");
            }
            performStart();
        }
    }

    final void performRestoreInstanceState(Bundle savedInstanceState) {
        onRestoreInstanceState(savedInstanceState);
        restoreManagedDialogs(savedInstanceState);
    }

    final void performPostCreate(Bundle savedInstanceState) {
        mCalled = false;
        onPostCreate(savedInstanceState);
        if (!mCalled) {
            throw new SuperNotCalledException("Activity " + mComponent.toShortString()
                    + " did not call through to super.onPostCreate()");
        }
    }

    final void performResume() {
        dispatchActivityPreResumed();
        performRestart();
        mFragments.execPendingActions();
        mCalled = false;
        onResume();
        if (!mCalled) {
            throw new SuperNotCalledException("Activity " + mComponent.toShortString()
                    + " did not call through to super.onResume()");
        }
        mResumed = true;
        mFragments.dispatchResume();
        mFragments.execPendingActions();
        mCalled = false;
        onPostResume();
        if (!mCalled) {
            throw new SuperNotCalledException("Activity " + mComponent.toShortString()
                    + " did not call through to super.onPostResume()");
        }
        dispatchActivityPostResumed();
    }

    final void performPause() {
        dispatchActivityPrePaused();
        mFragments.dispatchPause();
        mCalled = false;
        onPause();
        mResumed = false;
        if (!mCalled) {
            throw new SuperNotCalledException("Activity " + mComponent.toShortString()
                    + " did not call through to super.onPause()");
        }
        dispatchActivityPostPaused();
    }

    final void performUserLeaving() {
        onUserInteraction();
        onUserLeaveHint();
    }

    final void performStop() {
        if (!mStopped) {
            dispatchActivityPreStopped();
            if (mWindow != null) mWindow.closeAllPanels();
            mFragments.dispatchStop();
            mCalled = false;
            onStop();
            if (!mCalled) {
                throw new SuperNotCalledException("Activity " + mComponent.toShortString()
                        + " did not call through to super.onStop()");
            }
            mStopped = true;
            dispatchActivityPostStopped();
        }
        mResumed = false;
    }

    final void performSaveInstanceState(Bundle outState) {
        dispatchActivityPreSaveInstanceState(outState);
        onSaveInstanceState(outState);
        saveManagedDialogs(outState);
        dispatchActivityPostSaveInstanceState(outState);
    }

    final void performDestroy() {
        dispatchActivityPreDestroyed();
        mDestroyed = true;
        mFragments.dispatchDestroy();
        mCalled = false;
        onDestroy();
        if (!mCalled) {
            throw new SuperNotCalledException("Activity " + mComponent.toShortString()
                    + " did not call through to super.onDestroy()");
        }
        mFragments.doLoaderDestroy();
        dispatchActivityPostDestroyed();
    }

    final void performNewIntent(Intent intent) {
        mFragments.noteStateNotSaved();
        onNewIntent(intent);
    }

    final void performConfigurationChanged(Configuration newConfig) {
        mCurrentConfig = new Configuration(newConfig);
        mCalled = false;
        onConfigurationChanged(newConfig);
        if (!mCalled) {
            throw new SuperNotCalledException("Activity " + mComponent.toShortString()
                    + " did not call through to super.onConfigurationChanged()");
        }
    }

    final boolean isResumed() { return mResumed; }

    final boolean isStopped() { return mStopped; }

    void makeVisible() {
        if (!mWindowAdded) {
            mDecor = mWindow.getDecorView();
            WindowManager.LayoutParams lp = mWindow.getAttributes();
            if (lp.type == 0 || lp.type == WindowManager.LayoutParams.TYPE_APPLICATION) {
                lp.type = WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
            }
            mWindowManager.addView(mDecor, lp);
            mWindowAdded = true;
        }
        mDecor.setVisibility(View.VISIBLE);
    }

    /** framework-internal. Shows or hides the window as the activity becomes visible or stopped. */
    void updateVisibility(boolean show) {
        if (mDecor == null) return;
        if (show) {
            if (mVisibleFromClient) mDecor.setVisibility(View.VISIBLE);
        } else {
            mDecor.setVisibility(View.INVISIBLE);
        }
    }

    void removeWindow() {
        if (!mWindowAdded) return;
        mWindowAdded = false;
        mWindowManager.removeViewImmediate(mDecor);
        mDecor = null;
        mWindow.closeAllPanels();
    }

    public void setVisible(boolean visible) {
        if (mVisibleFromClient != visible) {
            mVisibleFromClient = visible;
            if (mVisibleFromServer) {
                if (visible) makeVisible();
                else if (mDecor != null) mDecor.setVisibility(View.INVISIBLE);
            }
        }
    }

    // ---------------------------------------------------------------- results and navigation

    void dispatchActivityResult(String who, int requestCode, int resultCode, Intent data) {
        mFragments.noteStateNotSaved();
        if (who == null) {
            onActivityResult(requestCode, resultCode, data);
        } else {
            Fragment frag = mFragments.findFragmentByWho(who);
            if (frag != null) frag.onActivityResult(requestCode, resultCode, data);
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {}

    public void onActivityReenter(int resultCode, Intent data) {}

    public final void setResult(int resultCode) {
        synchronized (this) {
            mResultCode = resultCode;
            mResultData = null;
        }
    }

    public final void setResult(int resultCode, Intent data) {
        synchronized (this) {
            mResultCode = resultCode;
            mResultData = data;
        }
    }

    public String getCallingPackage() { return ActivityThread.getCallingPackage(this); }

    public ComponentName getCallingActivity() { return ActivityThread.getCallingActivity(this); }

    public Uri getReferrer() { return null; }

    public Uri onProvideReferrer() { return null; }

    public boolean isFinishing() { return mFinished; }

    public boolean isDestroyed() { return mDestroyed; }

    public void finish() {
        if (mFinished) return;
        mFinished = true;
        ActivityThread.finishActivity(this, mResultCode, mResultData);
    }

    public void finishAffinity() { ActivityThread.finishAffinity(this); }

    public void finishFromChild(Activity child) { finish(); }

    public void finishAfterTransition() { finish(); }

    public void finishActivity(int requestCode) { ActivityThread.finishActivityForRequest(this, requestCode); }

    public void finishActivityFromChild(Activity child, int requestCode) { finishActivity(requestCode); }

    public void finishAndRemoveTask() { ActivityThread.finishAffinity(this); }

    public boolean releaseInstance() { return false; }

    public boolean moveTaskToBack(boolean nonRoot) { return false; }

    public int getTaskId() { return 1; }

    public boolean isTaskRoot() { return ActivityThread.isTaskRoot(this); }

    public void recreate() { ActivityThread.recreateActivity(this); }

    public void onBackPressed() {
        if (mActionBar != null && mActionBar.collapseActionView()) return;
        FragmentManager fragmentManager = mFragments.getFragmentManager();
        if (!fragmentManager.isStateSaved() && fragmentManager.popBackStackImmediate()) return;
        finishAfterTransition();
    }

    public boolean shouldUpRecreateTask(Intent targetIntent) { return false; }

    public boolean navigateUpTo(Intent upIntent) { return ActivityThread.navigateUpTo(this, upIntent); }

    public boolean navigateUpToFromChild(Activity child, Intent upIntent) { return navigateUpTo(upIntent); }

    public Intent getParentActivityIntent() {
        final String parentName = mActivityInfo != null ? mActivityInfo.parentActivityName : null;
        if (TextUtils.isEmpty(parentName)) return null;
        final ComponentName target = new ComponentName(this, parentName);
        return new Intent().setComponent(target);
    }

    public boolean onNavigateUp() {
        Intent upIntent = getParentActivityIntent();
        if (upIntent != null) {
            navigateUpTo(upIntent);
            return true;
        }
        return false;
    }

    public boolean onNavigateUpFromChild(Activity child) { return onNavigateUp(); }

    // ---------------------------------------------------------------- starting activities

    @Override
    public void startActivity(Intent intent) { startActivity(intent, null); }

    @Override
    public void startActivity(Intent intent, Bundle options) { startActivityForResult(intent, -1, options); }

    @Override
    public void startActivities(Intent[] intents) { startActivities(intents, null); }

    @Override
    public void startActivities(Intent[] intents, Bundle options) {
        for (Intent intent : intents) startActivity(intent, options);
    }

    public void startActivityForResult(Intent intent, int requestCode) { startActivityForResult(intent, requestCode, null); }

    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        ActivityThread.startActivity(this, intent, requestCode, null);
    }

    public void startActivityFromChild(Activity child, Intent intent, int requestCode) {
        startActivityForResult(intent, requestCode);
    }

    public void startActivityFromChild(Activity child, Intent intent, int requestCode, Bundle options) {
        startActivityForResult(intent, requestCode, options);
    }

    public void startActivityFromFragment(Fragment fragment, Intent intent, int requestCode) {
        startActivityFromFragment(fragment, intent, requestCode, null);
    }

    public void startActivityFromFragment(Fragment fragment, Intent intent, int requestCode, Bundle options) {
        ActivityThread.startActivity(this, intent, requestCode, fragment.mWho);
    }

    public boolean startActivityIfNeeded(Intent intent, int requestCode) { return startActivityIfNeeded(intent, requestCode, null); }

    public boolean startActivityIfNeeded(Intent intent, int requestCode, Bundle options) {
        startActivityForResult(intent, requestCode, options);
        return true;
    }

    public boolean startNextMatchingActivity(Intent intent) { return false; }

    public boolean startNextMatchingActivity(Intent intent, Bundle options) { return false; }

    public void startIntentSenderForResult(IntentSender intent, int requestCode, Intent fillInIntent, int flagsMask,
            int flagsValues, int extraFlags) throws IntentSender.SendIntentException {
        startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, null);
    }

    public void startIntentSenderForResult(IntentSender intent, int requestCode, Intent fillInIntent, int flagsMask,
            int flagsValues, int extraFlags, Bundle options) throws IntentSender.SendIntentException {
        if (fillInIntent != null) {
            fillInIntent.setFlags((fillInIntent.getFlags() & ~flagsMask) | (flagsValues & flagsMask));
        }
        Intent target;
        try {
            target = intent.getTarget().activityIntent(fillInIntent);
        } catch (PendingIntent.CanceledException e) {
            throw new IntentSender.SendIntentException(e);
        }
        // An activity target starts from here so the result comes back; anything else is just sent.
        if (target != null) startActivityForResult(target, requestCode, options);
        else intent.sendIntent(this, 0, fillInIntent, null, null);
    }

    public PendingIntent createPendingResult(int requestCode, Intent data, int flags) {
        return PendingIntent.getActivityResult(this, requestCode, data, flags);
    }

    public void overridePendingTransition(int enterAnim, int exitAnim) {}

    public void overridePendingTransition(int enterAnim, int exitAnim, int backgroundColor) {}

    public void overrideActivityTransition(int overrideType, int enterAnim, int exitAnim) {}

    public void overrideActivityTransition(int overrideType, int enterAnim, int exitAnim, int backgroundColor) {}

    public void clearOverrideActivityTransition(int overrideType) {}

    public final void runOnUiThread(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else mHandler.post(action);
    }

    // ---------------------------------------------------------------- permissions

    public final void requestPermissions(String[] permissions, int requestCode) {
        requestPermissions(permissions, requestCode, 0);
    }

    /** Every permission is granted here; the result arrives on the next main loop turn, as on Android. */
    public final void requestPermissions(final String[] permissions, final int requestCode, int deviceId) {
        if (requestCode < 0) throw new IllegalArgumentException("requestCode should be >= 0");
        final String[] perms = permissions != null ? permissions.clone() : new String[0];
        final int[] results = new int[perms.length];
        for (int i = 0; i < results.length; i++) results[i] = checkSelfPermission(perms[i]);
        mHandler.post(new Runnable() {
            public void run() {
                if (!mDestroyed) onRequestPermissionsResult(requestCode, perms, results);
            }
        });
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {}

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults, int deviceId) {
        onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public boolean shouldShowRequestPermissionRationale(String permission) { return false; }

    public boolean shouldShowRequestPermissionRationale(String permission, int deviceId) { return false; }

    // ---------------------------------------------------------------- window and content

    public void setContentView(View view) {
        getWindow().setContentView(view);
        initWindowDecorActionBar();
    }

    public void setContentView(View view, ViewGroup.LayoutParams params) {
        getWindow().setContentView(view, params);
        initWindowDecorActionBar();
    }

    public void setContentView(int layoutResID) {
        getWindow().setContentView(layoutResID);
        initWindowDecorActionBar();
    }

    public void addContentView(View view, ViewGroup.LayoutParams params) {
        getWindow().addContentView(view, params);
        initWindowDecorActionBar();
    }

    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int id) { return (T) getWindow().findViewById(id); }

    public final <T extends View> T requireViewById(int id) {
        T view = findViewById(id);
        if (view == null) throw new IllegalArgumentException("ID does not reference a View inside this Activity");
        return view;
    }

    public final boolean requestWindowFeature(int featureId) { return getWindow().requestFeature(featureId); }

    public final void setFeatureDrawableResource(int featureId, int resId) { getWindow().setFeatureDrawableResource(featureId, resId); }

    public final void setFeatureDrawableUri(int featureId, Uri uri) { getWindow().setFeatureDrawableUri(featureId, uri); }

    public final void setFeatureDrawable(int featureId, Drawable drawable) { getWindow().setFeatureDrawable(featureId, drawable); }

    public final void setFeatureDrawableAlpha(int featureId, int alpha) { getWindow().setFeatureDrawableAlpha(featureId, alpha); }

    public void setFinishOnTouchOutside(boolean finish) { mWindow.setCloseOnTouchOutside(finish); }

    public void takeKeyEvents(boolean get) { getWindow().takeKeyEvents(get); }

    public final void setVolumeControlStream(int streamType) { getWindow().setVolumeControlStream(streamType); }

    public final int getVolumeControlStream() { return getWindow().getVolumeControlStream(); }

    public void setRequestedOrientation(int requestedOrientation) { mRequestedOrientation = requestedOrientation; }

    public int getRequestedOrientation() { return mRequestedOrientation; }

    public boolean isImmersive() { return false; }

    public void setImmersive(boolean i) {}

    public boolean setTranslucent(boolean translucent) { return false; }

    public void setShowWhenLocked(boolean showWhenLocked) {}

    public void setInheritShowWhenLocked(boolean inheritShowWhenLocked) {}

    public void setTurnScreenOn(boolean turnScreenOn) {}

    public void setRecentsScreenshotEnabled(boolean enabled) {}

    public void reportFullyDrawn() {}

    public void postponeEnterTransition() {}

    public void startPostponedEnterTransition() {}

    public boolean isActivityTransitionRunning() { return false; }

    public void onEnterAnimationComplete() {}

    public boolean isVoiceInteraction() { return false; }

    public boolean isVoiceInteractionRoot() { return false; }

    public boolean isLocalVoiceInteractionSupported() { return false; }

    public void startLocalVoiceInteraction(Bundle privateOptions) {}

    public void onLocalVoiceInteractionStarted() {}

    public void onLocalVoiceInteractionStopped() {}

    public void stopLocalVoiceInteraction() {}

    public void startLockTask() {}

    public void stopLockTask() {}

    public void showLockTaskEscapeMessage() {}

    public boolean showAssist(Bundle args) { return false; }

    public boolean isLaunchedFromBubble() { return false; }

    public void setTitle(CharSequence title) {
        mTitle = title;
        onTitleChanged(title, mTitleColor);
    }

    public void setTitle(int titleId) { setTitle(getText(titleId)); }

    @Deprecated
    public void setTitleColor(int textColor) {
        mTitleColor = textColor;
        onTitleChanged(mTitle, textColor);
    }

    public final CharSequence getTitle() { return mTitle; }

    public final int getTitleColor() { return mTitleColor; }

    protected void onTitleChanged(CharSequence title, int color) {
        if (mTitleReady) {
            final Window win = getWindow();
            if (win != null) {
                win.setTitle(title);
                if (color != 0) win.setTitleColor(color);
            }
        }
    }

    protected void onChildTitleChanged(Activity childActivity, CharSequence title) {}

    @Deprecated
    public final void setProgressBarVisibility(boolean visible) {}

    @Deprecated
    public final void setProgressBarIndeterminateVisibility(boolean visible) {}

    @Deprecated
    public final void setProgressBarIndeterminate(boolean indeterminate) {}

    @Deprecated
    public final void setProgress(int progress) {}

    @Deprecated
    public final void setSecondaryProgress(int secondaryProgress) {}

    public final void setDefaultKeyMode(int mode) { mDefaultKeyMode = mode; }

    @Override
    public Object getSystemService(String name) {
        if (getBaseContext() == null) {
            throw new IllegalStateException("System services not available to Activities before onCreate()");
        }
        if (WINDOW_SERVICE.equals(name)) return mWindowManager;
        return super.getSystemService(name);
    }

    public LayoutInflater getLayoutInflater() { return getWindow().getLayoutInflater(); }

    public MenuInflater getMenuInflater() {
        if (mMenuInflater == null) {
            initWindowDecorActionBar();
            mMenuInflater = mActionBar != null ? new MenuInflater(mActionBar.getThemedContext(), this)
                    : new MenuInflater(this);
        }
        return mMenuInflater;
    }

    public View onCreateView(String name, Context context, AttributeSet attrs) { return null; }

    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        if (!"fragment".equals(name)) return onCreateView(name, context, attrs);
        return mFragments.onCreateView(parent, name, context, attrs);
    }

    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.print(prefix);
        writer.print("Local Activity ");
        writer.print(Integer.toHexString(System.identityHashCode(this)));
        writer.println(" State:");
        String innerPrefix = prefix + "  ";
        writer.print(innerPrefix);
        writer.print("mResumed=");
        writer.print(mResumed);
        writer.print(" mStopped=");
        writer.print(mStopped);
        writer.print(" mFinished=");
        writer.println(mFinished);
        mFragments.getFragmentManager().dump(innerPrefix, fd, writer, args);
    }

    // ---------------------------------------------------------------- managed dialogs

    @Deprecated
    protected Dialog onCreateDialog(int id) { return null; }

    @Deprecated
    protected Dialog onCreateDialog(int id, Bundle args) { return onCreateDialog(id); }

    @Deprecated
    protected void onPrepareDialog(int id, Dialog dialog) { dialog.setOwnerActivity(this); }

    @Deprecated
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) { onPrepareDialog(id, dialog); }

    @Deprecated
    public final void showDialog(int id) { showDialog(id, null); }

    @Deprecated
    public final boolean showDialog(int id, Bundle args) {
        if (mManagedDialogs == null) mManagedDialogs = new SparseArray<ManagedDialog>();
        ManagedDialog md = mManagedDialogs.get(id);
        if (md == null) {
            md = new ManagedDialog();
            md.mDialog = createDialog(id, null, args);
            if (md.mDialog == null) return false;
            mManagedDialogs.put(id, md);
        }
        md.mArgs = args;
        onPrepareDialog(id, md.mDialog, args);
        md.mDialog.show();
        return true;
    }

    @Deprecated
    public final void dismissDialog(int id) {
        if (mManagedDialogs == null) throw missingDialog(id);
        final ManagedDialog md = mManagedDialogs.get(id);
        if (md == null) throw missingDialog(id);
        md.mDialog.dismiss();
    }

    private IllegalArgumentException missingDialog(int id) {
        return new IllegalArgumentException("no dialog with id " + id + " was ever shown via Activity#showDialog");
    }

    @Deprecated
    public final void removeDialog(int id) {
        if (mManagedDialogs != null) {
            final ManagedDialog md = mManagedDialogs.get(id);
            if (md != null) {
                md.mDialog.dismiss();
                mManagedDialogs.remove(id);
            }
        }
    }

    private Dialog createDialog(Integer dialogId, Bundle state, Bundle args) {
        final Dialog dialog = onCreateDialog(dialogId, args);
        if (dialog == null) return null;
        dialog.dispatchOnCreate(state);
        return dialog;
    }

    private void saveManagedDialogs(Bundle outState) {
        if (mManagedDialogs == null) return;
        final int numDialogs = mManagedDialogs.size();
        if (numDialogs == 0) return;
        Bundle dialogState = new Bundle();
        int[] ids = new int[mManagedDialogs.size()];
        for (int i = 0; i < numDialogs; i++) {
            final int key = mManagedDialogs.keyAt(i);
            ids[i] = key;
            final ManagedDialog md = mManagedDialogs.valueAt(i);
            dialogState.putBundle(SAVED_DIALOG_KEY_PREFIX + key, md.mDialog.onSaveInstanceState());
            if (md.mArgs != null) dialogState.putBundle(SAVED_DIALOG_ARGS_KEY_PREFIX + key, md.mArgs);
        }
        dialogState.putIntArray(SAVED_DIALOG_IDS_KEY, ids);
        outState.putBundle(SAVED_DIALOGS_TAG, dialogState);
    }

    private void restoreManagedDialogs(Bundle savedInstanceState) {
        final Bundle b = savedInstanceState.getBundle(SAVED_DIALOGS_TAG);
        if (b == null) return;
        final int[] ids = b.getIntArray(SAVED_DIALOG_IDS_KEY);
        if (ids == null) return;
        mManagedDialogs = new SparseArray<ManagedDialog>(ids.length);
        for (int dialogId : ids) {
            Bundle dialogState = b.getBundle(SAVED_DIALOG_KEY_PREFIX + dialogId);
            if (dialogState != null) {
                final ManagedDialog md = new ManagedDialog();
                md.mArgs = b.getBundle(SAVED_DIALOG_ARGS_KEY_PREFIX + dialogId);
                md.mDialog = createDialog(dialogId, dialogState, md.mArgs);
                if (md.mDialog != null) {
                    mManagedDialogs.put(dialogId, md);
                    onPrepareDialog(dialogId, md.mDialog, md.mArgs);
                    md.mDialog.onRestoreInstanceState(dialogState);
                }
            }
        }
    }

    // ---------------------------------------------------------------- Window.Callback / KeyEvent.Callback

    public void onUserInteraction() {}

    public boolean dispatchKeyEvent(KeyEvent event) {
        onUserInteraction();
        // Let the action bar open its menu in response to the menu key first.
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU && mActionBar != null && mActionBar.onMenuKeyEvent(event)) {
            return true;
        }
        Window win = getWindow();
        if (win.superDispatchKeyEvent(event)) return true;
        View decor = mDecor;
        if (decor == null) decor = win.peekDecorView();
        return event.dispatch(this, decor != null ? decor.getKeyDispatcherState() : null, this);
    }

    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        onUserInteraction();
        if (getWindow().superDispatchKeyShortcutEvent(event)) return true;
        return onKeyShortcut(event.getKeyCode(), event);
    }

    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) onUserInteraction();
        if (getWindow().superDispatchTouchEvent(ev)) return true;
        return onTouchEvent(ev);
    }

    public boolean dispatchTrackballEvent(MotionEvent ev) {
        onUserInteraction();
        if (getWindow().superDispatchTrackballEvent(ev)) return true;
        return onTrackballEvent(ev);
    }

    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        onUserInteraction();
        if (getWindow().superDispatchGenericMotionEvent(ev)) return true;
        return onGenericMotionEvent(ev);
    }

    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        event.setClassName(getClass().getName());
        event.setPackageName(getPackageName());
        return false;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            event.startTracking();
            return true;
        }
        if (mDefaultKeyMode == DEFAULT_KEYS_DISABLE) return false;
        if (mDefaultKeyMode == DEFAULT_KEYS_SHORTCUT) {
            Window w = getWindow();
            return w.hasFeature(Window.FEATURE_OPTIONS_PANEL)
                    && w.performPanelShortcut(Window.FEATURE_OPTIONS_PANEL, keyCode, event, Menu.FLAG_ALWAYS_PERFORM_CLOSE);
        }
        return false;
    }

    public boolean onKeyLongPress(int keyCode, KeyEvent event) { return false; }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.isTracking() && !event.isCanceled()) {
            onBackPressed();
            return true;
        }
        return false;
    }

    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) { return false; }

    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        final ActionBar actionBar = getActionBar();
        return actionBar != null && actionBar.onKeyShortcut(keyCode, event);
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (mWindow != null && mWindow.shouldCloseOnTouch(this, event)) {
            finish();
            return true;
        }
        return false;
    }

    public boolean onTrackballEvent(MotionEvent event) { return false; }

    public boolean onGenericMotionEvent(MotionEvent event) { return false; }

    public View onCreatePanelView(int featureId) { return null; }

    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            boolean show = onCreateOptionsMenu(menu);
            show |= mFragments.dispatchCreateOptionsMenu(menu, getMenuInflater());
            return show;
        }
        return false;
    }

    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            boolean goforit = onPrepareOptionsMenu(menu);
            goforit |= mFragments.dispatchPrepareOptionsMenu(menu);
            return goforit;
        }
        return true;
    }

    public boolean onMenuOpened(int featureId, Menu menu) {
        if (featureId == Window.FEATURE_ACTION_BAR) {
            initWindowDecorActionBar();
            if (mActionBar != null) mActionBar.dispatchMenuVisibilityChanged(true);
        }
        return true;
    }

    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (featureId) {
            case Window.FEATURE_OPTIONS_PANEL:
                if (onOptionsItemSelected(item)) return true;
                if (mFragments.dispatchOptionsItemSelected(item)) return true;
                if (item.getItemId() == android.R.id.home && mActionBar != null
                        && (mActionBar.getDisplayOptions() & ActionBar.DISPLAY_HOME_AS_UP) != 0) {
                    return getParent() == null ? onNavigateUp() : getParent().onNavigateUpFromChild(this);
                }
                return false;
            case Window.FEATURE_CONTEXT_MENU:
                if (onContextItemSelected(item)) return true;
                return mFragments.dispatchContextItemSelected(item);
            default:
                return false;
        }
    }

    public void onPanelClosed(int featureId, Menu menu) {
        switch (featureId) {
            case Window.FEATURE_OPTIONS_PANEL:
                mFragments.dispatchOptionsMenuClosed(menu);
                onOptionsMenuClosed(menu);
                break;
            case Window.FEATURE_CONTEXT_MENU:
                onContextMenuClosed(menu);
                break;
            case Window.FEATURE_ACTION_BAR:
                initWindowDecorActionBar();
                if (mActionBar != null) mActionBar.dispatchMenuVisibilityChanged(false);
                break;
            default:
                break;
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) { return true; }

    public boolean onPrepareOptionsMenu(Menu menu) { return true; }

    public boolean onOptionsItemSelected(MenuItem item) { return false; }

    public void onOptionsMenuClosed(Menu menu) {}

    public void invalidateOptionsMenu() {
        if (mActionBar == null || !mActionBar.invalidateOptionsMenu()) {
            getWindow().invalidatePanelMenu(Window.FEATURE_OPTIONS_PANEL);
        }
    }

    public void openOptionsMenu() {
        if (mActionBar == null || !mActionBar.openOptionsMenu()) getWindow().openPanel(Window.FEATURE_OPTIONS_PANEL, null);
    }

    public void closeOptionsMenu() {
        if (mActionBar == null || !mActionBar.closeOptionsMenu()) getWindow().closePanel(Window.FEATURE_OPTIONS_PANEL);
    }

    public boolean onContextItemSelected(MenuItem item) { return false; }

    public void onContextMenuClosed(Menu menu) {}

    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {}

    public void registerForContextMenu(View view) { view.setOnCreateContextMenuListener(this); }

    public void unregisterForContextMenu(View view) { view.setOnCreateContextMenuListener(null); }

    public void openContextMenu(View view) { view.showContextMenu(); }

    public void closeContextMenu() { getWindow().closePanel(Window.FEATURE_CONTEXT_MENU); }

    public void onProvideKeyboardShortcuts(List<?> data, Menu menu, int deviceId) {}

    public final void requestShowKeyboardShortcuts() {}

    public final void dismissKeyboardShortcutsHelper() {}

    public void onWindowAttributesChanged(WindowManager.LayoutParams params) {
        if (mDecor != null && mWindowAdded) getWindowManager().updateViewLayout(mDecor, params);
    }

    public void onContentChanged() {}

    public void onWindowFocusChanged(boolean hasFocus) {}

    public void onAttachedToWindow() {}

    public void onDetachedFromWindow() {}

    public boolean hasWindowFocus() {
        View d = mWindow != null ? mWindow.peekDecorView() : null;
        return d != null && d.hasWindowFocus();
    }

    public boolean onSearchRequested(SearchEvent searchEvent) {
        mSearchEvent = searchEvent;
        boolean result = onSearchRequested();
        mSearchEvent = null;
        return result;
    }

    public boolean onSearchRequested() { return false; }

    public final SearchEvent getSearchEvent() { return mSearchEvent; }

    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData, boolean globalSearch) {}

    public void triggerSearch(String query, Bundle appSearchData) {}

    public ActionMode startActionMode(ActionMode.Callback callback) { return mWindow.getDecorView().startActionMode(callback); }

    public ActionMode startActionMode(ActionMode.Callback callback, int type) {
        return mWindow.getDecorView().startActionMode(callback, type);
    }

    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
        // Only primary action modes are shown in the action bar.
        if (mActionModeTypeStarting == ActionMode.TYPE_PRIMARY) {
            initWindowDecorActionBar();
            if (mActionBar != null) return mActionBar.startActionMode(callback);
        }
        return null;
    }

    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
        try {
            mActionModeTypeStarting = type;
            return onWindowStartingActionMode(callback);
        } finally {
            mActionModeTypeStarting = ActionMode.TYPE_PRIMARY;
        }
    }

    public void onActionModeStarted(ActionMode mode) { mActionMode = mode; }

    public void onActionModeFinished(ActionMode mode) {
        if (mActionMode == mode) mActionMode = null;
    }

    // ---------------------------------------------------------------- fragment host

    class HostCallbacks extends FragmentHostCallback<Activity> {
        HostCallbacks() { super(Activity.this); }

        @Override
        public void onDump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
            Activity.this.dump(prefix, fd, writer, args);
        }

        @Override
        public boolean onShouldSaveFragmentState(Fragment fragment) { return !isFinishing(); }

        @Override
        public LayoutInflater onGetLayoutInflater() {
            final LayoutInflater result = Activity.this.getLayoutInflater();
            if (onUseFragmentManagerInflaterFactory()) return result.cloneInContext(Activity.this);
            return result;
        }

        @Override
        public boolean onUseFragmentManagerInflaterFactory() { return getApplicationInfo().targetSdkVersion >= 21; }

        @Override
        public Activity onGetHost() { return Activity.this; }

        @Override
        public void onInvalidateOptionsMenu() { Activity.this.invalidateOptionsMenu(); }

        @Override
        public void onStartActivityFromFragment(Fragment fragment, Intent intent, int requestCode, Bundle options) {
            Activity.this.startActivityFromFragment(fragment, intent, requestCode, options);
        }

        @Override
        public void onRequestPermissionsFromFragment(final Fragment fragment, String[] permissions, final int requestCode) {
            final String[] perms = permissions != null ? permissions.clone() : new String[0];
            final int[] results = new int[perms.length];
            for (int i = 0; i < results.length; i++) results[i] = checkSelfPermission(perms[i]);
            mHandler.post(new Runnable() {
                public void run() {
                    if (fragment.isAdded()) fragment.onRequestPermissionsResult(requestCode, perms, results);
                }
            });
        }

        @Override
        public boolean onHasWindowAnimations() { return getWindow() != null; }

        @Override
        public int onGetWindowAnimations() {
            final Window w = getWindow();
            return (w == null) ? 0 : w.getAttributes().windowAnimations;
        }

        @Override
        public void onAttachFragment(Fragment fragment) { Activity.this.onAttachFragment(fragment); }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends View> T onFindViewById(int id) { return (T) Activity.this.findViewById(id); }

        @Override
        public boolean onHasView() {
            final Window w = getWindow();
            return (w != null && w.peekDecorView() != null);
        }
    }
}
