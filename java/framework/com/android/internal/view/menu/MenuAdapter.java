package com.android.internal.view.menu;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import java.util.ArrayList;

/** framework-internal. Adapter over a menu's visible items (AOSP MenuAdapter). */
public class MenuAdapter extends BaseAdapter {
    private final MenuBuilder mAdapterMenu;
    private final LayoutInflater mInflater;
    private final int mItemLayoutRes;
    private final boolean mOverflowOnly;
    private ArrayList<MenuItemImpl> mItems;
    private boolean mForceShowIcon;

    private final Runnable mOnChange = new Runnable() {
        public void run() {
            mItems = null;
            notifyDataSetChanged();
        }
    };

    public MenuAdapter(MenuBuilder menu, LayoutInflater inflater, boolean overflowOnly, int itemLayoutRes) {
        mOverflowOnly = overflowOnly;
        mInflater = inflater;
        mAdapterMenu = menu;
        mItemLayoutRes = itemLayoutRes;
        menu.addChangeListener(mOnChange);
    }

    public void detach() { mAdapterMenu.removeChangeListener(mOnChange); }

    public void setForceShowIcon(boolean forceShow) { mForceShowIcon = forceShow; }

    private ArrayList<MenuItemImpl> items() {
        if (mItems == null) mItems = mOverflowOnly ? mAdapterMenu.getNonActionItems() : mAdapterMenu.getVisibleItems();
        return mItems;
    }

    public int getCount() { return items().size(); }

    public MenuBuilder getAdapterMenu() { return mAdapterMenu; }

    public MenuItemImpl getItem(int position) { return items().get(position); }

    public long getItemId(int position) { return position; }

    @Override
    public boolean hasStableIds() { return true; }

    @Override
    public boolean isEnabled(int position) { return getItem(position).isEnabled(); }

    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) convertView = mInflater.inflate(mItemLayoutRes, parent, false);
        final int currGroupId = getItem(position).getGroupId();
        final int prevGroupId = position - 1 >= 0 ? getItem(position - 1).getGroupId() : currGroupId;
        if (convertView instanceof ListMenuItemView) {
            ListMenuItemView itemView = (ListMenuItemView) convertView;
            itemView.setGroupDividerEnabled(mAdapterMenu.isGroupDividerEnabled() && (currGroupId != prevGroupId));
            if (mForceShowIcon) itemView.setForceShowIcon(true);
        }
        MenuView.ItemView itemView = (MenuView.ItemView) convertView;
        itemView.initialize(getItem(position), 0);
        return convertView;
    }
}
