package android.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;

/** Port of AOSP's CheckBoxPreference: the theme's checkBoxPreferenceStyle gives the checkbox widget layout. */
@Deprecated
public class CheckBoxPreference extends TwoStatePreference {
    public CheckBoxPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public CheckBoxPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CheckBoxPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.checkBoxPreferenceStyle);
    }

    public CheckBoxPreference(Context context) { this(context, null); }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        View checkboxView = view.findViewById(android.R.id.checkbox);
        if (checkboxView instanceof Checkable) ((Checkable) checkboxView).setChecked(mChecked);
        syncSummaryView(view);
    }
}
