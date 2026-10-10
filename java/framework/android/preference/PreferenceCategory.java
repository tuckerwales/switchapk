package android.preference;

import android.content.Context;
import android.util.AttributeSet;

/** Port of AOSP's PreferenceCategory (WS4): a titled, unselectable group header. */
@Deprecated
public class PreferenceCategory extends PreferenceGroup {
    public PreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public PreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public PreferenceCategory(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.preferenceCategoryStyle);
    }

    public PreferenceCategory(Context context) { this(context, null); }

    @Override
    protected boolean onPrepareAddPreference(Preference preference) {
        if (preference instanceof PreferenceCategory) {
            throw new IllegalArgumentException("Cannot add a " + PreferenceCategory.class.getSimpleName()
                    + " directly to a " + PreferenceCategory.class.getSimpleName());
        }
        return super.onPrepareAddPreference(preference);
    }

    @Override
    public boolean isEnabled() { return false; }

    @Override
    public boolean shouldDisableDependents() { return !super.isEnabled(); }
}
