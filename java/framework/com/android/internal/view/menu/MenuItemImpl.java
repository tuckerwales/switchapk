package com.android.internal.view.menu;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.ActionProvider;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;

/** framework-internal. One menu item (AOSP MenuItemImpl, model only). */
public final class MenuItemImpl implements MenuItem {
    private static final String TAG = "MenuItemImpl";
    private static final int CHECKABLE = 0x00000001;
    private static final int CHECKED = 0x00000002;
    private static final int EXCLUSIVE = 0x00000004;
    private static final int HIDDEN = 0x00000008;
    private static final int ENABLED = 0x00000010;
    private static final int IS_ACTION = 0x00000020;

    private final int mId;
    private final int mGroup;
    private final int mCategoryOrder;
    private final int mOrdering;
    private CharSequence mTitle;
    private CharSequence mTitleCondensed;
    private Intent mIntent;
    private char mShortcutNumericChar;
    private int mShortcutNumericModifiers = KeyEvent.META_CTRL_ON;
    private char mShortcutAlphabeticChar;
    private int mShortcutAlphabeticModifiers = KeyEvent.META_CTRL_ON;
    private Drawable mIconDrawable;
    private int mIconResId;
    private ColorStateList mIconTintList;
    private final MenuBuilder mMenu;
    private SubMenuBuilder mSubMenu;
    private Runnable mItemCallback;
    private MenuItem.OnMenuItemClickListener mClickListener;
    private int mFlags = ENABLED;
    private int mShowAsAction;
    private View mActionView;
    private ActionProvider mActionProvider;
    private MenuItem.OnActionExpandListener mOnActionExpandListener;
    private boolean mIsActionViewExpanded;
    private ContextMenu.ContextMenuInfo mMenuInfo;
    private CharSequence mContentDescription;
    private CharSequence mTooltipText;

    MenuItemImpl(MenuBuilder menu, int group, int id, int categoryOrder, int ordering, CharSequence title) {
        mMenu = menu;
        mId = id;
        mGroup = group;
        mCategoryOrder = categoryOrder;
        mOrdering = ordering;
        mTitle = title;
    }

    public boolean invoke() {
        if (mClickListener != null && mClickListener.onMenuItemClick(this)) return true;
        if (mMenu.dispatchMenuItemSelected(mMenu.getRootMenu(), this)) return true;
        if (mItemCallback != null) {
            mItemCallback.run();
            return true;
        }
        if (mIntent != null) {
            try {
                mMenu.getContext().startActivity(mIntent);
                return true;
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Can't find activity to handle intent; ignoring", e);
            }
        }
        return mActionProvider != null && mActionProvider.onPerformDefaultAction();
    }

    public boolean isEnabled() { return (mFlags & ENABLED) != 0; }

    public MenuItem setEnabled(boolean enabled) {
        if (enabled) mFlags |= ENABLED;
        else mFlags &= ~ENABLED;
        mMenu.onItemsChanged(false);
        return this;
    }

    public int getGroupId() { return mGroup; }

    public int getItemId() { return mId; }

    public int getOrder() { return mCategoryOrder; }

    public int getOrdering() { return mOrdering; }

    public Intent getIntent() { return mIntent; }

    public MenuItem setIntent(Intent intent) {
        mIntent = intent;
        return this;
    }

    public Runnable getCallback() { return mItemCallback; }

    public MenuItem setCallback(Runnable callback) {
        mItemCallback = callback;
        return this;
    }

    public char getAlphabeticShortcut() { return mShortcutAlphabeticChar; }

    public MenuItem setAlphabeticShortcut(char alphaChar) {
        mShortcutAlphabeticChar = Character.toLowerCase(alphaChar);
        return this;
    }

    public MenuItem setAlphabeticShortcut(char alphaChar, int alphaModifiers) {
        mShortcutAlphabeticChar = Character.toLowerCase(alphaChar);
        mShortcutAlphabeticModifiers = KeyEvent.normalizeMetaState(alphaModifiers);
        return this;
    }

    public int getAlphabeticModifiers() { return mShortcutAlphabeticModifiers; }

    public char getNumericShortcut() { return mShortcutNumericChar; }

    public int getNumericModifiers() { return mShortcutNumericModifiers; }

    public MenuItem setNumericShortcut(char numericChar) {
        mShortcutNumericChar = numericChar;
        return this;
    }

