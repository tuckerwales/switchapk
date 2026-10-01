package com.android.internal.view.menu;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;

/** framework-internal. Port of AOSP BaseMenuPresenter: keeps a MenuView's children in step with items. */
public abstract class BaseMenuPresenter implements MenuPresenter {
    protected Context mSystemContext;
    protected Context mContext;
    protected MenuBuilder mMenu;
    protected LayoutInflater mSystemInflater;
    protected LayoutInflater mInflater;
    private Callback mCallback;
    private final int mMenuLayoutRes;
    private final int mItemLayoutRes;
    protected MenuView mMenuView;
    private int mId;

    public BaseMenuPresenter(Context context, int menuLayoutRes, int itemLayoutRes) {
        mSystemContext = context;
        mSystemInflater = LayoutInflater.from(context);
        mMenuLayoutRes = menuLayoutRes;
        mItemLayoutRes = itemLayoutRes;
    }

    @Override
    public void initForMenu(Context context, MenuBuilder menu) {
        mContext = context;
        mInflater = LayoutInflater.from(mContext);
        mMenu = menu;
    }

    @Override
    public MenuView getMenuView(ViewGroup root) {
        if (mMenuView == null) {
            mMenuView = (MenuView) mSystemInflater.inflate(mMenuLayoutRes, root, false);
            mMenuView.initialize(mMenu);
            updateMenuView(true);
        }
        return mMenuView;
    }

    @Override
    public void updateMenuView(boolean cleared) {
        final ViewGroup parent = (ViewGroup) mMenuView;
        if (parent == null) return;
        int childIndex = 0;
        if (mMenu != null) {
            mMenu.flagActionItems();
            ArrayList<MenuItemImpl> visibleItems = mMenu.getVisibleItems();
            final int itemCount = visibleItems.size();
            for (int i = 0; i < itemCount; i++) {
                MenuItemImpl item = visibleItems.get(i);
                if (shouldIncludeItem(childIndex, item)) {
                    final View convertView = parent.getChildAt(childIndex);
                    final MenuItemImpl oldItem = convertView instanceof MenuView.ItemView
                            ? ((MenuView.ItemView) convertView).getItemData() : null;
                    final View itemView = getItemView(item, convertView, parent);
                    if (item != oldItem) {
                        itemView.setPressed(false);
                        itemView.jumpDrawablesToCurrentState();
                    }
                    if (itemView != convertView) addItemView(itemView, childIndex);
                    childIndex++;
                }
            }
        }
        while (childIndex < parent.getChildCount()) {
            if (!filterLeftoverView(parent, childIndex)) childIndex++;
        }
    }

    protected void addItemView(View itemView, int childIndex) {
        final ViewGroup currentParent = (ViewGroup) itemView.getParent();
        if (currentParent != null) currentParent.removeView(itemView);
        ((ViewGroup) mMenuView).addView(itemView, childIndex);
    }

    protected boolean filterLeftoverView(ViewGroup parent, int childIndex) {
        parent.removeViewAt(childIndex);
        return true;
    }

    @Override
    public void setCallback(Callback cb) { mCallback = cb; }

    public Callback getCallback() { return mCallback; }

    public MenuView.ItemView createItemView(ViewGroup parent) {
        return (MenuView.ItemView) mSystemInflater.inflate(mItemLayoutRes, parent, false);
    }

    public View getItemView(MenuItemImpl item, View convertView, ViewGroup parent) {
        MenuView.ItemView itemView;
        if (convertView instanceof MenuView.ItemView) itemView = (MenuView.ItemView) convertView;
        else itemView = createItemView(parent);
        bindItemView(item, itemView);
        return (View) itemView;
    }

    public abstract void bindItemView(MenuItemImpl item, MenuView.ItemView itemView);

    public boolean shouldIncludeItem(int childIndex, MenuItemImpl item) { return true; }

    @Override
    public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
        if (mCallback != null) mCallback.onCloseMenu(menu, allMenusAreClosing);
    }

    @Override
    public boolean onSubMenuSelected(SubMenuBuilder menu) {
        if (mCallback != null) return mCallback.onOpenSubMenu(menu);
        return false;
    }

    @Override
    public boolean flagActionItems() { return false; }

    @Override
    public boolean expandItemActionView(MenuBuilder menu, MenuItemImpl item) { return false; }

    @Override
    public boolean collapseItemActionView(MenuBuilder menu, MenuItemImpl item) { return false; }

    @Override
    public int getId() { return mId; }

    public void setId(int id) { mId = id; }

    @Override
    public android.os.Parcelable onSaveInstanceState() { return null; }

    @Override
    public void onRestoreInstanceState(android.os.Parcelable state) {}
}
