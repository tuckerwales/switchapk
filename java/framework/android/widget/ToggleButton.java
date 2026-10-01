package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

/** A button whose label switches between an on and an off string (AOSP ToggleButton). */
public class ToggleButton extends CompoundButton {
    private static final int[] ATTRS = {android.R.attr.textOn, android.R.attr.textOff, android.R.attr.disabledAlpha};

    private CharSequence mTextOn;
    private CharSequence mTextOff;
    private float mDisabledAlpha = 0.5f;

    public ToggleButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        TypedArray a = context.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, defStyleRes);
        mTextOn = a.getText(0);
        mTextOff = a.getText(1);
        mDisabledAlpha = a.getFloat(2, 0.5f);
        a.recycle();
        if (mTextOn == null) mTextOn = "ON";
        if (mTextOff == null) mTextOff = "OFF";
        syncText();
    }

    public ToggleButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ToggleButton(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.buttonStyleToggle);
    }

    public ToggleButton(Context context) { this(context, null); }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        syncText();
    }

    public CharSequence getTextOn() { return mTextOn; }

    public void setTextOn(CharSequence textOn) {
        mTextOn = textOn;
        syncText();
    }

    public CharSequence getTextOff() { return mTextOff; }

    public void setTextOff(CharSequence textOff) {
        mTextOff = textOff;
        syncText();
    }

    public float getDisabledAlpha() { return mDisabledAlpha; }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        syncText();
    }

    @Override
    public void setBackgroundDrawable(Drawable d) {
        super.setBackgroundDrawable(d);
        applyDisabledAlpha();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        applyDisabledAlpha();
    }

    @Override
    public CharSequence getAccessibilityClassName() { return ToggleButton.class.getName(); }

    private void syncText() {
        if (mTextOn == null && mTextOff == null) return;
        setText(isChecked() ? mTextOn : mTextOff);
    }

    private void applyDisabledAlpha() {
        Drawable background = getBackground();
        if (background != null) background.setAlpha(isEnabled() ? 255 : (int) (mDisabledAlpha * 255));
    }
}
