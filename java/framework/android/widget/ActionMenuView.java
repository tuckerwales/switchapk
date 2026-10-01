package android.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import com.android.internal.view.menu.ActionMenuItemView;
import com.android.internal.view.menu.MenuBuilder;
import com.android.internal.view.menu.MenuItemImpl;
import com.android.internal.view.menu.MenuPresenter;
import com.android.internal.view.menu.MenuView;

/**
 * Port of AOSP ActionMenuView: the action buttons and overflow button of a
 * Toolbar or action bar. Given an exact width it lays items out in 56dp
 * cells, expanding text items and spreading spare space, as on Android.
 */
public class ActionMenuView extends LinearLayout implements MenuBuilder.ItemInvoker, MenuView {
    static final int MIN_CELL_SIZE = 56; // dips
    static final int GENERATED_ITEM_PADDING = 4; // dips

    private MenuBuilder mMenu;
    private Context mPopupContext;
    private int mPopupTheme;
    private boolean mReserveOverflow;
    private ActionMenuPresenter mPresenter;
    private MenuPresenter.Callback mActionMenuPresenterCallback;
    private MenuBuilder.Callback mMenuBuilderCallback;
    private boolean mFormatItems;
    private int mFormatItemsWidth;
    private int mMinCellSize;
    private int mGeneratedItemPadding;
    private OnMenuItemClickListener mOnMenuItemClickListener;

    public ActionMenuView(Context context) { this(context, null); }

