package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;

/** A TextView with a check mark drawn at its end (port of AOSP CheckedTextView). */
public class CheckedTextView extends TextView implements Checkable {
    private static int[] sAttrs;
    private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};

    private boolean mChecked;
    private int mCheckMarkResource;
    private Drawable mCheckMarkDrawable;
    private ColorStateList mCheckMarkTintList = null;
    private BlendMode mCheckMarkBlendMode = null;
    private boolean mHasCheckMarkTint = false;
    private boolean mHasCheckMarkTintMode = false;
    private int mBasePadding;
    private int mCheckMarkWidth;
    private int mCheckMarkGravity = Gravity.END;
    private boolean mNeedRequestlayout;

    public CheckedTextView(Context context) { this(context, null); }

    public CheckedTextView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.checkedTextViewStyle);
    }

    public CheckedTextView(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public CheckedTextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (sAttrs == null) {
            sAttrs = new int[] {android.R.attr.checked, android.R.attr.checkMark, android.R.attr.checkMarkTint,
                android.R.attr.checkMarkTintMode, com.android.internal.util.InternalRes.attr("checkMarkGravity")};
        }
        final TypedArray a = context.obtainStyledAttributes(attrs, sAttrs, defStyleAttr, defStyleRes);
        final Drawable d = a.getDrawable(1);
        if (d != null) setCheckMarkDrawable(d);
        if (a.hasValue(3)) {
            mCheckMarkBlendMode = blendFromTintMode(a.getInt(3, -1));
            mHasCheckMarkTintMode = mCheckMarkBlendMode != null;
        }
        if (a.hasValue(2)) {
            mCheckMarkTintList = a.getColorStateList(2);
            mHasCheckMarkTint = true;
        }
        mCheckMarkGravity = a.getInt(4, Gravity.END);
        final boolean checked = a.getBoolean(0, false);
        setChecked(checked);
        a.recycle();
        applyCheckMarkTint();
    }

    private static BlendMode blendFromTintMode(int value) {
        switch (value) {
            case 3: return BlendMode.SRC_OVER;
            case 5: return BlendMode.SRC_IN;
            case 9: return BlendMode.SRC_ATOP;
            case 14: return BlendMode.MODULATE;
            case 15: return BlendMode.SCREEN;
            case 16: return BlendMode.PLUS;
            default: return null;
        }
    }

    public void toggle() { setChecked(!mChecked); }

    public boolean isChecked() { return mChecked; }

    public void setChecked(boolean checked) {
        if (mChecked != checked) {
            mChecked = checked;
            refreshDrawableState();
        }
    }

    public void setCheckMarkDrawable(int resId) {
        if (resId != 0 && resId == mCheckMarkResource) return;
        final Drawable d = resId != 0 ? getContext().getDrawable(resId) : null;
        setCheckMarkDrawableInternal(d, resId);
    }

    public void setCheckMarkDrawable(Drawable d) { setCheckMarkDrawableInternal(d, 0); }

    private void setCheckMarkDrawableInternal(Drawable d, int resId) {
        if (mCheckMarkDrawable != null) {
            mCheckMarkDrawable.setCallback(null);
            unscheduleDrawable(mCheckMarkDrawable);
        }
        mNeedRequestlayout = (d != mCheckMarkDrawable);
        if (d != null) {
            d.setCallback(this);
            d.setVisible(getVisibility() == VISIBLE, false);
            d.setState(CHECKED_STATE_SET);
            setMinHeight(d.getIntrinsicHeight());
            mCheckMarkWidth = d.getIntrinsicWidth();
            d.setState(getDrawableState());
        } else {
            mCheckMarkWidth = 0;
        }
        mCheckMarkDrawable = d;
        mCheckMarkResource = resId;
        applyCheckMarkTint();
        updatePadding();
    }

    public void setCheckMarkTintList(ColorStateList tint) {
        mCheckMarkTintList = tint;
        mHasCheckMarkTint = true;
        applyCheckMarkTint();
    }

    public ColorStateList getCheckMarkTintList() { return mCheckMarkTintList; }

    public void setCheckMarkTintMode(PorterDuff.Mode tintMode) {
        setCheckMarkTintBlendMode(tintMode != null ? BlendMode.valueOf(tintMode.name()) : null);
    }

    public void setCheckMarkTintBlendMode(BlendMode blendMode) {
        mCheckMarkBlendMode = blendMode;
        mHasCheckMarkTintMode = true;
        applyCheckMarkTint();
    }

    public PorterDuff.Mode getCheckMarkTintMode() {
        if (mCheckMarkBlendMode == null) return null;
        try {
            return PorterDuff.Mode.valueOf(mCheckMarkBlendMode.name());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public BlendMode getCheckMarkTintBlendMode() { return mCheckMarkBlendMode; }

    private void applyCheckMarkTint() {
        if (mCheckMarkDrawable != null && (mHasCheckMarkTint || mHasCheckMarkTintMode)) {
            mCheckMarkDrawable = mCheckMarkDrawable.mutate();
            if (mHasCheckMarkTint) mCheckMarkDrawable.setTintList(mCheckMarkTintList);
            if (mHasCheckMarkTintMode) mCheckMarkDrawable.setTintBlendMode(mCheckMarkBlendMode);
            if (mCheckMarkDrawable.isStateful()) mCheckMarkDrawable.setState(getDrawableState());
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (mCheckMarkDrawable != null) mCheckMarkDrawable.setVisible(visibility == VISIBLE, false);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mCheckMarkDrawable != null) mCheckMarkDrawable.jumpToCurrentState();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) { return who == mCheckMarkDrawable || super.verifyDrawable(who); }

    public Drawable getCheckMarkDrawable() { return mCheckMarkDrawable; }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updatePadding();
    }

    private boolean isCheckMarkAtStart() {
        final int gravity = Gravity.getAbsoluteGravity(mCheckMarkGravity, getLayoutDirection());
        return (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT;
    }

    private void updatePadding() {
        final boolean newLayoutRequired;
        final int newPadding = (mCheckMarkDrawable != null) ? mCheckMarkWidth + mBasePadding : mBasePadding;
        if (isCheckMarkAtStart()) {
            newLayoutRequired = mNeedRequestlayout || getPaddingLeft() != newPadding;
            if (newLayoutRequired) super.setPadding(newPadding, getPaddingTop(), getPaddingRight(), getPaddingBottom());
        } else {
            newLayoutRequired = mNeedRequestlayout || getPaddingRight() != newPadding;
            if (newLayoutRequired) super.setPadding(getPaddingLeft(), getPaddingTop(), newPadding, getPaddingBottom());
        }
        if (newLayoutRequired) {
            requestLayout();
            mNeedRequestlayout = false;
        }
    }

    private void setBasePadding(boolean checkmarkAtStart) {
        if (checkmarkAtStart) mBasePadding = getPaddingLeft();
        else mBasePadding = getPaddingRight();
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        setBasePadding(isCheckMarkAtStart());
        updatePadding();
    }

    @Override
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        super.setPaddingRelative(start, top, end, bottom);
        setBasePadding(isCheckMarkAtStart());
        updatePadding();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mBasePadding == 0 && mCheckMarkDrawable != null) {
            setBasePadding(isCheckMarkAtStart());
            mBasePadding -= mCheckMarkWidth;
            if (mBasePadding < 0) mBasePadding = 0;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final Drawable checkMarkDrawable = mCheckMarkDrawable;
        if (checkMarkDrawable != null) {
            final int verticalGravity = getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
            final int height = checkMarkDrawable.getIntrinsicHeight();
            int y = 0;
            switch (verticalGravity) {
                case Gravity.BOTTOM: y = getHeight() - height; break;
                case Gravity.CENTER_VERTICAL: y = (getHeight() - height) / 2; break;
                default: break;
            }
            final boolean checkMarkAtStart = isCheckMarkAtStart();
            final int width = getWidth();
            final int top = y;
            final int bottom = top + height;
            final int left;
            final int right;
            if (checkMarkAtStart) {
                left = mBasePadding;
                right = left + mCheckMarkWidth;
            } else {
                right = width - mBasePadding;
                left = right - mCheckMarkWidth;
            }
            checkMarkDrawable.setBounds(getScrollX() + left, top, getScrollX() + right, bottom);
            checkMarkDrawable.draw(canvas);
            final Drawable background = getBackground();
            if (background != null) {
                background.setHotspotBounds(getScrollX() + left, top, getScrollX() + right, bottom);
            }
        }
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked()) mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        return drawableState;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        final Drawable checkMarkDrawable = mCheckMarkDrawable;
        if (checkMarkDrawable != null && checkMarkDrawable.isStateful()
                && checkMarkDrawable.setState(getDrawableState())) {
            invalidateDrawable(checkMarkDrawable);
        }
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);
        if (mCheckMarkDrawable != null) mCheckMarkDrawable.setHotspot(x, y);
    }

    @Override
    public CharSequence getAccessibilityClassName() { return CheckedTextView.class.getName(); }

    static class SavedState extends BaseSavedState {
        boolean checked;

        SavedState(Parcelable superState) { super(superState); }

        private SavedState(Parcel in) {
            super(in);
            checked = (Boolean) in.readValue(null);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeValue(checked);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) { return new SavedState(in); }

            public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.checked = isChecked();
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setChecked(ss.checked);
        requestLayout();
    }
}
