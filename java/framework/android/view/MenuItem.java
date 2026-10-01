package android.view;

public interface MenuItem {
    int SHOW_AS_ACTION_ALWAYS = 2;
    int SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW = 8;
    int SHOW_AS_ACTION_IF_ROOM = 1;
    int SHOW_AS_ACTION_NEVER = 0;
    int SHOW_AS_ACTION_WITH_TEXT = 4;
    int getItemId();
    int getGroupId();
    int getOrder();
    MenuItem setTitle(CharSequence p0);
    MenuItem setTitle(int p0);
    CharSequence getTitle();
    MenuItem setTitleCondensed(CharSequence p0);
    CharSequence getTitleCondensed();
    MenuItem setIcon(android.graphics.drawable.Drawable p0);
    MenuItem setIcon(int p0);
    android.graphics.drawable.Drawable getIcon();
    default MenuItem setIconTintList(android.content.res.ColorStateList p0) { return this; }
    default android.content.res.ColorStateList getIconTintList() { return null; }
    default MenuItem setIconTintMode(android.graphics.PorterDuff.Mode p0) { return this; }
    default MenuItem setIconTintBlendMode(android.graphics.BlendMode p0) { return this; }
    default android.graphics.PorterDuff.Mode getIconTintMode() { return null; }
    default android.graphics.BlendMode getIconTintBlendMode() { return null; }
    MenuItem setIntent(android.content.Intent p0);
    android.content.Intent getIntent();
    MenuItem setShortcut(char p0, char p1);
    default MenuItem setShortcut(char p0, char p1, int p2, int p3) { return this; }
    MenuItem setNumericShortcut(char p0);
    default MenuItem setNumericShortcut(char p0, int p1) { return this; }
    char getNumericShortcut();
    default int getNumericModifiers() { return KeyEvent.META_CTRL_ON; }
    MenuItem setAlphabeticShortcut(char p0);
    default MenuItem setAlphabeticShortcut(char p0, int p1) { return this; }
    char getAlphabeticShortcut();
    default int getAlphabeticModifiers() { return KeyEvent.META_CTRL_ON; }
    MenuItem setCheckable(boolean p0);
    boolean isCheckable();
    MenuItem setChecked(boolean p0);
    boolean isChecked();
    MenuItem setVisible(boolean p0);
    boolean isVisible();
    MenuItem setEnabled(boolean p0);
    boolean isEnabled();
    boolean hasSubMenu();
    SubMenu getSubMenu();
    MenuItem setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener p0);
    ContextMenu.ContextMenuInfo getMenuInfo();
    void setShowAsAction(int p0);
    MenuItem setShowAsActionFlags(int p0);
    MenuItem setActionView(View p0);
    MenuItem setActionView(int p0);
    View getActionView();
    MenuItem setActionProvider(ActionProvider p0);
    ActionProvider getActionProvider();
    boolean expandActionView();
    boolean collapseActionView();
    boolean isActionViewExpanded();
    MenuItem setOnActionExpandListener(MenuItem.OnActionExpandListener p0);
    default MenuItem setContentDescription(CharSequence p0) { return this; }
    default CharSequence getContentDescription() { return null; }
    default MenuItem setTooltipText(CharSequence p0) { return this; }
    default CharSequence getTooltipText() { return null; }
    public interface OnMenuItemClickListener {
        boolean onMenuItemClick(MenuItem p0);
    }

    public interface OnActionExpandListener {
        boolean onMenuItemActionExpand(MenuItem p0);
        boolean onMenuItemActionCollapse(MenuItem p0);
    }
}