    public ActionMenuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBaselineAligned(false);
        final float density = context.getResources().getDisplayMetrics().density;
        mMinCellSize = (int) (MIN_CELL_SIZE * density);
        mGeneratedItemPadding = (int) (GENERATED_ITEM_PADDING * density);
        mPopupContext = context;
        mPopupTheme = 0;
    }

    public void setPopupTheme(int resId) {
        if (mPopupTheme != resId) {
            mPopupTheme = resId;
            mPopupContext = resId == 0 ? getContext() : new ContextThemeWrapper(getContext(), resId);
        }
    }

    public int getPopupTheme() { return mPopupTheme; }

    /** framework-internal (hidden in AOSP). */
    public void setPresenter(ActionMenuPresenter presenter) {
        mPresenter = presenter;
        mPresenter.setMenuView(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mPresenter != null) {
            mPresenter.updateMenuView(false);
            if (mPresenter.isOverflowMenuShowing()) {
                mPresenter.hideOverflowMenu();
                mPresenter.showOverflowMenu();
            }
        }
    }

    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) { mOnMenuItemClickListener = listener; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final boolean wasFormatted = mFormatItems;
        mFormatItems = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY;
        if (wasFormatted != mFormatItems) mFormatItemsWidth = 0;
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        if (mFormatItems && mMenu != null && widthSize != mFormatItemsWidth) {
            mFormatItemsWidth = widthSize;
            mMenu.onItemsChanged(true);
        }
        final int childCount = getChildCount();
        if (mFormatItems && childCount > 0) {
            onMeasureExactFormat(widthMeasureSpec, heightMeasureSpec);
        } else {
            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                lp.leftMargin = lp.rightMargin = 0;
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private void onMeasureExactFormat(int widthMeasureSpec, int heightMeasureSpec) {
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        final int widthPadding = getPaddingLeft() + getPaddingRight();
        final int heightPadding = getPaddingTop() + getPaddingBottom();
        final int itemHeightSpec = getChildMeasureSpec(heightMeasureSpec, heightPadding,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        widthSize -= widthPadding;
        final int cellCount = widthSize / mMinCellSize;
        final int cellSizeRemaining = widthSize % mMinCellSize;
        if (cellCount == 0) {
            setMeasuredDimension(widthSize, 0);
            return;
        }
        final int cellSize = mMinCellSize + cellSizeRemaining / cellCount;
        int cellsRemaining = cellCount;
        int maxChildHeight = 0;
        int maxCellsUsed = 0;
        int expandableItemCount = 0;
        int visibleItemCount = 0;
        boolean hasOverflow = false;
        long smallestItemsAt = 0;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            final boolean isGeneratedItem = child instanceof ActionMenuItemView;
            visibleItemCount++;
            if (isGeneratedItem) child.setPadding(mGeneratedItemPadding, 0, mGeneratedItemPadding, 0);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            lp.expanded = false;
            lp.extraPixels = 0;
            lp.cellsUsed = 0;
            lp.expandable = false;
            lp.leftMargin = 0;
            lp.rightMargin = 0;
            lp.preventEdgeOffset = isGeneratedItem && ((ActionMenuItemView) child).hasText();
            final int cellsAvailable = lp.isOverflowButton ? 1 : cellsRemaining;
            final int cellsUsed = measureChildForCells(child, cellSize, cellsAvailable, itemHeightSpec, heightPadding);
            maxCellsUsed = Math.max(maxCellsUsed, cellsUsed);
            if (lp.expandable) expandableItemCount++;
            if (lp.isOverflowButton) hasOverflow = true;
            cellsRemaining -= cellsUsed;
            maxChildHeight = Math.max(maxChildHeight, child.getMeasuredHeight());
            if (cellsUsed == 1) smallestItemsAt |= (1 << i);
        }
        final boolean centerSingleExpandedItem = hasOverflow && visibleItemCount == 2;
        boolean needsExpansion = false;
        while (expandableItemCount > 0 && cellsRemaining > 0) {
            int minCells = Integer.MAX_VALUE;
            long minCellsAt = 0;
            int minCellsItemCount = 0;
            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (!lp.expandable) continue;
                if (lp.cellsUsed < minCells) {
                    minCells = lp.cellsUsed;
                    minCellsAt = 1L << i;
                    minCellsItemCount = 1;
                } else if (lp.cellsUsed == minCells) {
                    minCellsAt |= 1L << i;
                    minCellsItemCount++;
                }
            }
            smallestItemsAt |= minCellsAt;
            if (minCellsItemCount > cellsRemaining) break;
            minCells++;
            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if ((minCellsAt & (1 << i)) == 0) {
                    if (lp.cellsUsed == minCells) smallestItemsAt |= 1 << i;
                    continue;
                }
                if (centerSingleExpandedItem && lp.preventEdgeOffset && cellsRemaining == 1) {
                    child.setPadding(mGeneratedItemPadding + cellSize, 0, mGeneratedItemPadding, 0);
                }
                lp.cellsUsed++;
                lp.expanded = true;
                cellsRemaining--;
            }
            needsExpansion = true;
        }
        final boolean singleItem = !hasOverflow && visibleItemCount == 1;
        if (cellsRemaining > 0 && smallestItemsAt != 0
                && (cellsRemaining < visibleItemCount - 1 || singleItem || maxCellsUsed > 1)) {
            float expandCount = Long.bitCount(smallestItemsAt);
            if (!singleItem) {
                if ((smallestItemsAt & 1) != 0) {
                    LayoutParams lp = (LayoutParams) getChildAt(0).getLayoutParams();
                    if (!lp.preventEdgeOffset) expandCount -= 0.5f;
                }
                if ((smallestItemsAt & (1 << (childCount - 1))) != 0) {
                    LayoutParams lp = ((LayoutParams) getChildAt(childCount - 1).getLayoutParams());
                    if (!lp.preventEdgeOffset) expandCount -= 0.5f;
                }
            }
            final int extraPixels = expandCount > 0 ? (int) (cellsRemaining * cellSize / expandCount) : 0;
            for (int i = 0; i < childCount; i++) {
                if ((smallestItemsAt & (1 << i)) == 0) continue;
                final View child = getChildAt(i);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (child instanceof ActionMenuItemView) {
                    lp.extraPixels = extraPixels;
                    lp.expanded = true;
                    if (i == 0 && !lp.preventEdgeOffset) lp.leftMargin = -extraPixels / 2;
                    needsExpansion = true;
                } else if (lp.isOverflowButton) {
                    lp.extraPixels = extraPixels;
                    lp.expanded = true;
                    lp.rightMargin = -extraPixels / 2;
                    needsExpansion = true;
                } else {
                    if (i != 0) lp.leftMargin = extraPixels / 2;
                    if (i != childCount - 1) lp.rightMargin = extraPixels / 2;
                }
            }
            cellsRemaining = 0;
        }
        if (needsExpansion) {
            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (!lp.expanded) continue;
                final int width = lp.cellsUsed * cellSize + lp.extraPixels;
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), itemHeightSpec);
            }
        }
        if (heightMode != MeasureSpec.EXACTLY) heightSize = maxChildHeight;
        setMeasuredDimension(widthSize, heightSize);
    }

    static int measureChildForCells(View child, int cellSize, int cellsRemaining, int parentHeightMeasureSpec,
            int parentHeightPadding) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int childHeightSize = MeasureSpec.getSize(parentHeightMeasureSpec) - parentHeightPadding;
        final int childHeightMode = MeasureSpec.getMode(parentHeightMeasureSpec);
        final int childHeightSpec = MeasureSpec.makeMeasureSpec(childHeightSize, childHeightMode);
        final ActionMenuItemView itemView = child instanceof ActionMenuItemView ? (ActionMenuItemView) child : null;
        final boolean hasText = itemView != null && itemView.hasText();
        int cellsUsed = 0;
        if (cellsRemaining > 0 && (!hasText || cellsRemaining >= 2)) {
            final int childWidthSpec = MeasureSpec.makeMeasureSpec(cellSize * cellsRemaining, MeasureSpec.AT_MOST);
            child.measure(childWidthSpec, childHeightSpec);
            final int measuredWidth = child.getMeasuredWidth();
            cellsUsed = measuredWidth / cellSize;
            if (measuredWidth % cellSize != 0) cellsUsed++;
            if (hasText && cellsUsed < 2) cellsUsed = 2;
        }
        final boolean expandable = !lp.isOverflowButton && hasText;
        lp.expandable = expandable;
        lp.cellsUsed = cellsUsed;
        final int targetWidth = cellsUsed * cellSize;
        child.measure(MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY), childHeightSpec);
        return cellsUsed;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (!mFormatItems) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }
        final int childCount = getChildCount();
        final int midVertical = (bottom - top) / 2;
        final int dividerWidth = getDividerWidth();
        int overflowWidth = 0;
        int nonOverflowWidth = 0;
        int nonOverflowCount = 0;
        int widthRemaining = right - left - getPaddingRight() - getPaddingLeft();
        boolean hasOverflow = false;
        final boolean isLayoutRtl = isLayoutRtl();
        for (int i = 0; i < childCount; i++) {
            final View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            LayoutParams p = (LayoutParams) v.getLayoutParams();
            if (p.isOverflowButton) {
                overflowWidth = v.getMeasuredWidth();
                if (hasDividerBeforeChildAt(i)) overflowWidth += dividerWidth;
                int height = v.getMeasuredHeight();
                int r;
                int l;
                if (isLayoutRtl) {
                    l = getPaddingLeft() + p.leftMargin;
                    r = l + overflowWidth;
                } else {
                    r = getWidth() - getPaddingRight() - p.rightMargin;
                    l = r - overflowWidth;
                }
                int t = midVertical - (height / 2);
                int b = t + height;
                v.layout(l, t, r, b);
                widthRemaining -= overflowWidth;
                hasOverflow = true;
            } else {
                final int size = v.getMeasuredWidth() + p.leftMargin + p.rightMargin;
                nonOverflowWidth += size;
                widthRemaining -= size;
                if (hasDividerBeforeChildAt(i)) nonOverflowWidth += dividerWidth;
                nonOverflowCount++;
            }
        }
        if (childCount == 1 && !hasOverflow) {
            final View v = getChildAt(0);
            final int width = v.getMeasuredWidth();
            final int height = v.getMeasuredHeight();
            final int midHorizontal = (right - left) / 2;
            final int l = midHorizontal - width / 2;
            final int t = midVertical - height / 2;
            v.layout(l, t, l + width, t + height);
            return;
        }
        final int spacerCount = nonOverflowCount - (hasOverflow ? 0 : 1);
        final int spacerSize = Math.max(0, spacerCount > 0 ? widthRemaining / spacerCount : 0);
        if (isLayoutRtl) {
            int startRight = getWidth() - getPaddingRight();
            for (int i = 0; i < childCount; i++) {
                final View v = getChildAt(i);
                final LayoutParams lp = (LayoutParams) v.getLayoutParams();
                if (v.getVisibility() == GONE || lp.isOverflowButton) continue;
                startRight -= lp.rightMargin;
                final int width = v.getMeasuredWidth();
                final int height = v.getMeasuredHeight();
                final int t = midVertical - height / 2;
                v.layout(startRight - width, t, startRight, t + height);
                startRight -= width + lp.leftMargin + spacerSize;
            }
        } else {
            int startLeft = getPaddingLeft();
            for (int i = 0; i < childCount; i++) {
                final View v = getChildAt(i);
                final LayoutParams lp = (LayoutParams) v.getLayoutParams();
                if (v.getVisibility() == GONE || lp.isOverflowButton) continue;
                startLeft += lp.leftMargin;
                final int width = v.getMeasuredWidth();
                final int height = v.getMeasuredHeight();
                final int t = midVertical - height / 2;
                v.layout(startLeft, t, startLeft + width, t + height);
                startLeft += width + lp.rightMargin + spacerSize;
            }
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dismissPopupMenus();
    }

    public void setOverflowIcon(Drawable icon) {
        getMenu();
        mPresenter.setOverflowIcon(icon);
    }

    public Drawable getOverflowIcon() {
        getMenu();
        return mPresenter.getOverflowIcon();
    }

    /** framework-internal (hidden in AOSP). */
    public boolean isOverflowReserved() { return mReserveOverflow; }

    /** framework-internal (hidden in AOSP). */
    public void setOverflowReserved(boolean reserveOverflow) { mReserveOverflow = reserveOverflow; }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_VERTICAL;
        return params;
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) { return new LayoutParams(getContext(), attrs); }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        if (p != null) {
            final LayoutParams result = p instanceof LayoutParams ? new LayoutParams((LayoutParams) p)
                    : new LayoutParams(p);
            if (result.gravity <= Gravity.NO_GRAVITY) result.gravity = Gravity.CENTER_VERTICAL;
            return result;
        }
        return generateDefaultLayoutParams();
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p != null && p instanceof LayoutParams; }

    /** framework-internal (hidden in AOSP). */
    public LayoutParams generateOverflowButtonLayoutParams() {
        LayoutParams result = generateDefaultLayoutParams();
        result.isOverflowButton = true;
        return result;
    }

    /** framework-internal (hidden in AOSP). */
    public boolean invokeItem(MenuItemImpl item) { return mMenu.performItemAction(item, 0); }

    /** framework-internal (hidden in AOSP). */
    public int getWindowAnimations() { return 0; }

    /** framework-internal (hidden in AOSP). */
    public void initialize(MenuBuilder menu) { mMenu = menu; }

    public Menu getMenu() {
        if (mMenu == null) {
            final Context context = getContext();
            mMenu = new MenuBuilder(context);
            mMenu.setCallback(new MenuBuilderCallback());
            mPresenter = new ActionMenuPresenter(context);
            mPresenter.setReserveOverflow(true);
            mPresenter.setCallback(mActionMenuPresenterCallback != null ? mActionMenuPresenterCallback
                    : new ActionMenuPresenterCallback());
            mMenu.addMenuPresenter(mPresenter, mPopupContext);
            mPresenter.setMenuView(this);
        }
        return mMenu;
    }

    /** framework-internal (hidden in AOSP). */
    public void setMenuCallbacks(MenuPresenter.Callback pcb, MenuBuilder.Callback mcb) {
        mActionMenuPresenterCallback = pcb;
        mMenuBuilderCallback = mcb;
    }

    /** framework-internal (hidden in AOSP). */
    public MenuBuilder peekMenu() { return mMenu; }

    public boolean showOverflowMenu() { return mPresenter != null && mPresenter.showOverflowMenu(); }

    public boolean hideOverflowMenu() { return mPresenter != null && mPresenter.hideOverflowMenu(); }

    public boolean isOverflowMenuShowing() { return mPresenter != null && mPresenter.isOverflowMenuShowing(); }

    /** framework-internal (hidden in AOSP). */
    public boolean isOverflowMenuShowPending() { return mPresenter != null && mPresenter.isOverflowMenuShowPending(); }

    public void dismissPopupMenus() {
        if (mPresenter != null) mPresenter.dismissPopupMenus();
    }

    @Override
    protected boolean hasDividerBeforeChildAt(int childIndex) {
        if (childIndex == 0) return false;
        final View childBefore = getChildAt(childIndex - 1);
        final View child = getChildAt(childIndex);
        boolean result = false;
        if (childIndex < getChildCount() && childBefore instanceof ActionMenuChildView) {
            result |= ((ActionMenuChildView) childBefore).needsDividerAfter();
        }
        if (childIndex > 0 && child instanceof ActionMenuChildView) {
            result |= ((ActionMenuChildView) child).needsDividerBefore();
        }
        return result;
    }

    /** framework-internal (hidden in AOSP). */
    public void setExpandedActionViewsExclusive(boolean exclusive) { mPresenter.setExpandedActionViewsExclusive(exclusive); }

    public interface OnMenuItemClickListener {
        boolean onMenuItemClick(MenuItem item);
    }

    private class MenuBuilderCallback implements MenuBuilder.Callback {
        public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
            return mOnMenuItemClickListener != null && mOnMenuItemClickListener.onMenuItemClick(item);
        }

        public void onMenuModeChange(MenuBuilder menu) {
            if (mMenuBuilderCallback != null) mMenuBuilderCallback.onMenuModeChange(menu);
        }
    }

    private class ActionMenuPresenterCallback implements MenuPresenter.Callback {
        public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {}

        public boolean onOpenSubMenu(MenuBuilder subMenu) { return false; }
    }

    /** framework-internal (hidden in AOSP). */
    public interface ActionMenuChildView {
        boolean needsDividerBefore();

        boolean needsDividerAfter();
    }

    public static class LayoutParams extends LinearLayout.LayoutParams {
        /** framework-internal (hidden in AOSP). */
        public boolean isOverflowButton;
        public int cellsUsed;
        public int extraPixels;
        public boolean expandable;
        public boolean preventEdgeOffset;
        boolean expanded;

        public LayoutParams(Context c, AttributeSet attrs) { super(c, attrs); }

        public LayoutParams(ViewGroup.LayoutParams other) { super(other); }

        public LayoutParams(LayoutParams other) {
            super((LinearLayout.LayoutParams) other);
            isOverflowButton = other.isOverflowButton;
        }

        public LayoutParams(int width, int height) {
            super(width, height);
            isOverflowButton = false;
        }

        /** framework-internal (hidden in AOSP). */
        public LayoutParams(int width, int height, boolean isOverflowButton) {
            super(width, height);
            this.isOverflowButton = isOverflowButton;
        }
    }
}
