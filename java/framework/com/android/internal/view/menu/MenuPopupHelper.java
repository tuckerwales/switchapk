package com.android.internal.view.menu;

import android.content.Context;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.ListPopupWindow;
import android.widget.PopupWindow;
import com.android.internal.util.InternalRes;

/**
 * framework-internal. AOSP MenuPopupHelper with StandardMenuPopup folded in:
 * a menu as a ListPopupWindow anchored to a view (PopupMenu). A sub menu
 * replaces its parent in a new popup on the same anchor.
 */
public class MenuPopupHelper implements AdapterView.OnItemClickListener, MenuBuilder.CloseListener,
        PopupWindow.OnDismissListener, View.OnKeyListener {
    private final Context mContext;
    private final MenuBuilder mMenu;
    private final boolean mOverflowOnly;
    private final int mPopupStyleAttr;
    private final int mPopupStyleRes;
    private final int mPopupMaxWidth;
    private View mAnchorView;
    private int mDropDownGravity = Gravity.START;
    private boolean mForceShowIcon;
    private ListPopupWindow mPopup;
    private MenuAdapter mAdapter;
    private PopupWindow.OnDismissListener mOnDismissListener;
    private MenuBuilder.CloseListener mPresenterCallback;
    private boolean mOpenedSubMenu;

    public MenuPopupHelper(Context context, MenuBuilder menu, View anchorView, boolean overflowOnly,
            int popupStyleAttr) {
        this(context, menu, anchorView, overflowOnly, popupStyleAttr, 0);
    }

    public MenuPopupHelper(Context context, MenuBuilder menu, View anchorView, boolean overflowOnly,
            int popupStyleAttr, int popupStyleRes) {
        mContext = context;
        mMenu = menu;
        mAnchorView = anchorView;
        mOverflowOnly = overflowOnly;
        mPopupStyleAttr = popupStyleAttr;
        mPopupStyleRes = popupStyleRes;
        final Resources res = context.getResources();
        int prefDialogWidth = InternalRes.dimen("config_prefDialogWidth");
        mPopupMaxWidth = Math.max(res.getDisplayMetrics().widthPixels / 2,
                prefDialogWidth != 0 ? res.getDimensionPixelSize(prefDialogWidth) : 0);
    }

    public void setAnchorView(View anchor) { mAnchorView = anchor; }

    public void setForceShowIcon(boolean forceShowIcon) { mForceShowIcon = forceShowIcon; }

    public void setGravity(int gravity) { mDropDownGravity = gravity; }

    public int getGravity() { return mDropDownGravity; }

    public void setOnDismissListener(PopupWindow.OnDismissListener listener) { mOnDismissListener = listener; }

    /** Told when the whole menu closes (a parent popup hands this to its sub menu). */
    public void setPresenterCallback(MenuBuilder.CloseListener cb) { mPresenterCallback = cb; }

    public void show() {
        if (!tryShow()) throw new IllegalStateException("MenuPopupHelper cannot be used without an anchor");
    }

    public boolean tryShow() {
        if (isShowing()) return true;
        if (mAnchorView == null) return false;
        mPopup = new ListPopupWindow(mContext, null, mPopupStyleAttr, mPopupStyleRes);
        mAdapter = new MenuAdapter(mMenu, LayoutInflater.from(mContext), mOverflowOnly,
                InternalRes.layout("popup_menu_item_layout"));
        mAdapter.setForceShowIcon(mForceShowIcon);
        mPopup.setAdapter(mAdapter);
        mPopup.setOnDismissListener(this);
        mPopup.setOnItemClickListener(this);
        mPopup.setAnchorView(mAnchorView);
        mPopup.setDropDownGravity(mDropDownGravity);
        mPopup.setModal(true);
        mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        mPopup.setContentWidth(measureIndividualMenuWidth(mAdapter, mContext, mPopupMaxWidth));
        mPopup.show();
        mPopup.getListView().setOnKeyListener(this);
        mMenu.addCloseListener(this);
        return true;
    }

    public void dismiss() {
        if (isShowing()) mPopup.dismiss();
    }

    public boolean isShowing() { return mPopup != null && mPopup.isShowing(); }

    public ListPopupWindow getPopup() { return mPopup; }

    /** AOSP MenuPopup.measureIndividualMenuWidth: the widest row, capped at the allowed width. */
    static int measureIndividualMenuWidth(ListAdapter adapter, Context context, int maxAllowedWidth) {
        int maxWidth = 0;
        View itemView = null;
        int itemType = 0;
        final ViewGroup parent = new FrameLayout(context);
        final int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int count = adapter.getCount();
        for (int i = 0; i < count; i++) {
            final int positionType = adapter.getItemViewType(i);
            if (positionType != itemType) {
                itemType = positionType;
                itemView = null;
            }
            itemView = adapter.getView(i, itemView, parent);
            itemView.measure(widthMeasureSpec, heightMeasureSpec);
            final int itemWidth = itemView.getMeasuredWidth();
            if (itemWidth >= maxAllowedWidth) return maxAllowedWidth;
            if (itemWidth > maxWidth) maxWidth = itemWidth;
        }
        return maxWidth;
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final MenuItemImpl item = mAdapter.getItem(position);
        if (item.hasSubMenu()) {
            item.invoke();
            final MenuPopupHelper sub = new MenuPopupHelper(mContext, (MenuBuilder) item.getSubMenu(), mAnchorView,
                    mOverflowOnly, mPopupStyleAttr, mPopupStyleRes);
            sub.setForceShowIcon(mForceShowIcon);
            sub.setGravity(mDropDownGravity);
            sub.setOnDismissListener(mOnDismissListener);
            sub.setPresenterCallback(new MenuBuilder.CloseListener() {
                public void onMenuClosed(MenuBuilder menu, boolean allMenusAreClosing) { mMenu.close(true); }
            });
            mOpenedSubMenu = true;
            dismiss();
            sub.tryShow();
            return;
        }
        mMenu.performItemAction(item, 0);
    }

    public void onMenuClosed(MenuBuilder menu, boolean allMenusAreClosing) {
        if (menu != mMenu && !allMenusAreClosing) return;
        dismiss();
    }

    public void onDismiss() {
        mMenu.removeCloseListener(this);
        if (mAdapter != null) mAdapter.detach();
        mPopup = null;
        if (mOpenedSubMenu) {
            mOpenedSubMenu = false;
            return;
        }
        mMenu.close(true);
        if (mPresenterCallback != null) mPresenterCallback.onMenuClosed(mMenu, true);
        if (mOnDismissListener != null) mOnDismissListener.onDismiss();
    }

    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_MENU) {
            dismiss();
            return true;
        }
        return false;
    }
}
