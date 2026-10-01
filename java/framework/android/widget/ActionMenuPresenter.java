package android.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import com.android.internal.util.InternalRes;
import com.android.internal.view.ActionBarPolicy;
import com.android.internal.view.menu.ActionMenuItemView;
import com.android.internal.view.menu.BaseMenuPresenter;
import com.android.internal.view.menu.MenuBuilder;
import com.android.internal.view.menu.MenuItemImpl;
import com.android.internal.view.menu.MenuPopupHelper;
import com.android.internal.view.menu.MenuView;
import com.android.internal.view.menu.SubMenuBuilder;
import java.util.ArrayList;

/**
 * framework-internal (hidden public class in AOSP). Port of ActionMenuPresenter:
 * decides which items become action buttons (ActionBarPolicy limits, groups,
 * widths), keeps an ActionMenuView in step and shows the overflow menu and
 * action-button sub menus as popups.
 */
public class ActionMenuPresenter extends BaseMenuPresenter {
    private OverflowMenuButton mOverflowButton;
    private Drawable mPendingOverflowIcon;
    private boolean mPendingOverflowIconSet;
    private boolean mReserveOverflow;
    private boolean mReserveOverflowSet;
    private int mWidthLimit;
    private int mActionItemWidthLimit;
    private int mMaxItems;
    private boolean mMaxItemsSet;
    private boolean mStrictWidthLimit;
    private boolean mWidthLimitSet;
    private boolean mExpandedActionViewsExclusive;
    private int mMinCellSize;
    private final SparseBooleanArray mActionButtonGroups = new SparseBooleanArray();
    private OverflowPopup mOverflowPopup;
    private ActionButtonSubmenu mActionButtonPopup;
    private OpenOverflowRunnable mPostedOpenRunnable;
    int mOpenSubMenuId;

    public ActionMenuPresenter(Context context) {
        super(context, InternalRes.layout("action_menu_layout"), InternalRes.layout("action_menu_item_layout"));
    }

    @Override
    public void initForMenu(Context context, MenuBuilder menu) {
        super.initForMenu(context, menu);
        final ActionBarPolicy abp = ActionBarPolicy.get(context);
        if (!mReserveOverflowSet) mReserveOverflow = abp.showsOverflowMenuButton();
        if (!mWidthLimitSet) mWidthLimit = abp.getEmbeddedMenuWidthLimit();
        if (!mMaxItemsSet) mMaxItems = abp.getMaxActionButtons();
        int width = mWidthLimit;
        if (mReserveOverflow) {
            if (mOverflowButton == null) {
                mOverflowButton = new OverflowMenuButton(mSystemContext);
                if (mPendingOverflowIconSet) {
                    mOverflowButton.setImageDrawable(mPendingOverflowIcon);
                    mPendingOverflowIcon = null;
                    mPendingOverflowIconSet = false;
                }
                final int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                mOverflowButton.measure(spec, spec);
            }
            width -= mOverflowButton.getMeasuredWidth();
        } else {
            mOverflowButton = null;
        }
        mActionItemWidthLimit = width;
        mMinCellSize = (int) (ActionMenuView.MIN_CELL_SIZE * context.getResources().getDisplayMetrics().density);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (!mMaxItemsSet) mMaxItems = ActionBarPolicy.get(mContext).getMaxActionButtons();
        if (mMenu != null) mMenu.onItemsChanged(true);
    }

    public void setWidthLimit(int width, boolean strict) {
        mWidthLimit = width;
        mStrictWidthLimit = strict;
        mWidthLimitSet = true;
    }

    public void setReserveOverflow(boolean reserveOverflow) {
        mReserveOverflow = reserveOverflow;
        mReserveOverflowSet = true;
    }

    public void setItemLimit(int itemCount) {
        mMaxItems = itemCount;
        mMaxItemsSet = true;
    }

    public void setExpandedActionViewsExclusive(boolean isExclusive) { mExpandedActionViewsExclusive = isExclusive; }

    public void setOverflowIcon(Drawable icon) {
        if (mOverflowButton != null) {
            mOverflowButton.setImageDrawable(icon);
        } else {
            mPendingOverflowIconSet = true;
            mPendingOverflowIcon = icon;
        }
    }

