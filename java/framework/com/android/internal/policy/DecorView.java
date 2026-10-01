package com.android.internal.policy;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;

/**
 * framework-internal. Root view of a PhoneWindow (AOSP DecorView): routes input
 * through the Window.Callback (Activity/Dialog), draws the window background
 * and applies the minimum width of floating windows.
 */
public class DecorView extends FrameLayout {
    private final PhoneWindow mWindow;
    private final Rect mFramePadding = new Rect();
    private final Rect mBackgroundPadding = new Rect();

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

    public boolean superDispatchKeyEvent(KeyEvent event) { return super.dispatchKeyEvent(event); }

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

    @Override
    public boolean showContextMenuForChild(View originalView) { return mWindow.showContextMenuForChild(originalView); }

    @Override
    public boolean showContextMenuForChild(View originalView, float x, float y) {
        return mWindow.showContextMenuForChild(originalView);
    }
}
