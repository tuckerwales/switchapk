package com.android.internal.policy;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.Window;
import android.widget.FrameLayout;
import com.android.internal.util.InternalRes;
import com.android.internal.view.FloatingActionMode;
import com.android.internal.view.StandaloneActionMode;
import com.android.internal.widget.ActionBarContextView;

/**
 * framework-internal. Root view of a PhoneWindow (AOSP DecorView): routes input
 * through the Window.Callback (Activity/Dialog), draws the window background
 * and applies the minimum width of floating windows.
 */
public class DecorView extends FrameLayout {
    private final PhoneWindow mWindow;
    private final Rect mFramePadding = new Rect();
    private final Rect mBackgroundPadding = new Rect();

    /** The primary action mode, whether shown by the action bar or by mPrimaryActionModeView. */
    ActionMode mPrimaryActionMode;
    private ActionBarContextView mPrimaryActionModeView;
    /** The floating action mode (selection toolbar). */
    private ActionMode mFloatingActionMode;

    DecorView(Context context, PhoneWindow window) {
        super(context);
        mWindow = window;
    }

    /** framework-internal. */
    public PhoneWindow getWindow() { return mWindow; }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        final int keyCode = event.getKeyCode();
        final int action = event.getAction();
        final boolean isDown = action == KeyEvent.ACTION_DOWN;
        if (!mWindow.isDestroyed()) {
            final Window.Callback cb = mWindow.getCallback();
            final boolean handled = cb != null ? cb.dispatchKeyEvent(event) : super.dispatchKeyEvent(event);
            if (handled) return true;
        }
        return isDown ? mWindow.onKeyDown(-1, keyCode, event) : mWindow.onKeyUp(-1, keyCode, event);
    }

    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent ev) {
        final Window.Callback cb = mWindow.getCallback();
        return cb != null && !mWindow.isDestroyed() ? cb.dispatchKeyShortcutEvent(ev)
                : super.dispatchKeyShortcutEvent(ev);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final Window.Callback cb = mWindow.getCallback();
        return cb != null && !mWindow.isDestroyed() ? cb.dispatchTouchEvent(ev) : super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent ev) {
        final Window.Callback cb = mWindow.getCallback();
        return cb != null && !mWindow.isDestroyed() ? cb.dispatchTrackballEvent(ev) : super.dispatchTrackballEvent(ev);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        final Window.Callback cb = mWindow.getCallback();
        return cb != null && !mWindow.isDestroyed() ? cb.dispatchGenericMotionEvent(ev)
                : super.dispatchGenericMotionEvent(ev);
    }

    public boolean superDispatchKeyEvent(KeyEvent event) {
        // Back cancels a floating toolbar first, then a primary action mode.
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && mFloatingActionMode != null) {
            if (event.getAction() == KeyEvent.ACTION_UP) mFloatingActionMode.finish();
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && mPrimaryActionMode != null) {
            if (event.getAction() == KeyEvent.ACTION_UP) mPrimaryActionMode.finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    public boolean superDispatchKeyShortcutEvent(KeyEvent event) { return super.dispatchKeyShortcutEvent(event); }

    public boolean superDispatchTouchEvent(MotionEvent event) { return super.dispatchTouchEvent(event); }

    public boolean superDispatchTrackballEvent(MotionEvent event) { return super.dispatchTrackballEvent(event); }

    public boolean superDispatchGenericMotionEvent(MotionEvent event) { return super.dispatchGenericMotionEvent(event); }

    @Override
    public boolean onTouchEvent(MotionEvent event) { return false; }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        final Window.Callback cb = mWindow.getCallback();
        if (cb != null && !mWindow.isDestroyed()) cb.onWindowFocusChanged(hasWindowFocus);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        final Window.Callback cb = mWindow.getCallback();
        if (cb != null && !mWindow.isDestroyed()) cb.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        final Window.Callback cb = mWindow.getCallback();
        if (cb != null) cb.onDetachedFromWindow();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) { super.onConfigurationChanged(newConfig); }

    void setWindowBackground(Drawable drawable) {
        if (getBackground() != drawable) {
            setBackgroundDrawable(drawable);
            if (drawable != null) drawable.getPadding(mBackgroundPadding);
            else mBackgroundPadding.setEmpty();
        }
        mWindow.updateFormatForBackground(drawable);
    }

    void setWindowFrame(Drawable drawable) {
        if (getForeground() != drawable) {
            setForeground(drawable);
            if (drawable != null) drawable.getPadding(mFramePadding);
            else mFramePadding.setEmpty();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        final boolean isPortrait = metrics.widthPixels < metrics.heightPixels;
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        boolean fixedWidth = false;
        if (widthMode == MeasureSpec.AT_MOST) {
            final TypedValue tvw = isPortrait ? mWindow.mFixedWidthMinor : mWindow.mFixedWidthMajor;
            final int w = resolveDimension(tvw, metrics, metrics.widthPixels);
            if (w > 0) {
                final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(w, widthSize), MeasureSpec.EXACTLY);
                fixedWidth = true;
            }
        }
        if (heightMode == MeasureSpec.AT_MOST) {
            final TypedValue tvh = isPortrait ? mWindow.mFixedHeightMajor : mWindow.mFixedHeightMinor;
            final int h = resolveDimension(tvh, metrics, metrics.heightPixels);
            if (h > 0) {
                final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(h, heightSize), MeasureSpec.EXACTLY);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        boolean measure = false;
        widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        if (!fixedWidth && widthMode == MeasureSpec.AT_MOST) {
            final TypedValue tv = isPortrait ? mWindow.mMinWidthMinor : mWindow.mMinWidthMajor;
            final int min = resolveDimension(tv, metrics, metrics.widthPixels);
            if (width < min) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(min, MeasureSpec.EXACTLY);
                measure = true;
            }
        }
        if (measure) super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private static int resolveDimension(TypedValue tv, DisplayMetrics metrics, int base) {
        if (tv == null || tv.type == TypedValue.TYPE_NULL) return 0;
        if (tv.type == TypedValue.TYPE_DIMENSION) return (int) tv.getDimension(metrics);
        if (tv.type == TypedValue.TYPE_FRACTION) return (int) tv.getFraction(base, base);
        return 0;
    }

    @Override
    public String toString() { return "DecorView@" + Integer.toHexString(hashCode()); }

    // ---------------------------------------------------------------- action modes (AOSP DecorView)

    @Override
    public ActionMode startActionModeForChild(View originalView, ActionMode.Callback callback) {
        return startActionModeForChild(originalView, callback, ActionMode.TYPE_PRIMARY);
    }

    @Override
    public ActionMode startActionModeForChild(View child, ActionMode.Callback callback, int type) {
        return startActionMode(child, callback, type);
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback) { return startActionMode(callback, ActionMode.TYPE_PRIMARY); }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback, int type) { return startActionMode(this, callback, type); }

    private ActionMode startActionMode(View originatingView, ActionMode.Callback callback, int type) {
        final ActionModeCallbackWrapper wrappedCallback = new ActionModeCallbackWrapper(callback);
        ActionMode mode = null;
        final Window.Callback cb = mWindow.getCallback();
        if (cb != null && !mWindow.isDestroyed()) mode = cb.onWindowStartingActionMode(wrappedCallback, type);
        if (mode != null) {
            if (mode.getType() == ActionMode.TYPE_PRIMARY) {
                cleanupPrimaryActionMode();
                mPrimaryActionMode = mode;
            } else if (mode.getType() == ActionMode.TYPE_FLOATING) {
                cleanupFloatingActionMode();
                mFloatingActionMode = mode;
            }
        } else {
            mode = createActionMode(type, wrappedCallback, originatingView);
            if (mode != null && wrappedCallback.onCreateActionMode(mode, mode.getMenu())) {
                if (mode.getType() == ActionMode.TYPE_FLOATING) setHandledFloatingActionMode(mode);
                else setHandledPrimaryActionMode(mode);
            } else {
                mode = null;
            }
        }
        if (mode != null && cb != null && !mWindow.isDestroyed()) cb.onActionModeStarted(mode);
        return mode;
    }

    private void cleanupPrimaryActionMode() {
        if (mPrimaryActionMode != null) {
            mPrimaryActionMode.finish();
            mPrimaryActionMode = null;
        }
        if (mPrimaryActionModeView != null) mPrimaryActionModeView.killMode();
    }

    private ActionMode createActionMode(int type, ActionMode.Callback2 callback, View originatingView) {
        if (type == ActionMode.TYPE_FLOATING) return createFloatingActionMode(originatingView, callback);
        if (type == ActionMode.TYPE_PRIMARY) return createStandaloneActionMode(callback);
        return null;
    }

    private ActionMode createFloatingActionMode(View originatingView, ActionMode.Callback2 callback) {
        if (originatingView == null) return null;
        cleanupFloatingActionMode();
        return new FloatingActionMode(getContext(), callback, originatingView);
    }

    private void cleanupFloatingActionMode() {
        if (mFloatingActionMode != null) {
            mFloatingActionMode.finish();
            mFloatingActionMode = null;
        }
    }

    private void setHandledFloatingActionMode(ActionMode mode) {
        mFloatingActionMode = mode;
        mode.invalidate();
    }

    private ActionMode createStandaloneActionMode(ActionMode.Callback callback) {
        cleanupPrimaryActionMode();
        if (mPrimaryActionModeView == null || !mPrimaryActionModeView.isAttachedToWindow()) {
            final View stub = findViewById(InternalRes.viewId("action_mode_bar_stub"));
            if (stub instanceof ViewStub) {
                final View inflated = ((ViewStub) stub).inflate();
                if (inflated instanceof ActionBarContextView) mPrimaryActionModeView = (ActionBarContextView) inflated;
            }
        }
        if (mPrimaryActionModeView != null) {
            mPrimaryActionModeView.killMode();
            return new StandaloneActionMode(mPrimaryActionModeView.getContext(), mPrimaryActionModeView, callback, true);
        }
        return null;
    }

    private void setHandledPrimaryActionMode(ActionMode mode) {
        mPrimaryActionMode = mode;
        mode.invalidate();
        mPrimaryActionModeView.initForMode(mode);
        mPrimaryActionModeView.setVisibility(View.VISIBLE);
        requestFitSystemWindows();
    }

    /** Clears the decor's mode when it ends and tells the window callback (AOSP ActionModeCallback2Wrapper). */
    private class ActionModeCallbackWrapper extends ActionMode.Callback2 {
        private final ActionMode.Callback mWrapped;

        ActionModeCallbackWrapper(ActionMode.Callback wrapped) { mWrapped = wrapped; }

        public boolean onCreateActionMode(ActionMode mode, Menu menu) { return mWrapped.onCreateActionMode(mode, menu); }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            requestFitSystemWindows();
            return mWrapped.onPrepareActionMode(mode, menu);
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) { return mWrapped.onActionItemClicked(mode, item); }

        public void onDestroyActionMode(ActionMode mode) {
            mWrapped.onDestroyActionMode(mode);
            if (mode == mPrimaryActionMode) {
                if (mPrimaryActionModeView != null) {
                    mPrimaryActionModeView.setVisibility(GONE);
                    mPrimaryActionModeView.killMode();
                }
                mPrimaryActionMode = null;
            }
            if (mode == mFloatingActionMode) mFloatingActionMode = null;
            final Window.Callback cb = mWindow.getCallback();
            if (cb != null && !mWindow.isDestroyed()) cb.onActionModeFinished(mode);
            requestFitSystemWindows();
        }

        @Override
        public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
            if (mWrapped instanceof ActionMode.Callback2) {
                ((ActionMode.Callback2) mWrapped).onGetContentRect(mode, view, outRect);
            } else {
                super.onGetContentRect(mode, view, outRect);
            }
        }
    }

    @Override
    public boolean showContextMenuForChild(View originalView) { return mWindow.showContextMenuForChild(originalView); }

    @Override
    public boolean showContextMenuForChild(View originalView, float x, float y) {
        return mWindow.showContextMenuForChild(originalView);
    }
}
