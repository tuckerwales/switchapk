package com.android.internal.view.menu;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import com.android.internal.util.InternalRes;

/**
 * framework-internal. Shows the options menu as a Material overflow-style
 * popup in the top end corner of the screen (what AOSP shows from the action
 * bar's overflow button; this tree has no action bar decor yet). Opened by
 * the MENU key, which the controller's + button falls back to.
 */
public class MenuPanel implements AdapterView.OnItemClickListener, MenuBuilder.CloseListener,
        DialogInterface.OnDismissListener, DialogInterface.OnKeyListener {
    private final Context mContext;
    private final MenuBuilder mMenu;
    private final MenuBuilder.CloseListener mCallback;
    private Dialog mDialog;
    private MenuAdapter mAdapter;
    private boolean mOpenedSubMenu;

    public MenuPanel(Context context, MenuBuilder menu, MenuBuilder.CloseListener callback) {
        mContext = context;
        mMenu = menu;
        mCallback = callback;
    }

    public boolean isShowing() { return mDialog != null && mDialog.isShowing(); }

    public void show() {
        mDialog = new Dialog(mContext);
        final Context themed = mDialog.getContext();
        final LayoutInflater inflater = LayoutInflater.from(themed);
        mAdapter = new MenuAdapter(mMenu, inflater, true, InternalRes.layout("popup_menu_item_layout"));
        final ListView list = new ListView(themed);
        list.setAdapter(mAdapter);
        list.setOnItemClickListener(this);
        list.setDivider(null);
        list.setSelector(resolveDrawable(themed, android.R.attr.listChoiceBackgroundIndicator));
        final DisplayMetrics dm = themed.getResources().getDisplayMetrics();
        final int width = measureContentWidth(list, mAdapter, dm);
        final int vpad = (int) (8 * dm.density);
        list.setPadding(0, vpad, 0, vpad);
        list.setClipToPadding(false);
        FrameLayout frame = new FrameLayout(themed);
        frame.addView(list, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setContentView(frame);
        mDialog.setCanceledOnTouchOutside(true);
        mDialog.setOnDismissListener(this);
        mDialog.setOnKeyListener(this);
        final Window w = mDialog.getWindow();
        Drawable bg = resolveDrawable(themed, android.R.attr.popupBackground);
        if (bg != null) w.setBackgroundDrawable(bg);
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setGravity(Gravity.TOP | Gravity.END);
        w.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.x = (int) (8 * dm.density);
        lp.y = (int) (8 * dm.density);
        w.setAttributes(lp);
        mMenu.addCloseListener(this);
        mDialog.show();
    }

    /** AOSP MenuPopup.measureIndividualMenuWidth: widest row, at most half the screen. */
    private static int measureContentWidth(ListView parent, MenuAdapter adapter, DisplayMetrics dm) {
        int maxWidth = 0;
        View itemView = null;
        final int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        FrameLayout measureParent = new FrameLayout(parent.getContext());
        for (int i = 0; i < adapter.getCount(); i++) {
            itemView = adapter.getView(i, null, measureParent);
            itemView.measure(widthMeasureSpec, heightMeasureSpec);
            maxWidth = Math.max(maxWidth, itemView.getMeasuredWidth());
        }
        final int maxAllowed = Math.max(dm.widthPixels / 2, (int) (280 * dm.density));
        return Math.min(maxWidth, maxAllowed);
    }

    private static Drawable resolveDrawable(Context context, int attr) {
        TypedArray a = context.obtainStyledAttributes(new int[] {attr});
        Drawable d = a.getDrawable(0);
        a.recycle();
        return d;
    }

    public void dismiss() {
        if (mDialog != null) mDialog.dismiss();
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        MenuItemImpl item = mAdapter.getItem(position);
        if (item.hasSubMenu()) {
            item.invoke();
            mOpenedSubMenu = true;
            MenuDialogHelper sub = new MenuDialogHelper((MenuBuilder) item.getSubMenu());
            sub.setPresenterCallback(new MenuBuilder.CloseListener() {
                public void onMenuClosed(MenuBuilder menu, boolean allMenusAreClosing) { mMenu.close(true); }
            });
            dismiss();
            sub.show(null);
            return;
        }
        mMenu.performItemAction(item, 0);
    }

    public void onMenuClosed(MenuBuilder menu, boolean allMenusAreClosing) {
        if (allMenusAreClosing || menu == mMenu) dismiss();
    }

    public void onDismiss(DialogInterface dialog) {
        mMenu.removeCloseListener(this);
        if (mAdapter != null) mAdapter.detach();
        if (!mOpenedSubMenu) mMenu.close(true);
        if (mCallback != null && !mOpenedSubMenu) mCallback.onMenuClosed(mMenu, true);
    }

    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) dismiss();
            return true;
        }
        return mMenu.performShortcut(keyCode, event, 0);
    }
}
