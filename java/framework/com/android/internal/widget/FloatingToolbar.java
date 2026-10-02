package com.android.internal.widget;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import com.android.internal.view.menu.MenuBuilder;
import com.android.internal.view.menu.MenuItemImpl;
import java.util.ArrayList;

/**
 * framework-internal. The floating action mode toolbar: a horizontal row of
 * menu items in a popup, placed above the content rect when it fits.
 * Overflow into a second panel is not provided; items that do not fit the
 * screen stay on the row and the window is clamped on screen.
 */
public class FloatingToolbar {
    private static final String TAG = "FloatingActionMode";
    private static final int BAR_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFF212121;

    private final Context mContext;
    private final PopupWindow mPopup;
    private final LinearLayout mBar;
    private MenuBuilder mMenu;

    public FloatingToolbar(Context context) {
        mContext = context;
        mBar = new LinearLayout(context);
        mBar.setOrientation(LinearLayout.HORIZONTAL);
        mBar.setBackground(new ColorDrawable(BAR_COLOR));
        mPopup = new PopupWindow(context);
        mPopup.setContentView(mBar);
        mPopup.setFocusable(false);
        mPopup.setOutsideTouchable(true);
        mPopup.setTouchModal(false);
        mPopup.setElevation(dp(8));
    }

    public void setMenu(MenuBuilder menu) { mMenu = menu; }

    public void dismiss() {
        if (mPopup.isShowing()) mPopup.dismiss();
    }

    public boolean isShowing() { return mPopup.isShowing(); }

    /** {@code contentRect} is in {@code anchor} coordinates. */
    public void show(View anchor, Rect contentRect) {
        if (anchor == null || mMenu == null) return;
        rebuild();
        if (mBar.getChildCount() == 0) {
            dismiss();
            return;
        }
        mBar.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int width = mBar.getMeasuredWidth();
        int height = mBar.getMeasuredHeight();
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int left = loc[0] + contentRect.left;
        int top = loc[1] + contentRect.top;
        int bottom = loc[1] + contentRect.bottom;
        int gap = dp(8);
        int x = left;
        int y = top - gap - height;
        if (y < 0) y = bottom + gap;
        mPopup.setWidth(width);
        mPopup.setHeight(height);
        if (mPopup.isShowing()) mPopup.update(x, y, width, height);
        else mPopup.showAtLocation(anchor, Gravity.TOP | Gravity.LEFT, x, y);
        Log.i(TAG, "FLOATPOS " + x + " " + y + " " + width + " " + height);
    }

    private void rebuild() {
        mBar.removeAllViews();
        ArrayList<MenuItemImpl> items = mMenu.getVisibleItems();
        for (int i = 0; i < items.size(); i++) {
            final MenuItem item = items.get(i);
            TextView label = new TextView(mContext);
            label.setText(item.getTitle());
            label.setTextColor(TEXT_COLOR);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            label.setGravity(Gravity.CENTER);
            label.setMinimumWidth(dp(88));
            label.setPadding(dp(16), 0, dp(16), 0);
            label.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { mMenu.performItemAction(item, MenuBuilder.FLAG_PERFORM_NO_CLOSE); }
            });
            mBar.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                mContext.getResources().getDisplayMetrics()));
    }
}
