package com.android.internal.view.menu;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import com.android.internal.util.InternalRes;

/**
 * framework-internal. One row of a list menu, inflated from the framework's
 * list_menu_item_layout or popup_menu_item_layout (port of AOSP ListMenuItemView).
 */
public class ListMenuItemView extends LinearLayout implements MenuView.ItemView {
    private MenuItemImpl mItemData;
    private ImageView mIconView;
    private RadioButton mRadioButton;
    private TextView mTitleView;
    private CheckBox mCheckBox;
    private TextView mShortcutView;
    private ImageView mSubMenuArrowView;
    private ImageView mGroupDivider;
    private Drawable mBackground;
    private int mTextAppearance;
    private Context mTextAppearanceContext;
    private boolean mPreserveIconSpacing;
    private boolean mForceShowIcon;
    private int mMenuType;
    private LayoutInflater mInflater;

    public ListMenuItemView(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public ListMenuItemView(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public ListMenuItemView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final TypedArray a = context.obtainStyledAttributes(attrs,
                new int[] {android.R.attr.itemBackground, android.R.attr.itemTextAppearance,
                    InternalRes.attr("preserveIconSpacing")},
                android.R.attr.listMenuViewStyle, defStyleRes);
        mBackground = a.getDrawable(0);
        mTextAppearance = a.getResourceId(1, -1);
        mPreserveIconSpacing = a.getBoolean(2, false);
        mTextAppearanceContext = context;
        a.recycle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setBackground(mBackground);
        mTitleView = (TextView) findViewById(android.R.id.title);
        if (mTextAppearance != -1 && mTitleView != null) {
            mTitleView.setTextAppearance(mTextAppearanceContext, mTextAppearance);
        }
        mShortcutView = (TextView) findViewById(InternalRes.viewId("shortcut"));
        mSubMenuArrowView = (ImageView) findViewById(InternalRes.viewId("submenuarrow"));
        if (mSubMenuArrowView != null) mSubMenuArrowView.setImageTintList(mTitleView != null ? mTitleView.getTextColors() : null);
        mGroupDivider = (ImageView) findViewById(InternalRes.viewId("group_divider"));
    }

    public void initialize(MenuItemImpl itemData, int menuType) {
        mItemData = itemData;
        mMenuType = menuType;
        setVisibility(itemData.isVisible() ? View.VISIBLE : View.GONE);
        setTitle(itemData.getTitleForItemView(this));
        setCheckable(itemData.isCheckable());
        setShortcut(itemData.shouldShowShortcut(), itemData.getShortcut());
        setIcon(itemData.getIcon());
        setEnabled(itemData.isEnabled());
        setSubMenuArrowVisible(itemData.hasSubMenu());
        setContentDescription(itemData.getContentDescription());
    }

    private void addContentView(View v) { addContentView(v, -1); }

    private void addContentView(View v, int index) {
        if (mGroupDivider != null || mSubMenuArrowView != null) {
            View content = findViewById(android.R.id.content);
            if (content instanceof ViewGroup) {
                ((ViewGroup) content).addView(v, index);
                return;
            }
        }
        addView(v, index);
    }

    public void setForceShowIcon(boolean forceShow) { mPreserveIconSpacing = mForceShowIcon = forceShow; }

    public void setTitle(CharSequence title) {
        if (mTitleView == null) return;
        if (title != null) {
            mTitleView.setText(title);
            if (mTitleView.getVisibility() != VISIBLE) mTitleView.setVisibility(VISIBLE);
        } else {
            if (mTitleView.getVisibility() != GONE) mTitleView.setVisibility(GONE);
        }
    }

    public MenuItemImpl getItemData() { return mItemData; }

    public void setCheckable(boolean checkable) {
        if (!checkable && mRadioButton == null && mCheckBox == null) return;
        final CompoundButton compoundButton;
        final CompoundButton otherCompoundButton;
        if (mItemData.isExclusiveCheckable()) {
            if (mRadioButton == null) insertRadioButton();
            compoundButton = mRadioButton;
            otherCompoundButton = mCheckBox;
        } else {
            if (mCheckBox == null) insertCheckBox();
            compoundButton = mCheckBox;
            otherCompoundButton = mRadioButton;
        }
        if (checkable) {
            compoundButton.setChecked(mItemData.isChecked());
            final int newVisibility = checkable ? VISIBLE : GONE;
            if (compoundButton.getVisibility() != newVisibility) compoundButton.setVisibility(newVisibility);
            if (otherCompoundButton != null && otherCompoundButton.getVisibility() != GONE) {
                otherCompoundButton.setVisibility(GONE);
            }
        } else {
            if (mCheckBox != null) mCheckBox.setVisibility(GONE);
            if (mRadioButton != null) mRadioButton.setVisibility(GONE);
        }
    }

    public void setChecked(boolean checked) {
        CompoundButton compoundButton;
        if (mItemData.isExclusiveCheckable()) {
            if (mRadioButton == null) insertRadioButton();
            compoundButton = mRadioButton;
        } else {
            if (mCheckBox == null) insertCheckBox();
            compoundButton = mCheckBox;
        }
        compoundButton.setChecked(checked);
    }

    private void setSubMenuArrowVisible(boolean hasSubmenu) {
        if (mSubMenuArrowView != null) mSubMenuArrowView.setVisibility(hasSubmenu ? View.VISIBLE : View.GONE);
    }

    public void setShortcut(boolean showShortcut, char shortcutKey) {
        if (mShortcutView == null) return;
        final int newVisibility = (showShortcut && mItemData.shouldShowShortcut()) ? VISIBLE : GONE;
        if (newVisibility == VISIBLE) mShortcutView.setText(mItemData.getShortcutLabel());
        if (mShortcutView.getVisibility() != newVisibility) mShortcutView.setVisibility(newVisibility);
    }

    public void setIcon(Drawable icon) {
        final boolean showIcon = mItemData.shouldShowIcon() || mForceShowIcon;
        if (!showIcon && !mPreserveIconSpacing) return;
        if (mIconView == null && icon == null && !mPreserveIconSpacing) return;
        if (mIconView == null) insertIconView();
        if (icon != null || mPreserveIconSpacing) {
            mIconView.setImageDrawable(showIcon ? icon : null);
            if (mIconView.getVisibility() != VISIBLE) mIconView.setVisibility(VISIBLE);
        } else {
            mIconView.setVisibility(GONE);
        }
    }

    private void insertIconView() {
        LayoutInflater inflater = getInflater();
        mIconView = (ImageView) inflater.inflate(InternalRes.layout("list_menu_item_icon"), this, false);
        addContentView(mIconView, 0);
    }

    private void insertRadioButton() {
        LayoutInflater inflater = getInflater();
        mRadioButton = (RadioButton) inflater.inflate(InternalRes.layout("list_menu_item_radio"), this, false);
        addContentView(mRadioButton);
    }

    private void insertCheckBox() {
        LayoutInflater inflater = getInflater();
        mCheckBox = (CheckBox) inflater.inflate(InternalRes.layout("list_menu_item_checkbox"), this, false);
        addContentView(mCheckBox);
    }

    public boolean prefersCondensedTitle() { return false; }

    public boolean showsIcon() { return mForceShowIcon; }

    private LayoutInflater getInflater() {
        if (mInflater == null) mInflater = LayoutInflater.from(getContext());
        return mInflater;
    }

    public void setGroupDividerEnabled(boolean groupDividerEnabled) {
        if (mGroupDivider != null) mGroupDivider.setVisibility(groupDividerEnabled ? View.VISIBLE : View.GONE);
    }
}
