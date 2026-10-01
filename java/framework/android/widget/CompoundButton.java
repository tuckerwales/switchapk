package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;

/**
 * A button with a check state and an indicator drawable (AOSP CompoundButton).
 * The indicator is drawn by this class, not as a compound drawable, so an app
 * drawable on the text is left alone. With no button drawable, a flat box is
 * drawn so the control is still visible.
 */
public abstract class CompoundButton extends Button implements Checkable {
    private static final int[] ATTRS = {android.R.attr.button, android.R.attr.checked};
    private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};

    private boolean mChecked;
    private boolean mBroadcasting;
    private Drawable mButtonDrawable;
    private ColorStateList mButtonTintList;
    private PorterDuff.Mode mButtonTintMode;
    private BlendMode mButtonBlendMode;
    private boolean mHasButtonTint;
    private boolean mHasButtonTintMode;
    private OnCheckedChangeListener mOnCheckedChangeListener;
    private OnCheckedChangeListener mOnCheckedChangeWidgetListener;
    private Paint mFallbackPaint;

    public interface OnCheckedChangeListener {
        void onCheckedChanged(CompoundButton buttonView, boolean isChecked);
    }

    public CompoundButton(Context context) { this(context, null); }

    public CompoundButton(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public CompoundButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CompoundButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (attrs == null && defStyleAttr == 0 && defStyleRes == 0) return;
        TypedArray a = context.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, defStyleRes);
        Drawable d = a.getDrawable(0);
        if (d != null) setButtonDrawable(d);
        boolean checked = a.getBoolean(1, false);
        a.recycle();
        setChecked(checked);
    }

    @Override
    public void toggle() { setChecked(!mChecked); }

    @Override
    public boolean performClick() {
        toggle();
        return super.performClick();
    }

    @Override
    public boolean isChecked() { return mChecked; }

    @Override
    public void setChecked(boolean checked) {
        if (mChecked == checked) return;
        mChecked = checked;
        refreshDrawableState();
        if (mBroadcasting) return;
        mBroadcasting = true;
        if (mOnCheckedChangeListener != null) mOnCheckedChangeListener.onCheckedChanged(this, mChecked);
        if (mOnCheckedChangeWidgetListener != null) mOnCheckedChangeWidgetListener.onCheckedChanged(this, mChecked);
        mBroadcasting = false;
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) { mOnCheckedChangeListener = listener; }

    /** framework-internal: lets RadioGroup listen without replacing the app listener. */
    void setOnCheckedChangeWidgetListener(OnCheckedChangeListener listener) {
        mOnCheckedChangeWidgetListener = listener;
    }

    public void setButtonDrawable(int resId) {
        setButtonDrawable(resId == 0 ? null : getContext().getDrawable(resId));
    }

    public void setButtonDrawable(Drawable drawable) {
        if (mButtonDrawable == drawable) return;
        if (mButtonDrawable != null) {
            mButtonDrawable.setCallback(null);
            unscheduleDrawable(mButtonDrawable);
        }
        mButtonDrawable = drawable;
        if (drawable != null) {
            drawable.setCallback(this);
            drawable.setLayoutDirection(getLayoutDirection());
            if (drawable.isStateful()) drawable.setState(getDrawableState());
            drawable.setVisible(getVisibility() == VISIBLE, false);
            applyButtonTint();
        }
        requestLayout();
        invalidate();
    }

    public Drawable getButtonDrawable() { return mButtonDrawable; }

    public void setButtonIcon(Icon icon) { setButtonDrawable(icon == null ? null : icon.loadDrawable(getContext())); }

    public void setButtonTintList(ColorStateList tint) {
        mButtonTintList = tint;
        mHasButtonTint = true;
        applyButtonTint();
    }

    public ColorStateList getButtonTintList() { return mButtonTintList; }

    public void setButtonTintMode(PorterDuff.Mode tintMode) {
        mButtonTintMode = tintMode;
        mButtonBlendMode = null;
        mHasButtonTintMode = true;
        applyButtonTint();
    }

    public void setButtonTintBlendMode(BlendMode blendMode) {
        mButtonBlendMode = blendMode;
        mButtonTintMode = ImageView.modeOf(blendMode);
        mHasButtonTintMode = true;
        applyButtonTint();
    }

    public PorterDuff.Mode getButtonTintMode() { return mButtonTintMode; }

    public BlendMode getButtonTintBlendMode() { return mButtonBlendMode; }

    @Override
    public CharSequence getAccessibilityClassName() { return CompoundButton.class.getName(); }

    @Override
    public int getCompoundPaddingLeft() {
        int padding = super.getCompoundPaddingLeft();
        if (!isLayoutRtl()) padding += buttonWidth();
        return padding;
    }

    @Override
    public int getCompoundPaddingRight() {
        int padding = super.getCompoundPaddingRight();
        if (isLayoutRtl()) padding += buttonWidth();
        return padding;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int dw = buttonWidth();
        int dh = buttonHeight();
        if (dw > 0 && dh > 0 && (mButtonDrawable != null || drawFallbackButton())) {
            int vertical = getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
            int top;
            if (vertical == Gravity.BOTTOM) top = getHeight() - getPaddingBottom() - dh;
            else if (vertical == Gravity.CENTER_VERTICAL) {
                top = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - dh) / 2;
            } else top = getPaddingTop();
            int left = isLayoutRtl() ? getWidth() - getPaddingRight() - dw : getPaddingLeft();
            left += getScrollX();
            top += getScrollY();
            if (mButtonDrawable != null) {
                mButtonDrawable.setBounds(left, top, left + dw, top + dh);
                mButtonDrawable.draw(canvas);
            } else {
                if (mFallbackPaint == null) mFallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                mFallbackPaint.setColor(mChecked ? 0xFF4CAF50 : 0xFFBDBDBD);
                canvas.drawRect(left, top, left + dw, top + dh, mFallbackPaint);
            }
        }
        super.onDraw(canvas);
    }

    /** CheckBox and RadioButton draw a flat box when the theme supplies no button drawable. */
    boolean drawFallbackButton() { return false; }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        int[] state = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked()) mergeDrawableStates(state, CHECKED_STATE_SET);
        return state;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mButtonDrawable != null) {
            int[] state = getDrawableState();
            if (mButtonDrawable.setState(state)) invalidateDrawable(mButtonDrawable);
        }
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);
        if (mButtonDrawable != null) mButtonDrawable.setHotspot(x, y);
    }

    @Override
    protected boolean verifyDrawable(Drawable who) { return super.verifyDrawable(who) || who == mButtonDrawable; }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mButtonDrawable != null) mButtonDrawable.jumpToCurrentState();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        state.checked = isChecked();
        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setChecked(ss.checked);
    }

    private void applyButtonTint() {
        if (mButtonDrawable != null && (mHasButtonTint || mHasButtonTintMode)) {
            mButtonDrawable = mButtonDrawable.mutate();
            if (mHasButtonTint) mButtonDrawable.setTintList(mButtonTintList);
            if (mHasButtonTintMode) {
                mButtonDrawable.setTintMode(mButtonTintMode);
                if (mButtonBlendMode != null) mButtonDrawable.setTintBlendMode(mButtonBlendMode);
            }
            if (mButtonDrawable.isStateful()) mButtonDrawable.setState(getDrawableState());
        }
    }

    private int buttonWidth() {
        if (mButtonDrawable == null) return drawFallbackButton() ? fallbackSize() : 0;
        int w = mButtonDrawable.getIntrinsicWidth();
        return w > 0 ? w : fallbackSize();
    }

    private int buttonHeight() {
        if (mButtonDrawable == null) return drawFallbackButton() ? fallbackSize() : 0;
        int h = mButtonDrawable.getIntrinsicHeight();
        return h > 0 ? h : fallbackSize();
    }

    private int fallbackSize() { return (int) (18f * getResources().getDisplayMetrics().density + 0.5f); }

    static class SavedState extends View.BaseSavedState {
        boolean checked;

        SavedState(Parcelable superState) { super(superState); }

        private SavedState(Parcel in) {
            super(in);
            checked = in.readBoolean();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeBoolean(checked);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) { return new SavedState(in); }

            public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }
}
