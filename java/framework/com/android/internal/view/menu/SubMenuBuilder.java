package com.android.internal.view.menu;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;

/** framework-internal. A submenu: a MenuBuilder attached to an item of its parent. */
public class SubMenuBuilder extends MenuBuilder implements SubMenu {
    private final MenuBuilder mParentMenu;
    private final MenuItemImpl mItem;
    private CharSequence mHeaderTitle;
    private Drawable mHeaderIcon;
    private View mHeaderView;

    public SubMenuBuilder(Context context, MenuBuilder parentMenu, MenuItemImpl item) {
        super(context);
        mParentMenu = parentMenu;
        mItem = item;
    }

    @Override
    public void setQwertyMode(boolean isQwerty) { mParentMenu.setQwertyMode(isQwerty); }

    @Override
    public boolean isQwertyMode() { return mParentMenu.isQwertyMode(); }

    public Menu getParentMenu() { return mParentMenu; }

    public MenuItem getItem() { return mItem; }

    @Override
    public MenuBuilder getRootMenu() { return mParentMenu.getRootMenu(); }

    @Override
    boolean dispatchMenuItemSelected(MenuBuilder menu, MenuItem item) {
        return super.dispatchMenuItemSelected(menu, item) || mParentMenu.dispatchMenuItemSelected(menu, item);
    }

    public SubMenu setIcon(Drawable icon) {
        mItem.setIcon(icon);
        return this;
    }

    public SubMenu setIcon(int iconRes) {
        mItem.setIcon(iconRes);
        return this;
    }

    public SubMenu setHeaderIcon(Drawable icon) {
        mHeaderIcon = icon;
        return this;
    }

    public SubMenu setHeaderIcon(int iconRes) {
        mHeaderIcon = getContext().getDrawable(iconRes);
        return this;
    }

    public SubMenu setHeaderTitle(CharSequence title) {
        mHeaderTitle = title;
        return this;
    }

    public SubMenu setHeaderTitle(int titleRes) {
        mHeaderTitle = getContext().getString(titleRes);
        return this;
    }

    public SubMenu setHeaderView(View view) {
        mHeaderView = view;
        return this;
    }

    public void clearHeader() {
        mHeaderTitle = null;
        mHeaderIcon = null;
        mHeaderView = null;
    }

    public CharSequence getHeaderTitle() { return mHeaderTitle; }
}
