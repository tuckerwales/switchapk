package android.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import com.android.internal.policy.PhoneWindow;
import java.lang.ref.WeakReference;

/**
 * Base class for dialogs (port of AOSP Dialog). A dialog owns a floating
 * PhoneWindow themed from {@code android:dialogTheme} and added to the window
 * manager on {@link #show}.
 */
public class Dialog implements DialogInterface, Window.Callback, KeyEvent.Callback,
        View.OnCreateContextMenuListener {
    private static final String TAG = "Dialog";
    private static final int DISMISS = 0x43;
    private static final int CANCEL = 0x44;
    private static final int SHOW = 0x45;
    private static final String DIALOG_SHOWING_TAG = "android:dialogShowing";
    private static final String DIALOG_HIERARCHY_TAG = "android:dialogHierarchy";

    private Activity mOwnerActivity;
    final Context mContext;
    final WindowManager mWindowManager;
    final Window mWindow;
    View mDecor;
    protected boolean mCancelable = true;
    private String mCancelAndDismissTaken;
    private Message mCancelMessage;
    private Message mDismissMessage;
    private Message mShowMessage;
    private OnKeyListener mOnKeyListener;
    private boolean mCreated = false;
    private boolean mShowing = false;
    private boolean mCanceled = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Handler mListenersHandler;
    private SearchEvent mSearchEvent;
    private ActionMode mActionMode;
    private int mActionModeTypeStarting = ActionMode.TYPE_PRIMARY;

    private final Runnable mDismissAction = new Runnable() {
        public void run() { dismissDialog(); }
    };

    public Dialog(Context context) { this(context, 0, true); }

    public Dialog(Context context, int themeResId) { this(context, themeResId, true); }

    Dialog(Context context, int themeResId, boolean createContextThemeWrapper) {
        if (createContextThemeWrapper) {
            if (themeResId == 0) {
                final TypedValue outValue = new TypedValue();
                context.getTheme().resolveAttribute(android.R.attr.dialogTheme, outValue, true);
                themeResId = outValue.resourceId;
            }
            mContext = new ContextThemeWrapper(context, themeResId);
        } else {
            mContext = context;
        }
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final Window w = new PhoneWindow(mContext);
        mWindow = w;
        w.setCallback(this);
        w.setWindowManager(mWindowManager, null, null);
        w.setGravity(Gravity.CENTER);
        mListenersHandler = new ListenersHandler(this);
        Activity owner = findActivity(context);
        if (owner != null) setOwnerActivity(owner);
    }

    @Deprecated
    protected Dialog(Context context, boolean cancelable, Message cancelCallback) {
        this(context);
        mCancelable = cancelable;
        mCancelMessage = cancelCallback;
    }

    protected Dialog(Context context, boolean cancelable, OnCancelListener cancelListener) {
        this(context);
        mCancelable = cancelable;
        setOnCancelListener(cancelListener);
    }

    private static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        return null;
    }

    public final Context getContext() { return mContext; }

    private ActionBar mActionBar;

    public ActionBar getActionBar() { return mActionBar; }

    public final void setOwnerActivity(Activity activity) { mOwnerActivity = activity; }

    public final Activity getOwnerActivity() { return mOwnerActivity; }

    public boolean isShowing() { return mDecor == null ? false : mDecor.getVisibility() == View.VISIBLE; }

    public void create() {
        if (!mCreated) dispatchOnCreate(null);
    }

    public void show() {
        if (mShowing) {
            if (mDecor != null) {
                mDecor.setVisibility(View.VISIBLE);
            }
            return;
        }
        mCanceled = false;
        if (!mCreated) {
            dispatchOnCreate(null);
        } else {
            final android.content.res.Configuration config = mContext.getResources().getConfiguration();
            mWindow.getDecorView().dispatchConfigurationChanged(config);
        }
        onStart();
        mDecor = mWindow.getDecorView();
        if (mActionBar == null && mWindow.hasFeature(Window.FEATURE_ACTION_BAR)) {
            final android.content.pm.ApplicationInfo info = mContext.getApplicationInfo();
            if (info != null) {
                mWindow.setDefaultIcon(info.icon);
                mWindow.setDefaultLogo(info.logo);
            }
            mActionBar = new com.android.internal.app.WindowDecorActionBar(this);
        }
        WindowManager.LayoutParams l = mWindow.getAttributes();
        if ((l.softInputMode & WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION) == 0) {
            WindowManager.LayoutParams nl = new WindowManager.LayoutParams();
            nl.copyFrom(l);
            nl.softInputMode |= WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION;
            l = nl;
        }
        mWindowManager.addView(mDecor, l);
        mShowing = true;
        sendShowMessage();
    }

    public void hide() {
        if (mDecor != null) mDecor.setVisibility(View.GONE);
    }

    public void dismiss() {
        if (Looper.myLooper() == mHandler.getLooper()) {
            dismissDialog();
        } else {
            mHandler.post(mDismissAction);
        }
    }

    void dismissDialog() {
        if (mDecor == null || !mShowing) return;
        if (mWindow.isDestroyed()) return;
        try {
            mWindowManager.removeViewImmediate(mDecor);
        } finally {
            if (mActionMode != null) mActionMode.finish();
            mDecor = null;
            mWindow.closeAllPanels();
            onStop();
            mShowing = false;
            sendDismissMessage();
        }
    }

    private void sendDismissMessage() {
        if (mDismissMessage != null) Message.obtain(mDismissMessage).sendToTarget();
    }

    private void sendShowMessage() {
        if (mShowMessage != null) Message.obtain(mShowMessage).sendToTarget();
    }

    void dispatchOnCreate(Bundle savedInstanceState) {
        if (!mCreated) {
            onCreate(savedInstanceState);
            mCreated = true;
        }
    }

    protected void onCreate(Bundle savedInstanceState) {}

    protected void onStart() {}

    protected void onStop() {}

    public Bundle onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(DIALOG_SHOWING_TAG, mShowing);
        if (mCreated) bundle.putBundle(DIALOG_HIERARCHY_TAG, mWindow.saveHierarchyState());
        return bundle;
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        final Bundle dialogHierarchyState = savedInstanceState.getBundle(DIALOG_HIERARCHY_TAG);
        if (dialogHierarchyState == null) return;
        dispatchOnCreate(savedInstanceState);
        mWindow.restoreHierarchyState(dialogHierarchyState);
        if (savedInstanceState.getBoolean(DIALOG_SHOWING_TAG)) show();
    }

    public Window getWindow() { return mWindow; }

    public View getCurrentFocus() { return mWindow != null ? mWindow.getCurrentFocus() : null; }

    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int id) { return (T) mWindow.findViewById(id); }

    public final <T extends View> T requireViewById(int id) {
        T view = findViewById(id);
        if (view == null) throw new IllegalArgumentException("ID does not reference a View inside this Dialog");
        return view;
    }

    public void setContentView(int layoutResID) { mWindow.setContentView(layoutResID); }

    public void setContentView(View view) { mWindow.setContentView(view); }

    public void setContentView(View view, ViewGroup.LayoutParams params) { mWindow.setContentView(view, params); }

    public void addContentView(View view, ViewGroup.LayoutParams params) { mWindow.addContentView(view, params); }

    public void setTitle(CharSequence title) {
        mWindow.setTitle(title);
        mWindow.getAttributes().setTitle(title);
    }

    public void setTitle(int titleId) { setTitle(mContext.getText(titleId)); }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            event.startTracking();
            return true;
        }
        return false;
    }

    public boolean onKeyLongPress(int keyCode, KeyEvent event) { return false; }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) && event.isTracking()
                && !event.isCanceled()) {
            onBackPressed();
            return true;
        }
        return false;
    }

    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) { return false; }

    public void onBackPressed() {
        if (mCancelable) cancel();
    }

    public boolean onKeyShortcut(int keyCode, KeyEvent event) { return false; }

    public boolean onTouchEvent(MotionEvent event) {
        if (mCancelable && mShowing && mWindow.shouldCloseOnTouch(mContext, event)) {
            cancel();
            return true;
        }
        return false;
    }

    public boolean onTrackballEvent(MotionEvent event) { return false; }

    public boolean onGenericMotionEvent(MotionEvent event) { return false; }

    public void onWindowAttributesChanged(WindowManager.LayoutParams params) {
        if (mDecor != null) mWindowManager.updateViewLayout(mDecor, params);
    }

    public void onContentChanged() {}

    public void onWindowFocusChanged(boolean hasFocus) {}

    public void onAttachedToWindow() {}

    public void onDetachedFromWindow() {}

    public boolean dispatchKeyEvent(KeyEvent event) {
        if ((mOnKeyListener != null) && (mOnKeyListener.onKey(this, event.getKeyCode(), event))) return true;
        if (mWindow.superDispatchKeyEvent(event)) return true;
        return event.dispatch(this, mDecor != null ? mDecor.getKeyDispatcherState() : null, this);
    }

    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        if (mWindow.superDispatchKeyShortcutEvent(event)) return true;
        return onKeyShortcut(event.getKeyCode(), event);
    }

    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mWindow.superDispatchTouchEvent(ev)) return true;
        return onTouchEvent(ev);
    }

    public boolean dispatchTrackballEvent(MotionEvent ev) {
        if (mWindow.superDispatchTrackballEvent(ev)) return true;
        return onTrackballEvent(ev);
    }

    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (mWindow.superDispatchGenericMotionEvent(ev)) return true;
        return onGenericMotionEvent(ev);
    }

    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        event.setClassName(getClass().getName());
        event.setPackageName(mContext.getPackageName());
        return false;
    }

    public View onCreatePanelView(int featureId) { return null; }

    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL) return onCreateOptionsMenu(menu);
        return false;
    }

    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL && menu != null) {
            return onPrepareOptionsMenu(menu) && menu.hasVisibleItems();
        }
        return true;
    }

    public boolean onMenuOpened(int featureId, Menu menu) { return true; }

    public boolean onMenuItemSelected(int featureId, MenuItem item) { return false; }

    public void onPanelClosed(int featureId, Menu menu) {}

    public boolean onCreateOptionsMenu(Menu menu) { return true; }

    public boolean onPrepareOptionsMenu(Menu menu) { return true; }

    public boolean onOptionsItemSelected(MenuItem item) { return false; }

    public void onOptionsMenuClosed(Menu menu) {}

    public void openOptionsMenu() { mWindow.openPanel(Window.FEATURE_OPTIONS_PANEL, null); }

    public void closeOptionsMenu() { mWindow.closePanel(Window.FEATURE_OPTIONS_PANEL); }

    public void invalidateOptionsMenu() { mWindow.invalidatePanelMenu(Window.FEATURE_OPTIONS_PANEL); }

    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {}

    public void registerForContextMenu(View view) { view.setOnCreateContextMenuListener(this); }

    public void unregisterForContextMenu(View view) { view.setOnCreateContextMenuListener(null); }

    public void openContextMenu(View view) { view.showContextMenu(); }

    public boolean onContextItemSelected(MenuItem item) { return false; }

    public void onContextMenuClosed(Menu menu) {}

    public boolean onSearchRequested(SearchEvent searchEvent) {
        mSearchEvent = searchEvent;
        return onSearchRequested();
    }

    public boolean onSearchRequested() { return false; }

    public final SearchEvent getSearchEvent() { return mSearchEvent; }

    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) { return null; }

    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
        try {
            mActionModeTypeStarting = type;
            return onWindowStartingActionMode(callback);
        } finally {
            mActionModeTypeStarting = ActionMode.TYPE_PRIMARY;
        }
    }

    public void onActionModeStarted(ActionMode mode) {
        if (mode != null && mode.getType() == ActionMode.TYPE_PRIMARY) mActionMode = mode;
    }

    public void onActionModeFinished(ActionMode mode) {
        if (mode == mActionMode) mActionMode = null;
    }

    public void takeKeyEvents(boolean get) { mWindow.takeKeyEvents(get); }

    public final boolean requestWindowFeature(int featureId) { return getWindow().requestFeature(featureId); }

    public final void setFeatureDrawableResource(int featureId, int resId) {
        getWindow().setFeatureDrawableResource(featureId, resId);
    }

    public final void setFeatureDrawableUri(int featureId, Uri uri) { getWindow().setFeatureDrawableUri(featureId, uri); }

    public final void setFeatureDrawable(int featureId, Drawable drawable) {
        getWindow().setFeatureDrawable(featureId, drawable);
    }

    public final void setFeatureDrawableAlpha(int featureId, int alpha) {
        getWindow().setFeatureDrawableAlpha(featureId, alpha);
    }

    public LayoutInflater getLayoutInflater() { return getWindow().getLayoutInflater(); }

    public void setCancelable(boolean flag) { mCancelable = flag; }

    public void setCanceledOnTouchOutside(boolean cancel) {
        if (cancel && !mCancelable) mCancelable = true;
        mWindow.setCloseOnTouchOutside(cancel);
    }

    public void cancel() {
        if (!mCanceled && mCancelMessage != null) {
            mCanceled = true;
            Message.obtain(mCancelMessage).sendToTarget();
        }
        dismiss();
    }

    public void setOnCancelListener(OnCancelListener listener) {
        if (mCancelAndDismissTaken != null) {
            throw new IllegalStateException("OnCancelListener is already taken by " + mCancelAndDismissTaken
                    + " and can not be replaced.");
        }
        if (listener != null) mCancelMessage = mListenersHandler.obtainMessage(CANCEL, listener);
        else mCancelMessage = null;
    }

    public void setCancelMessage(Message msg) { mCancelMessage = msg; }

    public void setOnDismissListener(OnDismissListener listener) {
        if (mCancelAndDismissTaken != null) {
            throw new IllegalStateException("OnDismissListener is already taken by " + mCancelAndDismissTaken
                    + " and can not be replaced.");
        }
        if (listener != null) mDismissMessage = mListenersHandler.obtainMessage(DISMISS, listener);
        else mDismissMessage = null;
    }

    public void setOnShowListener(OnShowListener listener) {
        if (listener != null) mShowMessage = mListenersHandler.obtainMessage(SHOW, listener);
        else mShowMessage = null;
    }

    public void setDismissMessage(Message msg) { mDismissMessage = msg; }

    /** Hidden AOSP API. */
    public boolean takeCancelAndDismissListeners(String msg, OnCancelListener cancel, OnDismissListener dismiss) {
        if (mCancelAndDismissTaken != null) {
            mCancelAndDismissTaken = null;
        } else if (mCancelMessage != null || mDismissMessage != null) {
            return false;
        }
        setOnCancelListener(cancel);
        setOnDismissListener(dismiss);
        mCancelAndDismissTaken = msg;
        return true;
    }

    public final void setVolumeControlStream(int streamType) { getWindow().setVolumeControlStream(streamType); }

    public final int getVolumeControlStream() { return getWindow().getVolumeControlStream(); }

    public void setOnKeyListener(OnKeyListener onKeyListener) { mOnKeyListener = onKeyListener; }

    private static final class ListenersHandler extends Handler {
        private final WeakReference<DialogInterface> mDialog;

        public ListenersHandler(Dialog dialog) {
            super(Looper.getMainLooper());
            mDialog = new WeakReference<DialogInterface>(dialog);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DISMISS:
                    ((OnDismissListener) msg.obj).onDismiss(mDialog.get());
                    break;
                case CANCEL:
                    ((OnCancelListener) msg.obj).onCancel(mDialog.get());
                    break;
                case SHOW:
                    ((OnShowListener) msg.obj).onShow(mDialog.get());
                    break;
                default:
                    break;
            }
        }
    }
}
