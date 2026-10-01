package android.view;

import android.view.accessibility.AccessibilityEvent;

/**
 * Hidden AOSP API. A Window.Callback that forwards every call to another one,
 * for wrappers that only intercept a few (ToolbarActionBar).
 */
public class WindowCallbackWrapper implements Window.Callback {
    private final Window.Callback mWrapped;

    public WindowCallbackWrapper(Window.Callback wrapped) {
        if (wrapped == null) throw new IllegalArgumentException("Window callback may not be null");
        mWrapped = wrapped;
    }

    public boolean dispatchKeyEvent(KeyEvent event) { return mWrapped.dispatchKeyEvent(event); }

    public boolean dispatchKeyShortcutEvent(KeyEvent event) { return mWrapped.dispatchKeyShortcutEvent(event); }

    public boolean dispatchTouchEvent(MotionEvent event) { return mWrapped.dispatchTouchEvent(event); }

    public boolean dispatchTrackballEvent(MotionEvent event) { return mWrapped.dispatchTrackballEvent(event); }

    public boolean dispatchGenericMotionEvent(MotionEvent event) { return mWrapped.dispatchGenericMotionEvent(event); }

    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        return mWrapped.dispatchPopulateAccessibilityEvent(event);
    }

    public View onCreatePanelView(int featureId) { return mWrapped.onCreatePanelView(featureId); }

    public boolean onCreatePanelMenu(int featureId, Menu menu) { return mWrapped.onCreatePanelMenu(featureId, menu); }

    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        return mWrapped.onPreparePanel(featureId, view, menu);
    }

    public boolean onMenuOpened(int featureId, Menu menu) { return mWrapped.onMenuOpened(featureId, menu); }

    public boolean onMenuItemSelected(int featureId, MenuItem item) { return mWrapped.onMenuItemSelected(featureId, item); }

    public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) { mWrapped.onWindowAttributesChanged(attrs); }

    public void onContentChanged() { mWrapped.onContentChanged(); }

    public void onWindowFocusChanged(boolean hasFocus) { mWrapped.onWindowFocusChanged(hasFocus); }

    public void onAttachedToWindow() { mWrapped.onAttachedToWindow(); }

    public void onDetachedFromWindow() { mWrapped.onDetachedFromWindow(); }

    public void onPanelClosed(int featureId, Menu menu) { mWrapped.onPanelClosed(featureId, menu); }

    public boolean onSearchRequested(SearchEvent searchEvent) { return mWrapped.onSearchRequested(searchEvent); }

    public boolean onSearchRequested() { return mWrapped.onSearchRequested(); }

    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
        return mWrapped.onWindowStartingActionMode(callback);
    }

    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
        return mWrapped.onWindowStartingActionMode(callback, type);
    }

    public void onActionModeStarted(ActionMode mode) { mWrapped.onActionModeStarted(mode); }

    public void onActionModeFinished(ActionMode mode) { mWrapped.onActionModeFinished(mode); }

    public void onPointerCaptureChanged(boolean hasCapture) { mWrapped.onPointerCaptureChanged(hasCapture); }
}
