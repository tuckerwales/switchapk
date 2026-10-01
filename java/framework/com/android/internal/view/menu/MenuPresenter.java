package com.android.internal.view.menu;

import android.content.Context;
import android.os.Parcelable;
import android.view.ViewGroup;

/** framework-internal. Port of AOSP MenuPresenter: presents a MenuBuilder in some view. */
public interface MenuPresenter {
    interface Callback {
        void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing);

        boolean onOpenSubMenu(MenuBuilder subMenu);
    }

    void initForMenu(Context context, MenuBuilder menu);

    MenuView getMenuView(ViewGroup root);

    void updateMenuView(boolean cleared);

    void setCallback(Callback cb);

    boolean onSubMenuSelected(SubMenuBuilder subMenu);

    void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing);

    boolean flagActionItems();

    boolean expandItemActionView(MenuBuilder menu, MenuItemImpl item);

    boolean collapseItemActionView(MenuBuilder menu, MenuItemImpl item);

    int getId();

    Parcelable onSaveInstanceState();

    void onRestoreInstanceState(Parcelable state);
}
