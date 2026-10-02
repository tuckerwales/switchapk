package com.android.internal.view;

import android.content.Context;
import android.graphics.Rect;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import com.android.internal.view.menu.MenuBuilder;
import com.android.internal.widget.FloatingToolbar;
import java.lang.ref.WeakReference;

/**
 * framework-internal. Port of AOSP FloatingActionMode: a {@link ActionMode#TYPE_FLOATING}
 * shown in a {@link FloatingToolbar} next to the content rect from
 * {@link Callback2#onGetContentRect}. TextView opens one for a text selection.
 */
public class FloatingActionMode extends ActionMode implements MenuBuilder.Callback {
    private final Context mContext;
    private final ActionMode.Callback2 mCallback;
    private final WeakReference<View> mOriginatingView;
    private final MenuBuilder mMenu;
    private final FloatingToolbar mToolbar;
    private final Rect mContentRect = new Rect();
    private WeakReference<View> mCustomView;
    private CharSequence mTitle;
    private CharSequence mSubtitle;
    private boolean mFinished;
    private boolean mHidden;
    private boolean mWaitingForLayout;

    public FloatingActionMode(Context context, ActionMode.Callback2 callback, View originatingView) {
        mContext = context;
        mCallback = callback;
        mOriginatingView = new WeakReference<View>(originatingView);
        mMenu = new MenuBuilder(context).setDefaultShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        mMenu.setCallback(this);
        mToolbar = new FloatingToolbar(context);
        setType(TYPE_FLOATING);
    }

    @Override
    public void setTitle(CharSequence title) { mTitle = title; }

    @Override
    public void setSubtitle(CharSequence subtitle) { mSubtitle = subtitle; }

    @Override
    public void setTitle(int resId) { setTitle(resId != 0 ? mContext.getString(resId) : null); }

    @Override
    public void setSubtitle(int resId) { setSubtitle(resId != 0 ? mContext.getString(resId) : null); }

    @Override
    public void setCustomView(View view) { mCustomView = view != null ? new WeakReference<View>(view) : null; }

    @Override
    public void invalidate() {
        mCallback.onPrepareActionMode(this, mMenu);
        invalidateContentRect();
    }

    @Override
    public void invalidateContentRect() {
        mHidden = false;
        reposition();
    }

    @Override
    public void hide(long duration) {
        mHidden = true;
        mToolbar.dismiss();
        if (duration < 0) return;
        View view = mOriginatingView.get();
        if (view == null) return;
        view.postDelayed(new Runnable() {
            public void run() {
                if (mFinished) return;
                mHidden = false;
                reposition();
            }
        }, duration);
    }

    @Override
    public void finish() {
        if (mFinished) return;
        mFinished = true;
        mToolbar.dismiss();
        mCallback.onDestroyActionMode(this);
    }

    @Override
    public Menu getMenu() { return mMenu; }

    @Override
    public CharSequence getTitle() { return mTitle; }

    @Override
    public CharSequence getSubtitle() { return mSubtitle; }

    @Override
    public View getCustomView() { return mCustomView != null ? mCustomView.get() : null; }

    @Override
    public MenuInflater getMenuInflater() { return new MenuInflater(mContext); }

    public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
        return mCallback.onActionItemClicked(this, item);
    }

    public void onMenuModeChange(MenuBuilder menu) { invalidate(); }

    private void reposition() {
        if (mFinished || mHidden) return;
        final View view = mOriginatingView.get();
        if (view == null) {
            mToolbar.dismiss();
            return;
        }
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            if (mWaitingForLayout) return;
            mWaitingForLayout = true;
            view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                public void onGlobalLayout() {
                    View current = mOriginatingView.get();
                    if (current != null) current.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    mWaitingForLayout = false;
                    reposition();
                }
            });
            return;
        }
        mContentRect.setEmpty();
        mCallback.onGetContentRect(this, view, mContentRect);
        if (mContentRect.isEmpty()) mContentRect.set(0, 0, view.getWidth(), view.getHeight());
        mToolbar.setMenu(mMenu);
        mToolbar.show(view, mContentRect);
    }
}
