package com.android.internal.view;

import android.content.Context;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import com.android.internal.view.menu.MenuBuilder;
import com.android.internal.widget.ActionBarContextView;
import java.lang.ref.WeakReference;

/**
 * framework-internal. Port of AOSP StandaloneActionMode: a primary action
 * mode shown in the window's own context bar when there is no action bar.
 */
public class StandaloneActionMode extends ActionMode implements MenuBuilder.Callback {
    private final Context mContext;
    private final ActionBarContextView mContextView;
    private final ActionMode.Callback mCallback;
    private WeakReference<View> mCustomView;
    private boolean mFinished;
    private final boolean mFocusable;
    private final MenuBuilder mMenu;

    public StandaloneActionMode(Context context, ActionBarContextView view, ActionMode.Callback callback,
            boolean isFocusable) {
        mContext = context;
        mContextView = view;
        mCallback = callback;
        mMenu = new MenuBuilder(view.getContext()).setDefaultShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        mMenu.setCallback(this);
        mFocusable = isFocusable;
    }

    @Override
    public void setTitle(CharSequence title) { mContextView.setTitle(title); }

    @Override
    public void setSubtitle(CharSequence subtitle) { mContextView.setSubtitle(subtitle); }

    @Override
    public void setTitle(int resId) { setTitle(resId != 0 ? mContext.getString(resId) : null); }

    @Override
    public void setSubtitle(int resId) { setSubtitle(resId != 0 ? mContext.getString(resId) : null); }

    @Override
    public void setTitleOptionalHint(boolean titleOptional) {
        super.setTitleOptionalHint(titleOptional);
        mContextView.setTitleOptional(titleOptional);
    }

    @Override
    public boolean isTitleOptional() { return mContextView.isTitleOptional(); }

    @Override
    public void setCustomView(View view) {
        mContextView.setCustomView(view);
        mCustomView = view != null ? new WeakReference<View>(view) : null;
    }

    @Override
    public void invalidate() { mCallback.onPrepareActionMode(this, mMenu); }

    @Override
    public void finish() {
        if (mFinished) return;
        mFinished = true;
        mCallback.onDestroyActionMode(this);
    }

    @Override
    public Menu getMenu() { return mMenu; }

    @Override
    public CharSequence getTitle() { return mContextView.getTitle(); }

    @Override
    public CharSequence getSubtitle() { return mContextView.getSubtitle(); }

    @Override
    public View getCustomView() { return mCustomView != null ? mCustomView.get() : null; }

    @Override
    public MenuInflater getMenuInflater() { return new MenuInflater(mContextView.getContext()); }

    public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) { return mCallback.onActionItemClicked(this, item); }

    public void onMenuModeChange(MenuBuilder menu) {
        invalidate();
        mContextView.showOverflowMenu();
    }

    /** Hidden AOSP API. */
    public boolean isUiFocusable() { return mFocusable; }
}
