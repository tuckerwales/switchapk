package com.android.internal.widget;

import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.Menu;
import android.view.Window;
import com.android.internal.view.menu.MenuPresenter;

/**
 * framework-internal. Port of AOSP DecorContentParent: what PhoneWindow needs
 * from the decor layout that hosts the action bar.
 */
public interface DecorContentParent {
    void setWindowCallback(Window.Callback cb);

    void setWindowTitle(CharSequence title);

    CharSequence getTitle();

    void initFeature(int windowFeature);

    void setUiOptions(int uiOptions);

    boolean hasIcon();

    boolean hasLogo();

    void setIcon(int resId);

    void setIcon(Drawable d);

    void setLogo(int resId);

    boolean canShowOverflowMenu();

    boolean isOverflowMenuShowing();

    boolean isOverflowMenuShowPending();

    boolean showOverflowMenu();

    boolean hideOverflowMenu();

    void setMenuPrepared();

    void setMenu(Menu menu, MenuPresenter.Callback cb);

    void saveToolbarHierarchyState(SparseArray<Parcelable> toolbarStates);

    void restoreToolbarHierarchyState(SparseArray<Parcelable> toolbarStates);

    void dismissPopups();
}
