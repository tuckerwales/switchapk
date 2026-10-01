package android.view;

public interface Menu {
    int CATEGORY_ALTERNATIVE = 262144;
    int CATEGORY_CONTAINER = 65536;
    int CATEGORY_SECONDARY = 196608;
    int CATEGORY_SYSTEM = 131072;
    int FIRST = 1;
    int FLAG_ALWAYS_PERFORM_CLOSE = 2;
    int FLAG_APPEND_TO_GROUP = 1;
    int FLAG_PERFORM_NO_CLOSE = 1;
    int NONE = 0;
    int SUPPORTED_MODIFIERS_MASK = 69647;
    MenuItem add(CharSequence p0);
    MenuItem add(int p0);
    MenuItem add(int p0, int p1, int p2, CharSequence p3);
    MenuItem add(int p0, int p1, int p2, int p3);
    SubMenu addSubMenu(CharSequence p0);
    SubMenu addSubMenu(int p0);
    SubMenu addSubMenu(int p0, int p1, int p2, CharSequence p3);
    SubMenu addSubMenu(int p0, int p1, int p2, int p3);
    int addIntentOptions(int p0, int p1, int p2, android.content.ComponentName p3, android.content.Intent[] p4, android.content.Intent p5, int p6, MenuItem[] p7);
    void removeItem(int p0);
    void removeGroup(int p0);
    void clear();
    void setGroupCheckable(int p0, boolean p1, boolean p2);
    void setGroupVisible(int p0, boolean p1);
    void setGroupEnabled(int p0, boolean p1);
    boolean hasVisibleItems();
    MenuItem findItem(int p0);
    int size();
    MenuItem getItem(int p0);
    void close();
    boolean performShortcut(int p0, KeyEvent p1, int p2);
    boolean isShortcutKey(int p0, KeyEvent p1);
    boolean performIdentifierAction(int p0, int p1);
    void setQwertyMode(boolean p0);
    default void setGroupDividerEnabled(boolean p0) {  }
}
