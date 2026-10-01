package android.view;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** Inflates menu XML (&lt;menu&gt;, &lt;group&gt;, &lt;item&gt;) into a Menu (port of AOSP MenuInflater). */
public class MenuInflater {
    private static final String LOG_TAG = "MenuInflater";
    private static final String XML_MENU = "menu";
    private static final String XML_GROUP = "group";
    private static final String XML_ITEM = "item";
    private static final int NO_ID = 0;

    private static final Class<?>[] ACTION_VIEW_CONSTRUCTOR_SIGNATURE = new Class<?>[] {Context.class};
    private static final Class<?>[] ACTION_PROVIDER_CONSTRUCTOR_SIGNATURE = ACTION_VIEW_CONSTRUCTOR_SIGNATURE;

    private final Object[] mActionViewConstructorArguments;
    private final Object[] mActionProviderConstructorArguments;
    private final Context mContext;
    private Object mRealOwner;

    public MenuInflater(Context context) {
        mContext = context;
        mActionViewConstructorArguments = new Object[] {context};
        mActionProviderConstructorArguments = mActionViewConstructorArguments;
    }

    /** framework-internal (hidden in AOSP). */
    public MenuInflater(Context context, Object realOwner) {
        this(context);
        mRealOwner = realOwner;
    }

    public void inflate(int menuRes, Menu menu) {
        XmlResourceParser parser = null;
        try {
            parser = mContext.getResources().getLayout(menuRes);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            parseMenu(parser, attrs, menu);
        } catch (XmlPullParserException e) {
            throw new InflateException("Error inflating menu XML", e);
        } catch (IOException e) {
            throw new InflateException("Error inflating menu XML", e);
        } finally {
            if (parser != null) parser.close();
        }
    }

