package android.widget;

import android.content.Context;
import android.util.AttributeSet;

/** A two-state check box (AOSP CheckBox). */
public class CheckBox extends CompoundButton {
    public CheckBox(Context context) { this(context, null); }

    public CheckBox(Context context, AttributeSet attrs) { this(context, attrs, android.R.attr.checkboxStyle); }

    public CheckBox(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public CheckBox(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public CharSequence getAccessibilityClassName() { return CheckBox.class.getName(); }
}
