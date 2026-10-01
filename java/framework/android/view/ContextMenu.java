package android.view;

public interface ContextMenu extends Menu {
    ContextMenu setHeaderTitle(int p0);
    ContextMenu setHeaderTitle(CharSequence p0);
    ContextMenu setHeaderIcon(int p0);
    ContextMenu setHeaderIcon(android.graphics.drawable.Drawable p0);
    ContextMenu setHeaderView(View p0);
    void clearHeader();
    public interface ContextMenuInfo {
    }
}
