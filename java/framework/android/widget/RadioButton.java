package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityNodeInfo;

/** A radio button that stays checked once selected (AOSP RadioButton). */
public class RadioButton extends CompoundButton {
    public RadioButton(Context context) { this(context, null); }

    public RadioButton(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.radioButtonStyle);
    }

    public RadioButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RadioButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /** A checked radio button does not clear itself. The group clears the previous one. */
    @Override
    public void toggle() {
        if (!isChecked()) super.toggle();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(true);
        info.setChecked(isChecked());
    }

    @Override
    public CharSequence getAccessibilityClassName() { return RadioButton.class.getName(); }

    @Override
    boolean drawFallbackButton() { return true; }
}
