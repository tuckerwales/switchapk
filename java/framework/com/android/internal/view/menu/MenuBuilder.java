package com.android.internal.view.menu;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import java.util.ArrayList;

/**
 * framework-internal. Menu model (AOSP MenuBuilder): items sorted by category
 * and order, groups, shortcuts, item invocation, and presenters (the action
 * bar's ActionMenuPresenter) that split items into action and overflow items.
 * Menus are also shown by MenuPanel, MenuPopupHelper and MenuDialogHelper,
 * which register as close listeners.
 */
public class MenuBuilder implements Menu {
    private static final int[] sCategoryToOrder = new int[] {1, 4, 5, 3, 2, 0};

    private final Context mContext;
    private final Resources mResources;
    final ArrayList<MenuItemImpl> mItems = new ArrayList<MenuItemImpl>();
    private final ArrayList<java.lang.ref.WeakReference<MenuPresenter>> mPresenters =
            new ArrayList<java.lang.ref.WeakReference<MenuPresenter>>();
    private final ArrayList<MenuItemImpl> mActionItems = new ArrayList<MenuItemImpl>();
    private final ArrayList<MenuItemImpl> mNonActionItems = new ArrayList<MenuItemImpl>();
    private boolean mIsActionItemsStale = true;
    private boolean mPreventDispatchingItemsChanged;
    private boolean mItemsChangedWhileDispatchPrevented;
    private boolean mStructureChangedWhileDispatchPrevented;
    private Callback mCallback;
    private boolean mQwertyMode;
    private boolean mGroupDividerEnabled;
    ContextMenu.ContextMenuInfo mCurrentMenuInfo;

    public interface Callback {
        boolean onMenuItemSelected(MenuBuilder menu, MenuItem item);
        void onMenuModeChange(MenuBuilder menu);
    }

    public interface ItemInvoker {
        boolean invokeItem(MenuItemImpl item);
    }

    public MenuBuilder(Context context) {
        mContext = context;
        mResources = context.getResources();
    }

    public void setCallback(Callback cb) { mCallback = cb; }

    private int mDefaultShowAsAction = MenuItem.SHOW_AS_ACTION_NEVER;

    /** Show-as-action flags given to items added from now on (action modes use ifRoom). */
    public MenuBuilder setDefaultShowAsAction(int defaultShowAsAction) {
        mDefaultShowAsAction = defaultShowAsAction;
        return this;
    }

    public Context getContext() { return mContext; }

    public Resources getResources() { return mResources; }

    protected MenuItem addInternal(int group, int id, int categoryOrder, CharSequence title) {
        final int ordering = getOrdering(categoryOrder);
        final MenuItemImpl item = new MenuItemImpl(this, group, id, categoryOrder, ordering, title, mDefaultShowAsAction);
        if (mCurrentMenuInfo != null) item.setMenuInfo(mCurrentMenuInfo);
        mItems.add(findInsertIndex(mItems, ordering), item);
        onItemsChanged(true);
        return item;
    }

    public MenuItem add(CharSequence title) { return addInternal(0, 0, 0, title); }

    public MenuItem add(int titleRes) { return addInternal(0, 0, 0, mResources.getString(titleRes)); }

    public MenuItem add(int group, int id, int categoryOrder, CharSequence title) {
        return addInternal(group, id, categoryOrder, title);
    }

    public MenuItem add(int group, int id, int categoryOrder, int title) {
        return addInternal(group, id, categoryOrder, mResources.getString(title));
    }

    public SubMenu addSubMenu(CharSequence title) { return addSubMenu(0, 0, 0, title); }

    public SubMenu addSubMenu(int titleRes) { return addSubMenu(0, 0, 0, mResources.getString(titleRes)); }

    public SubMenu addSubMenu(int group, int id, int categoryOrder, CharSequence title) {
        final MenuItemImpl item = (MenuItemImpl) addInternal(group, id, categoryOrder, title);
        final SubMenuBuilder subMenu = new SubMenuBuilder(mContext, this, item);
        item.setSubMenu(subMenu);
        return subMenu;
    }

    public SubMenu addSubMenu(int group, int id, int categoryOrder, int titleRes) {
        return addSubMenu(group, id, categoryOrder, mResources.getString(titleRes));
    }

    public void setGroupDividerEnabled(boolean groupDividerEnabled) { mGroupDividerEnabled = groupDividerEnabled; }

    public boolean isGroupDividerEnabled() { return mGroupDividerEnabled; }

    public int addIntentOptions(int group, int id, int categoryOrder, ComponentName caller, Intent[] specifics,
            Intent intent, int flags, MenuItem[] outSpecificItems) {
        if ((flags & FLAG_APPEND_TO_GROUP) == 0) removeGroup(group);
        return 0;
    }

    public void removeItem(int id) { removeItemAtInt(findItemIndex(id), true); }