    private void parseMenu(XmlPullParser parser, AttributeSet attrs, Menu menu)
            throws XmlPullParserException, IOException {
        MenuState menuState = new MenuState(menu);
        int eventType = parser.getEventType();
        String tagName;
        boolean lookingForEndOfUnknownTag = false;
        String unknownTagName = null;
        do {
            if (eventType == XmlPullParser.START_TAG) {
                tagName = parser.getName();
                if (tagName.equals(XML_MENU)) {
                    eventType = parser.next();
                    break;
                }
                throw new RuntimeException("Expecting menu, got " + tagName);
            }
            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);

        boolean reachedEndOfMenu = false;
        while (!reachedEndOfMenu) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    if (lookingForEndOfUnknownTag) break;
                    tagName = parser.getName();
                    if (tagName.equals(XML_GROUP)) {
                        menuState.readGroup(attrs);
                    } else if (tagName.equals(XML_ITEM)) {
                        menuState.readItem(attrs);
                    } else if (tagName.equals(XML_MENU)) {
                        SubMenu subMenu = menuState.addSubMenuItem();
                        parseMenu(parser, attrs, subMenu);
                    } else {
                        lookingForEndOfUnknownTag = true;
                        unknownTagName = tagName;
                    }
                    break;
                case XmlPullParser.END_TAG:
                    tagName = parser.getName();
                    if (lookingForEndOfUnknownTag && tagName.equals(unknownTagName)) {
                        lookingForEndOfUnknownTag = false;
                        unknownTagName = null;
                    } else if (tagName.equals(XML_GROUP)) {
                        menuState.resetGroup();
                    } else if (tagName.equals(XML_ITEM)) {
                        if (!menuState.hasAddedItem()) {
                            if (menuState.itemActionProvider != null && menuState.itemActionProvider.hasSubMenu()) {
                                menuState.addSubMenuItem();
                            } else {
                                menuState.addItem();
                            }
                        }
                    } else if (tagName.equals(XML_MENU)) {
                        reachedEndOfMenu = true;
                    }
                    break;
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document");
            }
            eventType = parser.next();
        }
    }

    private Object getRealOwner() {
        if (mRealOwner == null) mRealOwner = findRealOwner(mContext);
        return mRealOwner;
    }

    private Object findRealOwner(Object owner) {
        if (owner instanceof android.app.Activity) return owner;
        if (owner instanceof ContextWrapper) return findRealOwner(((ContextWrapper) owner).getBaseContext());
        return owner;
    }

    private static class InflatedOnMenuItemClickListener implements MenuItem.OnMenuItemClickListener {
        private static final Class<?>[] PARAM_TYPES = new Class<?>[] {MenuItem.class};
        private final Object mRealOwner;
        private final Method mMethod;

        InflatedOnMenuItemClickListener(Object realOwner, String methodName) {
            mRealOwner = realOwner;
            Class<?> c = realOwner.getClass();
            try {
                mMethod = c.getMethod(methodName, PARAM_TYPES);
            } catch (Exception e) {
                InflateException ex = new InflateException("Couldn't resolve menu item onClick handler "
                        + methodName + " in class " + c.getName());
                ex.initCause(e);
                throw ex;
            }
        }

        public boolean onMenuItemClick(MenuItem item) {
            try {
                if (mMethod.getReturnType() == Boolean.TYPE) return (Boolean) mMethod.invoke(mRealOwner, item);
                mMethod.invoke(mRealOwner, item);
                return true;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final int[] GROUP_ATTRS = {android.R.attr.id, android.R.attr.menuCategory,
        android.R.attr.orderInCategory, android.R.attr.checkableBehavior, android.R.attr.visible,
        android.R.attr.enabled};

    private static final int[] ITEM_ATTRS = {android.R.attr.id, android.R.attr.menuCategory,
        android.R.attr.orderInCategory, android.R.attr.title, android.R.attr.titleCondensed, android.R.attr.icon,
        android.R.attr.alphabeticShortcut, android.R.attr.numericShortcut, android.R.attr.checkable,
        android.R.attr.checked, android.R.attr.visible, android.R.attr.enabled, android.R.attr.showAsAction,
        android.R.attr.actionLayout, android.R.attr.actionViewClass, android.R.attr.actionProviderClass,
        android.R.attr.onClick, android.R.attr.iconTint, android.R.attr.contentDescription,
        android.R.attr.tooltipText, android.R.attr.alphabeticModifiers, android.R.attr.numericModifiers};

    private class MenuState {
        private final Menu menu;
        private int groupId;
        private int groupCategory;
        private int groupOrder;
        private int groupCheckable;
        private boolean groupVisible;
        private boolean groupEnabled;
        private boolean itemAdded;
        private int itemId;
        private int itemCategoryOrder;
        private CharSequence itemTitle;
        private CharSequence itemTitleCondensed;
        private int itemIconResId;
        private ColorStateList itemIconTintList;
        private char itemAlphabeticShortcut;
        private int itemAlphabeticModifiers;
        private char itemNumericShortcut;
        private int itemNumericModifiers;
        private int itemCheckable;
        private boolean itemChecked;
        private boolean itemVisible;
        private boolean itemEnabled;
        private int itemShowAsAction;
        private int itemActionViewLayout;
        private String itemActionViewClassName;
        private String itemActionProviderClassName;
        private String itemListenerMethodName;
        ActionProvider itemActionProvider;
        private CharSequence itemContentDescription;
        private CharSequence itemTooltipText;

        private static final int defaultGroupId = NO_ID;
        private static final int defaultItemId = NO_ID;
        private static final int defaultItemCategory = 0;
        private static final int defaultItemOrder = 0;
        private static final int defaultItemCheckable = 0;
        private static final boolean defaultItemChecked = false;
        private static final boolean defaultItemVisible = true;
        private static final boolean defaultItemEnabled = true;

        MenuState(final Menu menu) {
            this.menu = menu;
            resetGroup();
        }

        void resetGroup() {
            groupId = defaultGroupId;
            groupCategory = defaultItemCategory;
            groupOrder = defaultItemOrder;
            groupCheckable = defaultItemCheckable;
            groupVisible = defaultItemVisible;
            groupEnabled = defaultItemEnabled;
        }

        void readGroup(AttributeSet attrs) {
            TypedArray a = mContext.obtainStyledAttributes(attrs, GROUP_ATTRS);
            groupId = a.getResourceId(0, defaultGroupId);
            groupCategory = a.getInt(1, defaultItemCategory);
            groupOrder = a.getInt(2, defaultItemOrder);
            groupCheckable = a.getInt(3, defaultItemCheckable);
            groupVisible = a.getBoolean(4, defaultItemVisible);
            groupEnabled = a.getBoolean(5, defaultItemEnabled);
            a.recycle();
        }

        void readItem(AttributeSet attrs) {
            TypedArray a = mContext.obtainStyledAttributes(attrs, ITEM_ATTRS);
            itemId = a.getResourceId(0, defaultItemId);
            final int category = a.getInt(1, groupCategory);
            final int order = a.getInt(2, groupOrder);
            itemCategoryOrder = (category & 0xffff0000) | (order & 0x0000ffff);
            itemTitle = a.getText(3);
            itemTitleCondensed = a.getText(4);
            itemIconResId = a.getResourceId(5, 0);
            itemAlphabeticShortcut = getShortcut(a.getString(6));
            itemAlphabeticModifiers = a.getInt(20, KeyEvent.META_CTRL_ON);
            itemNumericShortcut = getShortcut(a.getString(7));
            itemNumericModifiers = a.getInt(21, KeyEvent.META_CTRL_ON);
            if (a.hasValue(8)) itemCheckable = a.getBoolean(8, false) ? 1 : 0;
            else itemCheckable = groupCheckable;
            itemChecked = a.getBoolean(9, defaultItemChecked);
            itemVisible = a.getBoolean(10, groupVisible);
            itemEnabled = a.getBoolean(11, groupEnabled);
            itemShowAsAction = a.getInt(12, -1);
            itemListenerMethodName = a.getString(16);
            itemActionViewLayout = a.getResourceId(13, 0);
            itemActionViewClassName = a.getString(14);
            itemActionProviderClassName = a.getString(15);
            final boolean hasActionProvider = itemActionProviderClassName != null;
            if (hasActionProvider && itemActionViewLayout == 0 && itemActionViewClassName == null) {
                itemActionProvider = newInstance(itemActionProviderClassName, ACTION_PROVIDER_CONSTRUCTOR_SIGNATURE,
                        mActionProviderConstructorArguments);
            } else {
                itemActionProvider = null;
            }
            itemContentDescription = a.getText(18);
            itemTooltipText = a.getText(19);
            itemIconTintList = a.hasValue(17) ? a.getColorStateList(17) : null;
            a.recycle();
            itemAdded = false;
        }

        private char getShortcut(String shortcutString) {
            if (shortcutString == null) return 0;
            return shortcutString.charAt(0);
        }

        private void setItem(MenuItem item) {
            item.setChecked(itemChecked).setVisible(itemVisible).setEnabled(itemEnabled)
                    .setCheckable(itemCheckable >= 1).setTitleCondensed(itemTitleCondensed).setIcon(itemIconResId)
                    .setAlphabeticShortcut(itemAlphabeticShortcut, itemAlphabeticModifiers)
                    .setNumericShortcut(itemNumericShortcut, itemNumericModifiers);
            if (itemShowAsAction >= 0) item.setShowAsAction(itemShowAsAction);
            if (itemIconTintList != null) item.setIconTintList(itemIconTintList);
            if (itemListenerMethodName != null) {
                if (mContext.isRestricted()) {
                    throw new IllegalStateException("The android:onClick attribute cannot be used within a restricted context");
                }
                item.setOnMenuItemClickListener(new InflatedOnMenuItemClickListener(getRealOwner(),
                        itemListenerMethodName));
            }
            if (item instanceof com.android.internal.view.menu.MenuItemImpl && itemCheckable >= 2) {
                ((com.android.internal.view.menu.MenuItemImpl) item).setExclusiveCheckable(true);
            } else if (itemCheckable >= 2) {
                menu.setGroupCheckable(groupId, true, true);
            }
            boolean actionViewSpecified = false;
            if (itemActionViewClassName != null) {
                View actionView = (View) newInstance(itemActionViewClassName, ACTION_VIEW_CONSTRUCTOR_SIGNATURE,
                        mActionViewConstructorArguments);
                item.setActionView(actionView);
                actionViewSpecified = true;
            }
            if (itemActionViewLayout > 0) {
                if (!actionViewSpecified) {
                    item.setActionView(itemActionViewLayout);
                } else {
                    Log.w(LOG_TAG, "Ignoring attribute 'itemActionViewLayout'. Action view already specified.");
                }
            }
            if (itemActionProvider != null) item.setActionProvider(itemActionProvider);
            item.setContentDescription(itemContentDescription);
            item.setTooltipText(itemTooltipText);
        }

        MenuItem addItem() {
            itemAdded = true;
            MenuItem item = menu.add(groupId, itemId, itemCategoryOrder, itemTitle);
            setItem(item);
            return item;
        }

        SubMenu addSubMenuItem() {
            itemAdded = true;
            SubMenu subMenu = menu.addSubMenu(groupId, itemId, itemCategoryOrder, itemTitle);
            setItem(subMenu.getItem());
            return subMenu;
        }

        boolean hasAddedItem() { return itemAdded; }

        @SuppressWarnings("unchecked")
        private <T> T newInstance(String className, Class<?>[] constructorSignature, Object[] arguments) {
            try {
                Class<?> clazz = mContext.getClassLoader().loadClass(className);
                Constructor<?> constructor = clazz.getConstructor(constructorSignature);
                constructor.setAccessible(true);
                return (T) constructor.newInstance(arguments);
            } catch (Exception e) {
                Log.w(LOG_TAG, "Cannot instantiate class: " + className, e);
            }
            return null;
        }
    }
}
