package com.android.internal.view.menu;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import com.android.internal.util.InternalRes;

/**
 * framework-internal. Shows a menu (context menu or sub menu) as an
 * AlertDialog list (port of AOSP MenuDialogHelper).
 */
public class MenuDialogHelper implements DialogInterface.OnKeyListener, DialogInterface.OnClickListener,
        DialogInterface.OnDismissListener, MenuBuilder.CloseListener {
    private final MenuBuilder mMenu;
    private AlertDialog mDialog;
    private MenuAdapter mAdapter;
    private MenuBuilder.CloseListener mPresenterCallback;
    private boolean mOpenedSubMenu;

    public MenuDialogHelper(MenuBuilder menu) { mMenu = menu; }

    public void setPresenterCallback(MenuBuilder.CloseListener cb) { mPresenterCallback = cb; }

    public void show(IBinder windowToken) {
        final MenuBuilder menu = mMenu;
        final AlertDialog.Builder builder = new AlertDialog.Builder(menu.getContext());
        mAdapter = new MenuAdapter(menu, LayoutInflater.from(builder.getContext()), false,
                InternalRes.layout("list_menu_item_layout"));
        menu.addCloseListener(this);
        builder.setAdapter(mAdapter, this);
        View headerView = null;
        CharSequence headerTitle = null;
        android.graphics.drawable.Drawable headerIcon = null;
        if (menu instanceof ContextMenuBuilder) {
            headerView = ((ContextMenuBuilder) menu).getHeaderView();
            headerTitle = ((ContextMenuBuilder) menu).getHeaderTitle();
            headerIcon = ((ContextMenuBuilder) menu).getHeaderIcon();
        } else if (menu instanceof SubMenuBuilder) {
            headerTitle = ((SubMenuBuilder) menu).getItem().getTitle();
        }
        if (headerView != null) {
            builder.setCustomTitle(headerView);
        } else {
            builder.setIcon(headerIcon).setTitle(headerTitle);
        }
        builder.setOnKeyListener(this);
        mDialog = builder.create();
        mDialog.setOnDismissListener(this);
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        if (windowToken != null) lp.token = windowToken;
        lp.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        mDialog.show();
    }

    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                mMenu.close(true);
                dialog.dismiss();
                return true;
            }
        }
        return mMenu.performShortcut(keyCode, event, 0);
    }

    public void dismiss() {
        if (mDialog != null) mDialog.dismiss();
    }

    public void onDismiss(DialogInterface dialog) {
        mMenu.removeCloseListener(this);
        if (mAdapter != null) mAdapter.detach();
        if (mPresenterCallback != null) mPresenterCallback.onMenuClosed(mMenu, true);
        // With a sub menu open, the root menu closes when the sub menu does.
        if (!mOpenedSubMenu && !(mMenu instanceof SubMenuBuilder)) mMenu.close(true);
    }

    public void onMenuClosed(MenuBuilder menu, boolean allMenusAreClosing) {
        if (allMenusAreClosing || menu == mMenu) dismiss();
    }

    public void onClick(DialogInterface dialog, int which) {
        MenuItemImpl item = mAdapter.getItem(which);
        if (item.hasSubMenu()) {
            item.invoke();
            mOpenedSubMenu = true;
            MenuDialogHelper sub = new MenuDialogHelper((MenuBuilder) item.getSubMenu());
            sub.setPresenterCallback(new MenuBuilder.CloseListener() {
                public void onMenuClosed(MenuBuilder menu, boolean allMenusAreClosing) { mMenu.close(true); }
            });
            sub.show(null);
            return;
        }
        mMenu.performItemAction(item, 0);
    }
}