    public void removeGroup(int group) {
        final int i = findGroupIndex(group);
        if (i >= 0) {
            final int maxRemovable = mItems.size() - i;
            int numRemoved = 0;
            while ((numRemoved++ < maxRemovable) && (mItems.get(i).getGroupId() == group)) {
                removeItemAtInt(i, false);
            }
            onItemsChanged(true);
        }
    }

    private void removeItemAtInt(int index, boolean updateChildrenOnMenuViews) {
        if ((index < 0) || (index >= mItems.size())) return;
        mItems.remove(index);
        if (updateChildrenOnMenuViews) onItemsChanged(true);
    }

    public void removeItemAt(int index) { removeItemAtInt(index, true); }

    public void clear() {
        mItems.clear();
        onItemsChanged(true);
    }

    void setExclusiveItemChecked(MenuItem item) {
        final int group = item.getGroupId();
        for (MenuItemImpl curItem : mItems) {
            if (curItem.getGroupId() == group) {
                if (!curItem.isExclusiveCheckable()) continue;
                if (!curItem.isCheckable()) continue;
                curItem.setCheckedInt(curItem == item);
            }
        }
    }

    public void setGroupCheckable(int group, boolean checkable, boolean exclusive) {
        for (MenuItemImpl item : mItems) {
            if (item.getGroupId() == group) {
                item.setExclusiveCheckable(exclusive);
                item.setCheckable(checkable);
            }
        }
    }

    public void setGroupVisible(int group, boolean visible) {
        for (MenuItemImpl item : mItems) {
            if (item.getGroupId() == group) item.setVisible(visible);
        }
    }

    public void setGroupEnabled(int group, boolean enabled) {
        for (MenuItemImpl item : mItems) {
            if (item.getGroupId() == group) item.setEnabled(enabled);
        }
    }

    public boolean hasVisibleItems() {
        for (MenuItemImpl item : mItems) {
            if (item.isVisible()) return true;
        }
        return false;
    }

    public MenuItem findItem(int id) {
        for (MenuItemImpl item : mItems) {
            if (item.getItemId() == id) return item;
            if (item.hasSubMenu()) {
                MenuItem possibleItem = item.getSubMenu().findItem(id);
                if (possibleItem != null) return possibleItem;
            }
        }
        return null;
    }

