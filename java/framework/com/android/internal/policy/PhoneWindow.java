package com.android.internal.policy;

import android.content.Context;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

/** framework-internal. One decor view and one content child. Themes and inflation are TODO(WS1). */
public class PhoneWindow extends Window {
    private static final String TAG = "PhoneWindow";
    private static boolean sLoggedInflate;
    private final DecorView mDecor;
    private View mContent;

    public PhoneWindow(Context context) {
        super(context);
        mDecor = new DecorView(context);
        getAttributes().type = WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
    }

    @Override
    public void setContentView(View view) {
        setContentView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        mDecor.removeAllViews();
        mDecor.addView(view, params);
        mContent = view;
    }

    @Override
    public void setContentView(int layoutResID) {
        if (!sLoggedInflate) {
            sLoggedInflate = true;
            Log.w(TAG, "setContentView(int) is not implemented");
        }
    }

    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        mDecor.addView(view, params);
        mContent = view;
    }

    @Override
    public View getDecorView() { return mDecor; }

    @Override
    public View peekDecorView() { return mDecor; }

    @Override
    public View getCurrentFocus() { return mContent != null ? mContent : mDecor; }

    @Override
    public boolean superDispatchKeyEvent(KeyEvent event) { return mDecor.dispatchKeyEvent(event); }

    @Override
    public boolean superDispatchTouchEvent(MotionEvent event) { return mDecor.dispatchTouchEvent(event); }

    private static class DecorView extends ViewGroup {
        DecorView(Context context) { super(context); }
    }
}
