package com.android.internal.view.menu;

import android.graphics.drawable.Drawable;

/** framework-internal. A view that shows a menu or one menu item (AOSP MenuView). */
public interface MenuView {
    void initialize(MenuBuilder menu);

    int getWindowAnimations();

    interface ItemView {
        void initialize(MenuItemImpl itemData, int menuType);

        MenuItemImpl getItemData();

        void setTitle(CharSequence title);

        void setEnabled(boolean enabled);

        void setCheckable(boolean checkable);

        void setChecked(boolean checked);

        void setShortcut(boolean showShortcut, char shortcutKey);

        void setIcon(Drawable icon);

        boolean prefersCondensedTitle();

        boolean showsIcon();
    }
}