    public int findItemIndex(int id) {
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i).getItemId() == id) return i;
        }
        return -1;
    }

    public int findGroupIndex(int group) {
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i).getGroupId() == group) return i;
        }
        return -1;
    }

    public int size() { return mItems.size(); }

    public MenuItem getItem(int index) { return mItems.get(index); }

    public boolean isShortcutKey(int keyCode, KeyEvent event) { return findItemWithShortcutForKey(keyCode, event) != null; }

    public void setQwertyMode(boolean isQwerty) { mQwertyMode = isQwerty; }

    public boolean isQwertyMode() { return mQwertyMode; }

    private static int getOrdering(int categoryOrder) {
        final int index = (categoryOrder & CATEGORY_MASK) >> CATEGORY_SHIFT;
        if (index < 0 || index >= sCategoryToOrder.length) throw new IllegalArgumentException("order does not contain a valid category.");
        return (sCategoryToOrder[index] << CATEGORY_SHIFT) | (categoryOrder & USER_MASK);
    }

    private static int findInsertIndex(ArrayList<MenuItemImpl> items, int ordering) {
        for (int i = items.size() - 1; i >= 0; i--) {
            MenuItemImpl item = items.get(i);
            if (item.getOrdering() <= ordering) return i + 1;
        }
        return 0;
    }

    public boolean performShortcut(int keyCode, KeyEvent event, int flags) {
        final MenuItemImpl item = findItemWithShortcutForKey(keyCode, event);
        boolean handled = false;
        if (item != null) handled = performItemAction(item, flags);
        if ((flags & FLAG_ALWAYS_PERFORM_CLOSE) != 0) close();
        return handled;
    }

    MenuItemImpl findItemWithShortcutForKey(int keyCode, KeyEvent event) {
        final int metaState = event.getMetaState();
        final boolean qwerty = isQwertyMode();
        int c = event.getUnicodeChar(metaState & ~KeyEvent.META_CTRL_MASK);
        if (c == 0 && keyCode != KeyEvent.KEYCODE_DEL) c = event.getUnicodeChar(0);
        for (MenuItemImpl item : mItems) {
            if (item.hasSubMenu()) {
                MenuItemImpl subMenuItem = ((MenuBuilder) item.getSubMenu()).findItemWithShortcutForKey(keyCode, event);
                if (subMenuItem != null) return subMenuItem;
            }
            final char shortcutChar = qwerty ? item.getAlphabeticShortcut() : item.getNumericShortcut();
            if (shortcutChar != 0 && item.isEnabled()
                    && (Character.toLowerCase(shortcutChar) == Character.toLowerCase((char) c))) {
                return item;
            }
        }
        return null;
    }

    public boolean performIdentifierAction(int id, int flags) { return performItemAction(findItem(id), flags); }

    public boolean performItemAction(MenuItem item, int flags) { return performItemAction(item, null, flags); }

    public boolean performItemAction(MenuItem item, MenuPresenter preferredPresenter, int flags) {
        MenuItemImpl itemImpl = (MenuItemImpl) item;
        if (itemImpl == null || !itemImpl.isEnabled()) return false;
        boolean invoked = itemImpl.invoke();
        if (itemImpl.hasCollapsibleActionView()) {
            // Tapping an item with a collapsible action view (a SearchView) expands it in place.
            invoked |= itemImpl.expandActionView();
            if (invoked) close(true);
        } else if (itemImpl.hasSubMenu()) {
            // A presenter (the action bar) may show the sub menu; otherwise the menu closes.
            boolean shown = dispatchSubMenuSelected((SubMenuBuilder) itemImpl.getSubMenu(), preferredPresenter);
            invoked |= shown;
            if (!shown && (flags & FLAG_PERFORM_NO_CLOSE) == 0) close(true);
        } else if ((flags & FLAG_PERFORM_NO_CLOSE) == 0) {
            close(true);
        }
        return invoked;
    }

    // ---------------------------------------------------------------- presenters

    public void addMenuPresenter(MenuPresenter presenter) { addMenuPresenter(presenter, mContext); }

    public void addMenuPresenter(MenuPresenter presenter, Context menuContext) {
        mPresenters.add(new java.lang.ref.WeakReference<MenuPresenter>(presenter));
        presenter.initForMenu(menuContext, this);
        mIsActionItemsStale = true;
    }

    public void removeMenuPresenter(MenuPresenter presenter) {
        for (int i = mPresenters.size() - 1; i >= 0; i--) {
            MenuPresenter item = mPresenters.get(i).get();
            if (item == null || item == presenter) mPresenters.remove(i);
        }
    }

    private void dispatchPresenterUpdate(boolean cleared) {
        if (mPresenters.isEmpty()) return;
        stopDispatchingItemsChanged();
        for (int i = mPresenters.size() - 1; i >= 0; i--) {
            MenuPresenter presenter = mPresenters.get(i).get();
            if (presenter == null) mPresenters.remove(i);
            else presenter.updateMenuView(cleared);
        }
        startDispatchingItemsChanged();
    }

    private boolean dispatchSubMenuSelected(SubMenuBuilder subMenu, MenuPresenter preferredPresenter) {
        if (mPresenters.isEmpty()) return false;
        boolean result = false;
        if (preferredPresenter != null) result = preferredPresenter.onSubMenuSelected(subMenu);
        for (int i = mPresenters.size() - 1; i >= 0; i--) {
            MenuPresenter presenter = mPresenters.get(i).get();
            if (presenter == null) mPresenters.remove(i);
            else if (!result) result = presenter.onSubMenuSelected(subMenu);
        }
        return result;
    }

    public void stopDispatchingItemsChanged() {
        if (!mPreventDispatchingItemsChanged) {
            mPreventDispatchingItemsChanged = true;
            mItemsChangedWhileDispatchPrevented = false;
            mStructureChangedWhileDispatchPrevented = false;
        }
    }

    public void startDispatchingItemsChanged() {
        mPreventDispatchingItemsChanged = false;
        if (mItemsChangedWhileDispatchPrevented) {
            mItemsChangedWhileDispatchPrevented = false;
            onItemsChanged(mStructureChangedWhileDispatchPrevented);
        }
    }

    void onItemActionRequestChanged(MenuItemImpl item) {
        mIsActionItemsStale = true;
        onItemsChanged(true);
    }

    /** Asks the presenters which items go in the action bar; the rest overflow. */
    public void flagActionItems() {
        final ArrayList<MenuItemImpl> visibleItems = getVisibleItems();
        if (!mIsActionItemsStale) return;
        boolean flagged = false;
        for (int i = mPresenters.size() - 1; i >= 0; i--) {
            MenuPresenter presenter = mPresenters.get(i).get();
            if (presenter == null) mPresenters.remove(i);
            else flagged |= presenter.flagActionItems();
        }
        mActionItems.clear();
        mNonActionItems.clear();
        if (flagged) {
            for (MenuItemImpl item : visibleItems) {
                if (item.isActionButton()) mActionItems.add(item);
                else mNonActionItems.add(item);
            }
        } else {
            mNonActionItems.addAll(visibleItems);
        }
        mIsActionItemsStale = false;
    }

    private MenuItemImpl mExpandedItem;

    public boolean expandItemActionView(MenuItemImpl item) {
        if (mPresenters.isEmpty()) return false;
        boolean expanded = false;
        stopDispatchingItemsChanged();
        for (int i = mPresenters.size() - 1; i >= 0; i--) {
            MenuPresenter presenter = mPresenters.get(i).get();
            if (presenter == null) mPresenters.remove(i);
            else if ((expanded = presenter.expandItemActionView(this, item))) break;
        }
        startDispatchingItemsChanged();
        if (expanded) mExpandedItem = item;
        return expanded;
    }

    public boolean collapseItemActionView(MenuItemImpl item) {
        if (mPresenters.isEmpty() || mExpandedItem != item) return false;
        boolean collapsed = false;
        stopDispatchingItemsChanged();
        for (int i = mPresenters.size() - 1; i >= 0; i--) {
            MenuPresenter presenter = mPresenters.get(i).get();
            if (presenter == null) mPresenters.remove(i);
            else if ((collapsed = presenter.collapseItemActionView(this, item))) break;
        }
        startDispatchingItemsChanged();
        if (collapsed) mExpandedItem = null;
        return collapsed;
    }

    public MenuItemImpl getExpandedItem() { return mExpandedItem; }

    public ArrayList<MenuItemImpl> getActionItems() {
        flagActionItems();
        return mActionItems;
    }

    public ArrayList<MenuItemImpl> getNonActionItems() {
        flagActionItems();
        return mNonActionItems;
    }

    boolean dispatchMenuItemSelected(MenuBuilder menu, MenuItem item) {
        return mCallback != null && mCallback.onMenuItemSelected(menu, item);
    }

    /** framework-internal. Told when the menu (or a sub menu of it) closes. */
    public interface CloseListener {
        void onMenuClosed(MenuBuilder menu, boolean allMenusAreClosing);
    }

    private final ArrayList<CloseListener> mCloseListeners = new ArrayList<CloseListener>();
    private final ArrayList<Runnable> mChangeListeners = new ArrayList<Runnable>();
    private boolean mIsClosing;
    private boolean mShortcutsVisible;
    private boolean mOptionalIconsVisible;

    public void addCloseListener(CloseListener l) { if (!mCloseListeners.contains(l)) mCloseListeners.add(l); }

    public void removeCloseListener(CloseListener l) { mCloseListeners.remove(l); }

    /** framework-internal. Runs when items are added, removed or changed. */
    public void addChangeListener(Runnable r) { if (!mChangeListeners.contains(r)) mChangeListeners.add(r); }

    public void removeChangeListener(Runnable r) { mChangeListeners.remove(r); }

    public final void close(boolean allMenusAreClosing) {
        if (mIsClosing) return;
        mIsClosing = true;
        for (int i = mPresenters.size() - 1; i >= 0; i--) {
            MenuPresenter presenter = mPresenters.get(i).get();
            if (presenter == null) mPresenters.remove(i);
            else presenter.onCloseMenu(this, allMenusAreClosing);
        }
        ArrayList<CloseListener> listeners = new ArrayList<CloseListener>(mCloseListeners);
        for (CloseListener l : listeners) l.onMenuClosed(this, allMenusAreClosing);
        mIsClosing = false;
    }

    public void close() { close(true); }

    public void onItemsChanged(boolean structureChanged) {
        if (!mPreventDispatchingItemsChanged) {
            if (structureChanged) mIsActionItemsStale = true;
            dispatchPresenterUpdate(structureChanged);
        } else {
            mItemsChangedWhileDispatchPrevented = true;
            if (structureChanged) mStructureChangedWhileDispatchPrevented = true;
        }
        for (int i = 0; i < mChangeListeners.size(); i++) mChangeListeners.get(i).run();
    }

    public void setShortcutsVisible(boolean shortcutsVisible) { mShortcutsVisible = shortcutsVisible; }

    public boolean isShortcutsVisible() { return mShortcutsVisible; }

    public void setOptionalIconsVisible(boolean visible) { mOptionalIconsVisible = visible; }

    public boolean getOptionalIconsVisible() { return mOptionalIconsVisible; }

    /** framework-internal. Callback that receives item selections (used by sub menus). */
    public Callback getCallback() { return mCallback; }

    public ArrayList<MenuItemImpl> getVisibleItems() {
        ArrayList<MenuItemImpl> visible = new ArrayList<MenuItemImpl>();
        for (MenuItemImpl item : mItems) if (item.isVisible()) visible.add(item);
        return visible;
    }

    public MenuBuilder getRootMenu() { return this; }

    public void setCurrentMenuInfo(ContextMenu.ContextMenuInfo menuInfo) { mCurrentMenuInfo = menuInfo; }

    static final int USER_MASK = 0x0000ffff;
    static final int USER_SHIFT = 0;
    static final int CATEGORY_MASK = 0xffff0000;
    static final int CATEGORY_SHIFT = 16;
}
