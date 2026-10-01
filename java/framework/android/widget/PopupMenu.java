package android.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnTouchListener;
import com.android.internal.view.menu.MenuBuilder;
import com.android.internal.view.menu.MenuPopupHelper;

/** Port of AOSP PopupMenu: a menu in a popup anchored to a view. */
public class PopupMenu {
    private final Context mContext;
    private final MenuBuilder mMenu;
    private final View mAnchor;
    private final MenuPopupHelper mPopup;
    private OnMenuItemClickListener mMenuItemClickListener;
    private OnDismissListener mOnDismissListener;

    public interface OnDismissListener {
        void onDismiss(PopupMenu menu);
    }

    public interface OnMenuItemClickListener {
        boolean onMenuItemClick(MenuItem item);
    }

    public PopupMenu(Context context, View anchor) { this(context, anchor, Gravity.NO_GRAVITY); }

    public PopupMenu(Context context, View anchor, int gravity) {
        this(context, anchor, gravity, android.R.attr.popupMenuStyle, 0);
    }

    public PopupMenu(Context context, View anchor, int gravity, int popupStyleAttr, int popupStyleRes) {
        mContext = context;
        mAnchor = anchor;
        mMenu = new MenuBuilder(context);
        mMenu.setCallback(new MenuBuilder.Callback() {
            public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
                return mMenuItemClickListener != null && mMenuItemClickListener.onMenuItemClick(item);
            }

            public void onMenuModeChange(MenuBuilder menu) {}
        });
        mPopup = new MenuPopupHelper(context, mMenu, anchor, false, popupStyleAttr, popupStyleRes);
        mPopup.setGravity(gravity);
        mPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            public void onDismiss() {
                if (mOnDismissListener != null) mOnDismissListener.onDismiss(PopupMenu.this);
            }
        });
    }

    public void setGravity(int gravity) { mPopup.setGravity(gravity); }

    public int getGravity() { return mPopup.getGravity(); }

    /** TODO drag-to-open forwarding is not ported. */
    public OnTouchListener getDragToOpenListener() { return null; }

    public Menu getMenu() { return mMenu; }

    public MenuInflater getMenuInflater() { return new MenuInflater(mContext); }

    public void inflate(int menuRes) { getMenuInflater().inflate(menuRes, mMenu); }

    public void show() { mPopup.show(); }

    public void dismiss() { mPopup.dismiss(); }

    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) { mMenuItemClickListener = listener; }

    public void setOnDismissListener(OnDismissListener listener) { mOnDismissListener = listener; }

    public void setForceShowIcon(boolean forceShowIcon) { mPopup.setForceShowIcon(forceShowIcon); }
}
