package android.preference;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

/**
 * Port of AOSP's RingtonePreference: picks a sound with the system ringtone picker and persists its URI. There is no
 * picker on the console, so a click logs and leaves the value as it is.
 */
@Deprecated
public class RingtonePreference extends Preference implements PreferenceManager.OnActivityResultListener {
    private static final String TAG = "RingtonePreference";
    // RingtoneManager's action and extras (that class is not part of this framework yet)
    private static final String ACTION_RINGTONE_PICKER = "android.intent.action.RINGTONE_PICKER";
    private static final String EXTRA_RINGTONE_EXISTING_URI = "android.intent.extra.ringtone.EXISTING_URI";
    private static final String EXTRA_RINGTONE_SHOW_DEFAULT = "android.intent.extra.ringtone.SHOW_DEFAULT";
    private static final String EXTRA_RINGTONE_DEFAULT_URI = "android.intent.extra.ringtone.DEFAULT_URI";
    private static final String EXTRA_RINGTONE_SHOW_SILENT = "android.intent.extra.ringtone.SHOW_SILENT";
    private static final String EXTRA_RINGTONE_TYPE = "android.intent.extra.ringtone.TYPE";
    private static final String EXTRA_RINGTONE_TITLE = "android.intent.extra.ringtone.TITLE";
    private static final String EXTRA_RINGTONE_PICKED_URI = "android.intent.extra.ringtone.PICKED_URI";

    private int mRingtoneType;
    private boolean mShowDefault;
    private boolean mShowSilent;
    private int mRequestCode;

    public RingtonePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
            android.R.attr.ringtoneType, android.R.attr.showDefault, android.R.attr.showSilent,
        }, defStyleAttr, defStyleRes);
        mRingtoneType = a.getInt(0, 1 /* RingtoneManager.TYPE_RINGTONE */);
        mShowDefault = a.getBoolean(1, true);
        mShowSilent = a.getBoolean(2, true);
        a.recycle();
    }

    public RingtonePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RingtonePreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.ringtonePreferenceStyle);
    }

    public RingtonePreference(Context context) { this(context, null); }

    public int getRingtoneType() { return mRingtoneType; }
    public void setRingtoneType(int type) { mRingtoneType = type; }
    public boolean getShowDefault() { return mShowDefault; }
    public void setShowDefault(boolean showDefault) { mShowDefault = showDefault; }
    public boolean getShowSilent() { return mShowSilent; }
    public void setShowSilent(boolean showSilent) { mShowSilent = showSilent; }

    @Override
    protected void onClick() {
        Intent intent = new Intent(ACTION_RINGTONE_PICKER);
        onPrepareRingtonePickerIntent(intent);
        PreferenceFragment owningFragment = getPreferenceManager().getFragment() instanceof PreferenceFragment
                ? (PreferenceFragment) getPreferenceManager().getFragment() : null;
        try {
            if (owningFragment != null) {
                owningFragment.startActivityForResult(intent, mRequestCode);
            } else if (getPreferenceManager().getActivity() != null) {
                getPreferenceManager().getActivity().startActivityForResult(intent, mRequestCode);
            }
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "no ringtone picker on this device");
        }
    }

    protected void onPrepareRingtonePickerIntent(Intent ringtonePickerIntent) {
        ringtonePickerIntent.putExtra(EXTRA_RINGTONE_EXISTING_URI, onRestoreRingtone());
        ringtonePickerIntent.putExtra(EXTRA_RINGTONE_SHOW_DEFAULT, mShowDefault);
        ringtonePickerIntent.putExtra(EXTRA_RINGTONE_SHOW_SILENT, mShowSilent);
        ringtonePickerIntent.putExtra(EXTRA_RINGTONE_TYPE, mRingtoneType);
        ringtonePickerIntent.putExtra(EXTRA_RINGTONE_TITLE, getTitle());
    }

    protected void onSaveRingtone(Uri ringtoneUri) { persistString(ringtoneUri != null ? ringtoneUri.toString() : ""); }

    protected Uri onRestoreRingtone() {
        final String uriString = getPersistedString(null);
        return !TextUtils.isEmpty(uriString) ? Uri.parse(uriString) : null;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) { return a.getString(index); }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValueObj) {
        String defaultValue = (String) defaultValueObj;
        if (restorePersistedValue) return;
        if (!TextUtils.isEmpty(defaultValue)) onSaveRingtone(Uri.parse(defaultValue));
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);
        preferenceManager.registerOnActivityResultListener(this);
        mRequestCode = preferenceManager.getNextRequestCode();
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != mRequestCode) return false;
        if (data != null) {
            Uri uri = data.getParcelableExtra(EXTRA_RINGTONE_PICKED_URI);
            if (callChangeListener(uri != null ? uri.toString() : "")) onSaveRingtone(uri);
        }
        return true;
    }
}