    public Drawable getOverflowIcon() {
        if (mOverflowButton != null) return mOverflowButton.getDrawable();
        if (mPendingOverflowIconSet) return mPendingOverflowIcon;
        return null;
    }

    @Override
    public MenuView getMenuView(ViewGroup root) {
        MenuView oldMenuView = mMenuView;
        MenuView result = super.getMenuView(root);
        if (oldMenuView != result) ((ActionMenuView) result).setPresenter(this);
        return result;
    }

    @Override
    public View getItemView(MenuItemImpl item, View convertView, ViewGroup parent) {
        View actionView = item.getActionView();
        if (actionView == null || item.hasCollapsibleActionView()) actionView = super.getItemView(item, convertView, parent);
        actionView.setVisibility(item.isActionViewExpanded() ? View.GONE : View.VISIBLE);
        final ActionMenuView menuParent = (ActionMenuView) parent;
        final ViewGroup.LayoutParams lp = actionView.getLayoutParams();
        if (!menuParent.checkLayoutParams(lp)) actionView.setLayoutParams(menuParent.generateLayoutParams(lp));
        return actionView;
    }

    @Override
    public void bindItemView(MenuItemImpl item, MenuView.ItemView itemView) {
        itemView.initialize(item, 0);
        final ActionMenuView menuView = (ActionMenuView) mMenuView;
        final ActionMenuItemView actionItemView = (ActionMenuItemView) itemView;
        actionItemView.setItemInvoker(menuView);
    }

    @Override
    public boolean shouldIncludeItem(int childIndex, MenuItemImpl item) { return item.isActionButton(); }

    @Override
    public void updateMenuView(boolean cleared) {
        super.updateMenuView(cleared);
        if (mMenuView == null) return;
        ((View) mMenuView).requestLayout();
        final ArrayList<MenuItemImpl> nonActionItems = mMenu != null ? mMenu.getNonActionItems() : null;
        boolean hasOverflow = false;
        if (mReserveOverflow && nonActionItems != null) {
            final int count = nonActionItems.size();
            if (count == 1) hasOverflow = !nonActionItems.get(0).isActionViewExpanded();
            else hasOverflow = count > 0;
        }
        if (hasOverflow) {
            if (mOverflowButton == null) mOverflowButton = new OverflowMenuButton(mSystemContext);
            ViewGroup parent = (ViewGroup) mOverflowButton.getParent();
            if (parent != mMenuView) {
                if (parent != null) parent.removeView(mOverflowButton);
                ActionMenuView menuView = (ActionMenuView) mMenuView;
                menuView.addView(mOverflowButton, menuView.generateOverflowButtonLayoutParams());
            }
        } else if (mOverflowButton != null && mOverflowButton.getParent() == mMenuView) {
            ((ViewGroup) mMenuView).removeView(mOverflowButton);
        }
        ((ActionMenuView) mMenuView).setOverflowReserved(mReserveOverflow);
    }

    @Override
    protected boolean filterLeftoverView(ViewGroup parent, int childIndex) {
        if (parent.getChildAt(childIndex) == mOverflowButton) return false;
        return super.filterLeftoverView(parent, childIndex);
    }

    @Override
    public boolean onSubMenuSelected(SubMenuBuilder subMenu) {
        if (!subMenu.hasVisibleItems()) return false;
        SubMenuBuilder topSubMenu = subMenu;
        while (topSubMenu.getParentMenu() != mMenu) topSubMenu = (SubMenuBuilder) topSubMenu.getParentMenu();
        View anchor = findViewForItem(topSubMenu.getItem());
        if (anchor == null) {
            // A sub menu of an overflow item: the overflow popup shows it itself.
            if (mOverflowButton == null) return false;
            anchor = mOverflowButton;
        }
        mOpenSubMenuId = subMenu.getItem().getItemId();
        mActionButtonPopup = new ActionButtonSubmenu(mContext, subMenu, anchor);
        mActionButtonPopup.show();
        super.onSubMenuSelected(subMenu);
        return true;
    }

