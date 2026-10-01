package android.view;

public interface SubMenu extends Menu {
    SubMenu setHeaderTitle(int p0);
    SubMenu setHeaderTitle(CharSequence p0);
    SubMenu setHeaderIcon(int p0);
    SubMenu setHeaderIcon(android.graphics.drawable.Drawable p0);
    SubMenu setHeaderView(View p0);
    void clearHeader();
    SubMenu setIcon(int p0);
    SubMenu setIcon(android.graphics.drawable.Drawable p0);
    MenuItem getItem();
}
