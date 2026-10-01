package com.android.internal.view.menu;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.View;

/** framework-internal. Context menu model with a header (port of AOSP ContextMenuBuilder). */
public class ContextMenuBuilder extends MenuBuilder implements ContextMenu {
    private CharSequence mHeaderTitle;
    private Drawable mHeaderIcon;
    private View mHeaderView;

    public ContextMenuBuilder(Context context) { super(context); }

    public ContextMenu setHeaderIcon(Drawable icon) {
        mHeaderIcon = icon;
        return this;
    }

    public ContextMenu setHeaderIcon(int iconRes) {
        mHeaderIcon = getContext().getDrawable(iconRes);
        return this;
    }

    public ContextMenu setHeaderTitle(CharSequence title) {
        mHeaderTitle = title;
        return this;
    }

    public ContextMenu setHeaderTitle(int titleRes) {
        mHeaderTitle = getContext().getText(titleRes);
        return this;
    }

    public ContextMenu setHeaderView(View view) {
        mHeaderView = view;
        return this;
    }

    public void clearHeader() {
        mHeaderTitle = null;
        mHeaderIcon = null;
        mHeaderView = null;
    }

    public CharSequence getHeaderTitle() { return mHeaderTitle; }

    public Drawable getHeaderIcon() { return mHeaderIcon; }

    public View getHeaderView() { return mHeaderView; }

    /** Builds the menu from {@code originalView} and its parents, then shows it as a dialog. */
    public MenuDialogHelper showDialog(View originalView, IBinder token) {
        if (originalView != null) originalView.createContextMenu(this);
        if (getVisibleItems().size() > 0) {
            MenuDialogHelper helper = new MenuDialogHelper(this);
            helper.show(token);
            return helper;
        }
        return null;
    }
}