    private View findViewForItem(MenuItem item) {
        final ViewGroup parent = (ViewGroup) mMenuView;
        if (parent == null) return null;
        final int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = parent.getChildAt(i);
            if (child instanceof MenuView.ItemView && ((MenuView.ItemView) child).getItemData() == item) return child;
        }
        return null;
    }

    public boolean showOverflowMenu() {
        if (isOverflowReserved() && !isOverflowMenuShowing() && mMenu != null && mMenuView != null
                && mPostedOpenRunnable == null && !mMenu.getNonActionItems().isEmpty()) {
            OverflowPopup popup = new OverflowPopup(mContext, mMenu, mOverflowButton, true);
            mPostedOpenRunnable = new OpenOverflowRunnable(popup);
            ((View) mMenuView).post(mPostedOpenRunnable);
            super.onSubMenuSelected(null);
            return true;
        }
        return false;
    }

    public boolean hideOverflowMenu() {
        if (mPostedOpenRunnable != null && mMenuView != null) {
            ((View) mMenuView).removeCallbacks(mPostedOpenRunnable);
            mPostedOpenRunnable = null;
            return true;
        }
        MenuPopupHelper popup = mOverflowPopup;
        if (popup != null) {
            popup.dismiss();
            return true;
        }
        return false;
    }

    public boolean dismissPopupMenus() {
        boolean result = hideOverflowMenu();
        result |= hideSubMenus();
        return result;
    }

    public boolean hideSubMenus() {
        if (mActionButtonPopup != null) {
            mActionButtonPopup.dismiss();
            return true;
        }
        return false;
    }

    public boolean isOverflowMenuShowing() { return mOverflowPopup != null && mOverflowPopup.isShowing(); }

    public boolean isOverflowMenuShowPending() { return mPostedOpenRunnable != null || isOverflowMenuShowing(); }

    public boolean isOverflowReserved() { return mReserveOverflow; }

    @Override
    public boolean flagActionItems() {
        final ArrayList<MenuItemImpl> visibleItems;
        final int itemsSize;
        if (mMenu != null) {
            visibleItems = mMenu.getVisibleItems();
            itemsSize = visibleItems.size();
        } else {
            visibleItems = null;
            itemsSize = 0;
        }
        int maxActions = mMaxItems;
        int widthLimit = mActionItemWidthLimit;
        final int querySpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        final ViewGroup parent = (ViewGroup) mMenuView;
        int requiredItems = 0;
        int requestedItems = 0;
        int firstActionWidth = 0;
        boolean hasOverflow = false;
        for (int i = 0; i < itemsSize; i++) {
            MenuItemImpl item = visibleItems.get(i);
            if (item.requiresActionButton()) requiredItems++;
            else if (item.requestsActionButton()) requestedItems++;
            else hasOverflow = true;
            if (mExpandedActionViewsExclusive && item.isActionViewExpanded()) maxActions = 0;
        }
        if (mReserveOverflow && (hasOverflow || requiredItems + requestedItems > maxActions)) maxActions--;
        maxActions -= requiredItems;
        final SparseBooleanArray seenGroups = mActionButtonGroups;
        seenGroups.clear();
        int cellSize = 0;
        int cellsRemaining = 0;
        if (mStrictWidthLimit) {
            cellsRemaining = widthLimit / mMinCellSize;
            final int cellSizeRemaining = widthLimit % mMinCellSize;
            cellSize = mMinCellSize + cellSizeRemaining / Math.max(1, cellsRemaining);
        }
        for (int i = 0; i < itemsSize; i++) {
            MenuItemImpl item = visibleItems.get(i);
            if (item.requiresActionButton()) {
                View v = getItemView(item, null, parent);
                if (mStrictWidthLimit) {
                    cellsRemaining -= ActionMenuView.measureChildForCells(v, cellSize, cellsRemaining, querySpec, 0);
                } else {
                    v.measure(querySpec, querySpec);
                }
                final int measuredWidth = v.getMeasuredWidth();
                widthLimit -= measuredWidth;
                if (firstActionWidth == 0) firstActionWidth = measuredWidth;
                final int groupId = item.getGroupId();
                if (groupId != 0) seenGroups.put(groupId, true);
                item.setIsActionButton(true);
            } else if (item.requestsActionButton()) {
                final int groupId = item.getGroupId();
                final boolean inGroup = seenGroups.get(groupId);
                boolean isAction = (maxActions > 0 || inGroup) && widthLimit > 0
                        && (!mStrictWidthLimit || cellsRemaining > 0);
                if (isAction) {
                    View v = getItemView(item, null, parent);
                    if (mStrictWidthLimit) {
                        final int cells = ActionMenuView.measureChildForCells(v, cellSize, cellsRemaining, querySpec, 0);
                        cellsRemaining -= cells;
                        if (cells == 0) isAction = false;
                    } else {
                        v.measure(querySpec, querySpec);
                    }
                    final int measuredWidth = v.getMeasuredWidth();
                    widthLimit -= measuredWidth;
                    if (firstActionWidth == 0) firstActionWidth = measuredWidth;
                    if (mStrictWidthLimit) isAction &= widthLimit >= 0;
                    else isAction &= widthLimit + firstActionWidth > 0;
                }
                if (isAction && groupId != 0) {
                    seenGroups.put(groupId, true);
                } else if (inGroup) {
                    seenGroups.put(groupId, false);
                    for (int j = 0; j < i; j++) {
                        MenuItemImpl areYouMyGroupie = visibleItems.get(j);
                        if (areYouMyGroupie.getGroupId() == groupId) {
                            if (areYouMyGroupie.isActionButton()) maxActions++;
                            areYouMyGroupie.setIsActionButton(false);
                        }
                    }
                }
                if (isAction) maxActions--;
                item.setIsActionButton(isAction);
            } else {
                item.setIsActionButton(false);
            }
        }
        return true;
    }

    @Override
    public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
        dismissPopupMenus();
        super.onCloseMenu(menu, allMenusAreClosing);
    }

    public void setMenuView(ActionMenuView menuView) {
        if (menuView != mMenuView) {
            mMenuView = menuView;
            menuView.initialize(mMenu);
        }
    }

    private class OverflowMenuButton extends ImageButton implements ActionMenuView.ActionMenuChildView {
        OverflowMenuButton(Context context) {
            super(context, null, android.R.attr.actionOverflowButtonStyle);
            setClickable(true);
            setFocusable(true);
            setVisibility(VISIBLE);
            setEnabled(true);
        }

        @Override
        public boolean performClick() {
            if (super.performClick()) return true;
            playSoundEffect(SoundEffectConstants.CLICK);
            showOverflowMenu();
            return true;
        }

        public boolean needsDividerBefore() { return false; }

        public boolean needsDividerAfter() { return false; }
    }

    private class OverflowPopup extends MenuPopupHelper {
        OverflowPopup(Context context, MenuBuilder menu, View anchorView, boolean overflowOnly) {
            super(context, menu, anchorView, overflowOnly, android.R.attr.actionOverflowMenuStyle);
            setGravity(Gravity.END);
        }

        @Override
        public void onDismiss() {
            super.onDismiss();
            if (mMenu != null) mMenu.close();
            mOverflowPopup = null;
        }
    }

    private class ActionButtonSubmenu extends MenuPopupHelper {
        ActionButtonSubmenu(Context context, SubMenuBuilder subMenu, View anchorView) {
            super(context, subMenu, anchorView, false, android.R.attr.actionOverflowMenuStyle);
            MenuItemImpl item = (MenuItemImpl) subMenu.getItem();
            if (!item.isActionButton()) {
                setAnchorView(mOverflowButton == null ? (View) mMenuView : mOverflowButton);
            }
        }

        @Override
        public void onDismiss() {
            mActionButtonPopup = null;
            mOpenSubMenuId = 0;
            super.onDismiss();
        }
    }

    private class OpenOverflowRunnable implements Runnable {
        private final OverflowPopup mPopup;

        OpenOverflowRunnable(OverflowPopup popup) { mPopup = popup; }

        public void run() {
            final View menuView = (View) mMenuView;
            if (menuView != null && menuView.getWindowToken() != null && mPopup.tryShow()) mOverflowPopup = mPopup;
            mPostedOpenRunnable = null;
        }
    }
}
