package android.preference;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.HeaderViewListAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * Port of AOSP's PreferenceScreen (WS4): the root of a hierarchy, shown in a ListView; a nested screen opens as a
 * full-screen dialog listing its children.
 */
@Deprecated
public final class PreferenceScreen extends PreferenceGroup
        implements AdapterView.OnItemClickListener, DialogInterface.OnDismissListener {
    private ListAdapter mRootAdapter;
    private Dialog mDialog;
    private ListView mListView;

    /** framework-internal (hidden in AOSP; public for the inflater's reflection) */
    public PreferenceScreen(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.preferenceScreenStyle);
    }

    public ListAdapter getRootAdapter() {
        if (mRootAdapter == null) mRootAdapter = onCreateRootAdapter();
        return mRootAdapter;
    }

    protected ListAdapter onCreateRootAdapter() { return new PreferenceGroupAdapter(this); }

    public void bind(ListView listView) {
        listView.setOnItemClickListener(this);
        listView.setAdapter(getRootAdapter());
        onAttachedToActivity();
    }

    @Override
    protected void onClick() {
        if (getIntent() != null || getFragment() != null || getPreferenceCount() == 0) return;
        showDialog(null);
    }

    private void showDialog(Bundle state) {
        Context context = getContext();
        if (mListView != null) mListView.setAdapter(null);
        mListView = new ListView(context);
        mListView.setId(android.R.id.list);
        bind(mListView);
        final CharSequence title = getTitle();
        Dialog dialog = mDialog = new Dialog(context, context.getThemeResId());
        if (TextUtils.isEmpty(title)) {
            dialog.getWindow().requestFeature(android.view.Window.FEATURE_NO_TITLE);
        } else {
            dialog.setTitle(title);
        }
        dialog.setContentView(mListView);
        dialog.setOnDismissListener(this);
        if (state != null) dialog.onRestoreInstanceState(state);
        getPreferenceManager().addPreferencesScreen(dialog);
        dialog.show();
    }

    public void onDismiss(DialogInterface dialog) {
        mDialog = null;
        getPreferenceManager().removePreferencesScreen(dialog);
    }

    public Dialog getDialog() { return mDialog; }

    public void onItemClick(AdapterView parent, View view, int position, long id) {
        if (parent instanceof ListView) position -= ((ListView) parent).getHeaderViewsCount();
        Object item = getRootAdapter().getItem(position);
        if (!(item instanceof Preference)) return;
        ((Preference) item).performClick(this);
    }

    @Override
    protected boolean isOnSameScreenAsChildren() { return false; }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final Dialog dialog = mDialog;
        if (dialog == null || !dialog.isShowing()) return superState;
        final SavedState myState = new SavedState(superState);
        myState.isDialogShowing = true;
        myState.dialogBundle = dialog.onSaveInstanceState();
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
        if (myState.isDialogShowing) showDialog(myState.dialogBundle);
    }

    private static class SavedState extends BaseSavedState {
        boolean isDialogShowing;
        Bundle dialogBundle;

        SavedState(Parcel source) {
            super(source);
            isDialogShowing = source.readInt() == 1;
            dialogBundle = source.readBundle();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(isDialogShowing ? 1 : 0);
            dest.writeBundle(dialogBundle);
        }

        SavedState(Parcelable superState) { super(superState); }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) { return new SavedState(in); }
            public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }
}
