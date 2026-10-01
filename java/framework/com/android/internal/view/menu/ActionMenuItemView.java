package com.android.internal.view.menu;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ActionMenuView;
import android.widget.TextView;

/** framework-internal. Port of AOSP ActionMenuItemView: an action bar button showing an icon and/or a title. */
public class ActionMenuItemView extends TextView implements MenuView.ItemView, View.OnClickListener,
        ActionMenuView.ActionMenuChildView {
    private static final int MAX_ICON_SIZE = 32; // dp

    private MenuItemImpl mItemData;
    private CharSequence mTitle;
    private Drawable mIcon;
    private MenuBuilder.ItemInvoker mItemInvoker;
    private boolean mAllowTextWithIcon;
    private boolean mExpandedFormat;
    private int mMinWidth;
    private int mSavedPaddingLeft;
    private final int mMaxIconSize;

    public ActionMenuItemView(Context context) { this(context, null); }

    public ActionMenuItemView(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public ActionMenuItemView(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public ActionMenuItemView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mAllowTextWithIcon = shouldAllowTextWithIcon();
        final TypedArray a = context.obtainStyledAttributes(attrs, new int[] {android.R.attr.minWidth}, defStyleAttr,
                defStyleRes);
        mMinWidth = a.getDimensionPixelSize(0, 0);
        a.recycle();
        final float density = context.getResources().getDisplayMetrics().density;
        mMaxIconSize = (int) (MAX_ICON_SIZE * density + 0.5f);
        setOnClickListener(this);
        mSavedPaddingLeft = -1;
        setSaveEnabled(false);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mAllowTextWithIcon = shouldAllowTextWithIcon();
        updateTextButtonVisibility();
    }

    private boolean shouldAllowTextWithIcon() {
        final Configuration config = getContext().getResources().getConfiguration();
        final int widthDp = config.screenWidthDp;
        final int heightDp = config.screenHeightDp;
        return widthDp >= 480 || (widthDp >= 640 && heightDp >= 480)
                || config.orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @Override
    public void setPadding(int l, int t, int r, int b) {
        mSavedPaddingLeft = l;
        super.setPadding(l, t, r, b);
    }

    public MenuItemImpl getItemData() { return mItemData; }

    public void initialize(MenuItemImpl itemData, int menuType) {
        mItemData = itemData;
        setIcon(itemData.getIcon());
        setTitle(itemData.getTitleForItemView(this));
        setId(itemData.getItemId());
        setVisibility(itemData.isVisible() ? View.VISIBLE : View.GONE);
        setEnabled(itemData.isEnabled());
    }

    public void onClick(View v) {
        if (mItemInvoker != null) mItemInvoker.invokeItem(mItemData);
    }

    public void setItemInvoker(MenuBuilder.ItemInvoker invoker) { mItemInvoker = invoker; }

    public boolean prefersCondensedTitle() { return true; }

    public void setCheckable(boolean checkable) {}

    public void setChecked(boolean checked) {}

    public void setExpandedFormat(boolean expandedFormat) {
        if (mExpandedFormat != expandedFormat) {
            mExpandedFormat = expandedFormat;
            updateTextButtonVisibility();
        }
    }

    private void updateTextButtonVisibility() {
        boolean visible = !TextUtils.isEmpty(mTitle);
        visible &= mIcon == null || (mItemData != null && mItemData.showsTextAsAction()
                && (mAllowTextWithIcon || mExpandedFormat));
        setText(visible ? mTitle : null);
        final CharSequence contentDescription = mItemData != null ? mItemData.getContentDescription() : null;
        if (TextUtils.isEmpty(contentDescription)) {
            setContentDescription(visible ? null : (mItemData != null ? mItemData.getTitle() : null));
        } else {
            setContentDescription(contentDescription);
        }
    }

    public void setIcon(Drawable icon) {
        mIcon = icon;
        if (icon != null) {
            int width = icon.getIntrinsicWidth();
            int height = icon.getIntrinsicHeight();
            if (width > mMaxIconSize) {
                final float scale = (float) mMaxIconSize / width;
                width = mMaxIconSize;
                height = (int) (height * scale);
            }
            if (height > mMaxIconSize) {
                final float scale = (float) mMaxIconSize / height;
                height = mMaxIconSize;
                width = (int) (width * scale);
            }
            icon.setBounds(0, 0, width, height);
        }
        setCompoundDrawables(icon, null, null, null);
        updateTextButtonVisibility();
    }

    public boolean hasText() { return !TextUtils.isEmpty(getText()); }

    public void setShortcut(boolean showShortcut, char shortcutKey) {}

    public void setTitle(CharSequence title) {
        mTitle = title;
        updateTextButtonVisibility();
    }

    public boolean showsIcon() { return true; }

    public boolean needsDividerBefore() { return hasText() && mItemData.getIcon() == null; }

    public boolean needsDividerAfter() { return hasText(); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final boolean textVisible = hasText();
        if (textVisible && mSavedPaddingLeft >= 0) {
            super.setPadding(mSavedPaddingLeft, getPaddingTop(), getPaddingRight(), getPaddingBottom());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int oldMeasuredWidth = getMeasuredWidth();
        final int targetWidth = widthMode == MeasureSpec.AT_MOST ? Math.min(widthSize, mMinWidth) : mMinWidth;
        if (widthMode != MeasureSpec.EXACTLY && mMinWidth > 0 && oldMeasuredWidth < targetWidth) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY), heightMeasureSpec);
        }
        if (!textVisible && mIcon != null) {
            // Center the lone icon: TextView only centres compound drawables vertically.
            final int w = getMeasuredWidth();
            final int dw = mIcon.getBounds().width();
            super.setPadding((w - dw) / 2, getPaddingTop(), getPaddingRight(), getPaddingBottom());
        }
    }
}