    public MenuItem setNumericShortcut(char numericChar, int numericModifiers) {
        mShortcutNumericChar = numericChar;
        mShortcutNumericModifiers = KeyEvent.normalizeMetaState(numericModifiers);
        return this;
    }

    public MenuItem setShortcut(char numericChar, char alphaChar) {
        mShortcutNumericChar = numericChar;
        mShortcutAlphabeticChar = Character.toLowerCase(alphaChar);
        return this;
    }

    public MenuItem setShortcut(char numericChar, char alphaChar, int numericModifiers, int alphaModifiers) {
        setShortcut(numericChar, alphaChar);
        mShortcutNumericModifiers = KeyEvent.normalizeMetaState(numericModifiers);
        mShortcutAlphabeticModifiers = KeyEvent.normalizeMetaState(alphaModifiers);
        return this;
    }

    public SubMenu getSubMenu() { return mSubMenu; }

    public boolean hasSubMenu() { return mSubMenu != null; }

    void setSubMenu(SubMenuBuilder subMenu) {
        mSubMenu = subMenu;
        subMenu.setHeaderTitle(getTitle());
    }

    public CharSequence getTitle() { return mTitle; }

    public MenuItem setTitle(CharSequence title) {
        mTitle = title;
        mMenu.onItemsChanged(false);
        if (mSubMenu != null) mSubMenu.setHeaderTitle(title);
        return this;
    }

    public MenuItem setTitle(int title) { return setTitle(mMenu.getContext().getString(title)); }

    public CharSequence getTitleCondensed() { return mTitleCondensed != null ? mTitleCondensed : mTitle; }

    public MenuItem setTitleCondensed(CharSequence title) {
        mTitleCondensed = title;
        return this;
    }

    public Drawable getIcon() {
        if (mIconDrawable != null) return mIconDrawable;
        if (mIconResId != 0) {
            Drawable icon = mMenu.getContext().getDrawable(mIconResId);
            mIconResId = 0;
            mIconDrawable = icon;
            if (mIconTintList != null && icon != null) {
                mIconDrawable = icon.mutate();
                mIconDrawable.setTintList(mIconTintList);
            }
            return icon;
        }
        return null;
    }

    public MenuItem setIcon(Drawable icon) {
        mIconResId = 0;
        mIconDrawable = icon;
        mMenu.onItemsChanged(false);
        return this;
    }

    public MenuItem setIcon(int iconResId) {
        mIconDrawable = null;
        mIconResId = iconResId;
        mMenu.onItemsChanged(false);
        return this;
    }

    public MenuItem setIconTintList(ColorStateList iconTintList) {
        mIconTintList = iconTintList;
        if (mIconDrawable != null) {
            mIconDrawable = mIconDrawable.mutate();
            mIconDrawable.setTintList(iconTintList);
        }
        return this;
    }

    public ColorStateList getIconTintList() { return mIconTintList; }

    public boolean isCheckable() { return (mFlags & CHECKABLE) == CHECKABLE; }

    public MenuItem setCheckable(boolean checkable) {
        mFlags = (mFlags & ~CHECKABLE) | (checkable ? CHECKABLE : 0);
        return this;
    }

    public void setExclusiveCheckable(boolean exclusive) { mFlags = (mFlags & ~EXCLUSIVE) | (exclusive ? EXCLUSIVE : 0); }

    public boolean isExclusiveCheckable() { return (mFlags & EXCLUSIVE) != 0; }

    public boolean isChecked() { return (mFlags & CHECKED) == CHECKED; }

    public MenuItem setChecked(boolean checked) {
        if ((mFlags & EXCLUSIVE) != 0) mMenu.setExclusiveItemChecked(this);
        else setCheckedInt(checked);
        return this;
    }

    void setCheckedInt(boolean checked) { mFlags = (mFlags & ~CHECKED) | (checked ? CHECKED : 0); }

    public boolean isVisible() { return (mFlags & HIDDEN) == 0; }

    public MenuItem setVisible(boolean shown) {
        mFlags = (mFlags & ~HIDDEN) | (shown ? 0 : HIDDEN);
        mMenu.onItemsChanged(false);
        return this;
    }

