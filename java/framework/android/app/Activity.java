package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.WindowManagerImpl;
import android.view.accessibility.AccessibilityEvent;
import com.android.internal.policy.PhoneWindow;

public class Activity extends ContextThemeWrapper implements LayoutInflater.Factory2, Window.Callback,
        KeyEvent.Callback, View.OnCreateContextMenuListener {
    public static final int RESULT_CANCELED = 0;
    public static final int RESULT_OK = -1;
    public static final int RESULT_FIRST_USER = 1;

    private static final int ST_NONE = 0;
    private static final int ST_CREATED = 1;
    private static final int ST_STARTED = 2;
    private static final int ST_RESUMED = 3;
    private static final int ST_STOPPED = 4;
    private static final int ST_DESTROYED = 5;

    private Application mApplication;
    private Intent mIntent;
    private ActivityInfo mInfo;
    private Window mWindow;
    private boolean mWindowAdded;
    private boolean mFinished;
    private int mState;
    private int mResultCode = RESULT_CANCELED;
    private Intent mResultData;

    public Activity() { super(); }

    void attach(Context context, ActivityInfo info, Intent intent, Application application) {
        attachBaseContext(context);
        mApplication = application;
        mInfo = info;
        mIntent = intent != null ? intent : new Intent();
        if (info != null) {
            int theme = info.getThemeResource();
            if (theme != 0) setTheme(theme);
        }
        mWindow = new PhoneWindow(this);
        mWindow.setWindowManager(WindowManagerImpl.getDefault(), null, info != null ? info.name : null);
        mWindow.setCallback(this);
        mWindow.getLayoutInflater().setPrivateFactory(this);
        if (info != null && info.softInputMode != 0) mWindow.setSoftInputMode(info.softInputMode);
    }

    public final Application getApplication() { return mApplication; }
    public Intent getIntent() { return mIntent; }
    public Window getWindow() {
        if (mWindow == null) {
            mWindow = new PhoneWindow(this);
            mWindow.setCallback(this);
        }
        return mWindow;
    }
    public WindowManager getWindowManager() { return getWindow().getWindowManager(); }
    public boolean isFinishing() { return mFinished; }
    public ComponentName getComponentName() {
        return mInfo != null ? new ComponentName(mInfo.packageName, mInfo.name) : null;
    }

    public void setContentView(View view) { getWindow().setContentView(view); }
    public void setContentView(View view, ViewGroup.LayoutParams params) { getWindow().setContentView(view, params); }
    public void setContentView(int layoutResID) { getWindow().setContentView(layoutResID); }
    public void addContentView(View view, ViewGroup.LayoutParams params) { getWindow().addContentView(view, params); }

    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int id) { return (T) getWindow().findViewById(id); }

    public final void setResult(int resultCode) { setResult(resultCode, null); }
    public final void setResult(int resultCode, Intent data) {
        mResultCode = resultCode;
        mResultData = data;
    }

    public void finish() {
        if (mFinished) return;
        mFinished = true;
        ActivityThread.finishActivity(this, mResultCode, mResultData);
    }

    public void onBackPressed() { finish(); }

    public final void runOnUiThread(Runnable action) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) action.run();
        else new android.os.Handler(android.os.Looper.getMainLooper()).post(action);
    }

    @Override
    public void startActivity(Intent intent) { startActivity(intent, null); }

    @Override
    public void startActivity(Intent intent, Bundle options) { startActivityForResult(intent, -1, options); }

    public void startActivityForResult(Intent intent, int requestCode) {
        startActivityForResult(intent, requestCode, null);
    }

    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        ActivityThread.startActivity(this, intent, requestCode);
    }

    protected void onCreate(Bundle savedInstanceState) { onCreate(savedInstanceState, null); }
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {}
    protected void onRestart() {}
    protected void onStart() {}
    protected void onResume() {}
    protected void onPause() {}
    protected void onStop() {}
    protected void onDestroy() {}
    protected void onNewIntent(Intent intent) { mIntent = intent; }
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {}


    void performCreate() {
        if (mState != ST_NONE) return;
        onCreate(null);
        if (mFinished || mState == ST_DESTROYED) return;
        if (mApplication != null) mApplication.dispatchCreated(this, null);
        mState = ST_CREATED;
    }

    void performStart() {
        if (mFinished || mState == ST_DESTROYED) return;
        if (mState != ST_CREATED && mState != ST_STOPPED) return;
        if (mState == ST_STOPPED) {
            onRestart();
            mState = ST_CREATED;
        }
        onStart();
        if (mApplication != null) mApplication.dispatchStarted(this);
        mState = ST_STARTED;
    }

    void performResume() {
        if (mFinished || mState == ST_DESTROYED) return;
        if (mState == ST_STOPPED) performStart();
        if (mState != ST_STARTED) return;
        onResume();
        if (mApplication != null) mApplication.dispatchResumed(this);
        mState = ST_RESUMED;
        makeVisible();
    }

    void performPause() {
        if (mState != ST_RESUMED) return;
        onPause();
        if (mApplication != null) mApplication.dispatchPaused(this);
        mState = ST_STARTED;
    }

    void performStop() {
        if (mState == ST_RESUMED) performPause();
        if (mState != ST_STARTED) return;
        onStop();
        if (mApplication != null) mApplication.dispatchStopped(this);
        mState = ST_STOPPED;
    }

    void performDestroy() {
        if (mState == ST_DESTROYED || mState == ST_NONE) return;
        performStop();
        makeInvisible();
        onDestroy();
        if (mApplication != null) mApplication.dispatchDestroyed(this);
        mState = ST_DESTROYED;
    }

    void deliverResult(int requestCode, int resultCode, Intent data) {
        onActivityResult(requestCode, resultCode, data);
    }

    private void makeVisible() {
        View decor = getWindow().getDecorView();
        if (!mWindowAdded) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.type = WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
            getWindowManager().addView(decor, lp);
            mWindowAdded = true;
        } else {
            decor.invalidate();
        }
    }

    private void makeInvisible() {
        if (!mWindowAdded) return;
        mWindowAdded = false;
        getWindowManager().removeView(getWindow().getDecorView());
    }

    // ---------------------------------------------------------------- Window.Callback / KeyEvent.Callback (WS1)

    private CharSequence mTitle;
    private MenuInflater mMenuInflater;

    public void onUserInteraction() {}

    protected void onUserLeaveHint() {}

    public boolean dispatchKeyEvent(KeyEvent event) {
        onUserInteraction();
        Window win = getWindow();
        if (win.superDispatchKeyEvent(event)) return true;
        View decor = win.peekDecorView();
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

    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) { return false; }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            event.startTracking();
            return true;
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

    public boolean onKeyShortcut(int keyCode, KeyEvent event) { return false; }

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
        if (featureId == Window.FEATURE_OPTIONS_PANEL) return onCreateOptionsMenu(menu);
        return false;
    }

    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL) return onPrepareOptionsMenu(menu) && menu.hasVisibleItems();
        return true;
    }

    public boolean onMenuOpened(int featureId, Menu menu) { return true; }

    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (featureId) {
            case Window.FEATURE_OPTIONS_PANEL:
                return onOptionsItemSelected(item);
            case Window.FEATURE_CONTEXT_MENU:
                return onContextItemSelected(item);
            default:
                return false;
        }
    }

    public void onPanelClosed(int featureId, Menu menu) {}

    public boolean onCreateOptionsMenu(Menu menu) { return true; }

    public boolean onPrepareOptionsMenu(Menu menu) { return true; }

    public boolean onOptionsItemSelected(MenuItem item) { return false; }

    public void onOptionsMenuClosed(Menu menu) {}

    public void invalidateOptionsMenu() {}

    public void openOptionsMenu() {}

    public void closeOptionsMenu() {}

    public boolean onContextItemSelected(MenuItem item) { return false; }

    public void onContextMenuClosed(Menu menu) {}

    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {}

    public void registerForContextMenu(View view) { view.setOnCreateContextMenuListener(this); }

    public void unregisterForContextMenu(View view) { view.setOnCreateContextMenuListener(null); }

    public void openContextMenu(View view) { view.showContextMenu(); }

    public void closeContextMenu() {}

    public MenuInflater getMenuInflater() {
        if (mMenuInflater == null) mMenuInflater = new MenuInflater(this);
        return mMenuInflater;
    }

    public void onWindowAttributesChanged(WindowManager.LayoutParams params) {}

    public void onContentChanged() {}

    public void onWindowFocusChanged(boolean hasFocus) {}

    public void onAttachedToWindow() {}

    public void onDetachedFromWindow() {}

    public boolean hasWindowFocus() {
        View d = mWindow != null ? mWindow.peekDecorView() : null;
        return d != null && d.hasWindowFocus();
    }

    public boolean onSearchRequested() { return false; }

    public boolean onSearchRequested(SearchEvent searchEvent) { return onSearchRequested(); }

    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) { return null; }

    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) { return null; }

    public void onActionModeStarted(ActionMode mode) {}

    public void onActionModeFinished(ActionMode mode) {}

    public View onCreateView(String name, Context context, AttributeSet attrs) { return null; }

    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        if (!"fragment".equals(name)) return onCreateView(name, context, attrs);
        // TODO(WS4): legacy framework fragments inflated from <fragment>.
        return null;
    }

    public View getCurrentFocus() { return mWindow != null ? mWindow.getCurrentFocus() : null; }

    public LayoutInflater getLayoutInflater() { return getWindow().getLayoutInflater(); }

    public void setTitle(CharSequence title) {
        mTitle = title;
        getWindow().setTitle(title);
    }

    public void setTitle(int titleId) { setTitle(getText(titleId)); }

    public final CharSequence getTitle() { return mTitle; }

    @Override
    public Object getSystemService(String name) {
        if (WINDOW_SERVICE.equals(name)) return getWindowManager();
        return super.getSystemService(name);
    }
}
