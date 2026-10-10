package android.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;

/** Port of AOSP's EditTextPreference: a persisted string edited in an EditText dialog. */
@Deprecated
public class EditTextPreference extends DialogPreference {
    private EditText mEditText;
    private String mText;
    private boolean mTextSet;

    public EditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mEditText = new EditText(context, attrs);
        mEditText.setId(android.R.id.edit);
        mEditText.setEnabled(true);
    }

    public EditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public EditTextPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.editTextPreferenceStyle);
    }

    public EditTextPreference(Context context) { this(context, null); }

    public void setText(String text) {
        final boolean changed = !TextUtils.equals(mText, text);
        if (changed || !mTextSet) {
            mText = text;
            mTextSet = true;
            final boolean wasBlocking = shouldDisableDependents();
            persistString(text);
            final boolean isBlocking = shouldDisableDependents();
            if (isBlocking != wasBlocking) notifyDependencyChange(isBlocking);
            if (changed) notifyChanged();
        }
    }

    public String getText() { return mText; }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        EditText editText = mEditText;
        editText.setText(getText());
        ViewParent oldParent = editText.getParent();
        if (oldParent != view) {
            if (oldParent != null) ((ViewGroup) oldParent).removeView(editText);
            onAddEditTextToDialogView(view, editText);
        }
    }

    @Override
    protected void showDialog(android.os.Bundle state) {
        super.showDialog(state);
        mEditText.requestFocus();
    }

    protected void onAddEditTextToDialogView(View dialogView, EditText editText) {
        // com.android.internal.R.id.edittext_container in preference_dialog_edittext
        int id = getContext().getResources().getIdentifier("edittext_container", "id", "android");
        ViewGroup container = id != 0 ? (ViewGroup) dialogView.findViewById(id) : null;
        if (container == null && dialogView instanceof ViewGroup) container = (ViewGroup) dialogView;
        if (container != null) {
            container.addView(editText, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            String value = mEditText.getText().toString();
            if (callChangeListener(value)) setText(value);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) { return a.getString(index); }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setText(restoreValue ? getPersistedString(mText) : (String) defaultValue);
    }

    @Override
    public boolean shouldDisableDependents() { return TextUtils.isEmpty(mText) || super.shouldDisableDependents(); }

    public EditText getEditText() { return mEditText; }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) return superState;
        final SavedState myState = new SavedState(superState);
        myState.text = getText();
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
        setText(myState.text);
    }

    private static class SavedState extends BaseSavedState {
        String text;

        SavedState(Parcel source) {
            super(source);
            text = source.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(text);
        }

        SavedState(Parcelable superState) { super(superState); }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) { return new SavedState(in); }
            public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }
}