    public MenuItem setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener clickListener) {
        mClickListener = clickListener;
        return this;
    }

    @Override
    public String toString() { return mTitle != null ? mTitle.toString() : null; }

    void setMenuInfo(ContextMenu.ContextMenuInfo menuInfo) { mMenuInfo = menuInfo; }

    public ContextMenu.ContextMenuInfo getMenuInfo() { return mMenuInfo; }

    public boolean requiresActionButton() { return (mShowAsAction & SHOW_AS_ACTION_ALWAYS) == SHOW_AS_ACTION_ALWAYS; }

    public void setShowAsAction(int actionEnum) {
        switch (actionEnum & 0x3) {
            case SHOW_AS_ACTION_NEVER:
            case SHOW_AS_ACTION_IF_ROOM:
            case SHOW_AS_ACTION_ALWAYS:
                break;
            default:
                throw new IllegalArgumentException("SHOW_AS_ACTION_ALWAYS, SHOW_AS_ACTION_IF_ROOM,"
                        + " and SHOW_AS_ACTION_NEVER are mutually exclusive.");
        }
        mShowAsAction = actionEnum;
    }

    public int getShowAsAction() { return mShowAsAction; }

    public MenuItem setActionView(View view) {
        mActionView = view;
        mActionProvider = null;
        return this;
    }

    public MenuItem setActionView(int resId) {
        LayoutInflater inflater = LayoutInflater.from(mMenu.getContext());
        setActionView(inflater.inflate(resId, null, false));
        return this;
    }

    public View getActionView() {
        if (mActionView != null) return mActionView;
        if (mActionProvider != null) {
            mActionView = mActionProvider.onCreateActionView(this);
            return mActionView;
        }
        return null;
    }

    public ActionProvider getActionProvider() { return mActionProvider; }

    public MenuItem setActionProvider(ActionProvider actionProvider) {
        mActionView = null;
        mActionProvider = actionProvider;
        return this;
    }

    public MenuItem setShowAsActionFlags(int actionEnum) {
        setShowAsAction(actionEnum);
        return this;
    }

    public boolean expandActionView() {
        if ((mShowAsAction & SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW) == 0 || mActionView == null) return false;
        if (mOnActionExpandListener == null || mOnActionExpandListener.onMenuItemActionExpand(this)) {
            mIsActionViewExpanded = true;
            return true;
        }
        return false;
    }

    public boolean collapseActionView() {
        if ((mShowAsAction & SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW) == 0) return false;
        if (mActionView == null) return true;
        if (mOnActionExpandListener == null || mOnActionExpandListener.onMenuItemActionCollapse(this)) {
            mIsActionViewExpanded = false;
            return true;
        }
        return false;
    }

    public MenuItem setOnActionExpandListener(MenuItem.OnActionExpandListener listener) {
        mOnActionExpandListener = listener;
        return this;
    }

    public boolean isActionViewExpanded() { return mIsActionViewExpanded; }

    public MenuItem setContentDescription(CharSequence contentDescription) {
        mContentDescription = contentDescription;
        return this;
    }

    public CharSequence getContentDescription() { return mContentDescription; }

    public MenuItem setTooltipText(CharSequence tooltipText) {
        mTooltipText = tooltipText;
        return this;
    }

    public CharSequence getTooltipText() { return mTooltipText; }

    /** framework-internal. The title an item view shows (condensed when it prefers it). */
    CharSequence getTitleForItemView(MenuView.ItemView itemView) {
        return ((itemView != null) && itemView.prefersCondensedTitle()) ? getTitleCondensed() : getTitle();
    }

    /** framework-internal. Shortcuts are shown only when the menu shows them (no hardware keyboard here). */
    boolean shouldShowShortcut() { return mMenu.isShortcutsVisible() && (getShortcut() != 0); }

    char getShortcut() { return mMenu.isQwertyMode() ? getAlphabeticShortcut() : getNumericShortcut(); }

    String getShortcutLabel() {
        char shortcut = getShortcut();
        if (shortcut == 0) return "";
        StringBuilder sb = new StringBuilder("Menu+");
        switch (shortcut) {
            case '\n': sb.append("enter"); break;
            case '\b': sb.append("delete"); break;
            case ' ': sb.append("space"); break;
            default: sb.append(shortcut); break;
        }
        return sb.toString();
    }

    /** framework-internal. Overflow menus hide icons unless the menu opts in. */
    public boolean shouldShowIcon() { return mMenu.getOptionalIconsVisible(); }
}
