package android.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/** Port of AOSP's TwoStatePreference: a persisted boolean with on and off summaries. */
@Deprecated
public abstract class TwoStatePreference extends Preference {
    private static final int[] ATTRS = {
        android.R.attr.summaryOn, android.R.attr.summaryOff, android.R.attr.disableDependentsState,
    };

    private CharSequence mSummaryOn;
    private CharSequence mSummaryOff;
    boolean mChecked;
    private boolean mCheckedSet;
    private boolean mDisableDependentsState;

    public TwoStatePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        TypedArray a = context.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, defStyleRes);
        mSummaryOn = a.getString(0);
        mSummaryOff = a.getString(1);
        mDisableDependentsState = a.getBoolean(2, false);
        a.recycle();
    }

    public TwoStatePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TwoStatePreference(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public TwoStatePreference(Context context) { this(context, null); }

    @Override
    protected void onClick() {
        super.onClick();
        final boolean newValue = !isChecked();
        if (callChangeListener(newValue)) setChecked(newValue);
    }

    public void setChecked(boolean checked) {
        final boolean changed = mChecked != checked;
        if (changed || !mCheckedSet) {
            mChecked = checked;
            mCheckedSet = true;
            persistBoolean(checked);
            if (changed) {
                notifyDependencyChange(shouldDisableDependents());
                notifyChanged();
            }
        }
    }

    public boolean isChecked() { return mChecked; }

    @Override
    public boolean shouldDisableDependents() {
        boolean shouldDisable = mDisableDependentsState ? mChecked : !mChecked;
        return shouldDisable || super.shouldDisableDependents();
    }

    public void setSummaryOn(CharSequence summary) {
        mSummaryOn = summary;
        if (isChecked()) notifyChanged();
    }

    public void setSummaryOn(int summaryResId) { setSummaryOn(getContext().getString(summaryResId)); }
    public CharSequence getSummaryOn() { return mSummaryOn; }

    public void setSummaryOff(CharSequence summary) {
        mSummaryOff = summary;
        if (!isChecked()) notifyChanged();
    }

    public void setSummaryOff(int summaryResId) { setSummaryOff(getContext().getString(summaryResId)); }
    public CharSequence getSummaryOff() { return mSummaryOff; }
    public boolean getDisableDependentsState() { return mDisableDependentsState; }
    public void setDisableDependentsState(boolean disableDependentsState) {
        mDisableDependentsState = disableDependentsState;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) { return a.getBoolean(index, false); }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setChecked(restoreValue ? getPersistedBoolean(mChecked) : (Boolean) defaultValue);
    }

    /** framework-internal: the summary for the current state, as AOSP's hidden syncSummaryView. */
    void syncSummaryView(View view) {
        TextView summaryView = (TextView) view.findViewById(android.R.id.summary);
        if (summaryView == null) return;
        boolean useDefaultSummary = true;
        if (mChecked && !TextUtils.isEmpty(mSummaryOn)) {
            summaryView.setText(mSummaryOn);
            useDefaultSummary = false;
        } else if (!mChecked && !TextUtils.isEmpty(mSummaryOff)) {
            summaryView.setText(mSummaryOff);
            useDefaultSummary = false;
        }
        if (useDefaultSummary) {
            final CharSequence summary = getSummary();
            if (!TextUtils.isEmpty(summary)) {
                summaryView.setText(summary);
                useDefaultSummary = false;
            }
        }
        int newVisibility = useDefaultSummary ? View.GONE : View.VISIBLE;
        if (newVisibility != summaryView.getVisibility()) summaryView.setVisibility(newVisibility);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) return superState;
        final SavedState myState = new SavedState(superState);
        myState.checked = isChecked();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setChecked(myState.checked);
    }

    static class SavedState extends BaseSavedState {
        boolean checked;

        SavedState(Parcel source) {
            super(source);
            checked = source.readInt() == 1;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(checked ? 1 : 0);
        }

        SavedState(Parcelable superState) { super(superState); }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) { return new SavedState(in); }
            public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }
}
