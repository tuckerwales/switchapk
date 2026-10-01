package android.app;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Port of AOSP RemoteInput. Results travel in the intent's ClipData as on
 * Android, so apps reading replies with getResultsFromIntent work.
 */
public final class RemoteInput implements Parcelable {
    public static final String RESULTS_CLIP_LABEL = "android.remoteinput.results";
    public static final String EXTRA_RESULTS_DATA = "android.remoteinput.resultsData";
    private static final String EXTRA_DATA_TYPE_RESULTS_DATA = "android.remoteinput.dataTypeResultsData";
    private static final String EXTRA_RESULTS_SOURCE = "android.remoteinput.resultsSource";
    public static final int SOURCE_FREE_FORM_INPUT = 0;
    public static final int SOURCE_CHOICE = 1;
    public static final int EDIT_CHOICES_BEFORE_SENDING_AUTO = 0;
    public static final int EDIT_CHOICES_BEFORE_SENDING_DISABLED = 1;
    public static final int EDIT_CHOICES_BEFORE_SENDING_ENABLED = 2;

    private String mResultKey;
    private CharSequence mLabel;
    private CharSequence[] mChoices;
    private boolean mAllowFreeFormInput = true;
    private int mEditChoicesBeforeSending;
    private Bundle mExtras = new Bundle();
    private Set<String> mAllowedDataTypes = new HashSet<String>();

    RemoteInput() {}

    public String getResultKey() { return mResultKey; }

    public CharSequence getLabel() { return mLabel; }

    public CharSequence[] getChoices() { return mChoices; }

    public Set<String> getAllowedDataTypes() { return mAllowedDataTypes; }

    public boolean isDataOnly() {
        return !mAllowFreeFormInput && (mChoices == null || mChoices.length == 0) && !mAllowedDataTypes.isEmpty();
    }

    public boolean getAllowFreeFormInput() { return mAllowFreeFormInput; }

    public int getEditChoicesBeforeSending() { return mEditChoicesBeforeSending; }

    public Bundle getExtras() { return mExtras; }

    private static Intent getClipDataIntentFromIntent(Intent intent) {
        ClipData clipData = intent.getClipData();
        if (clipData == null) return null;
        ClipDescription desc = clipData.getDescription();
        if (!desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_INTENT)) return null;
        if (!RESULTS_CLIP_LABEL.contentEquals(desc.getLabel())) return null;
        return clipData.getItemAt(0).getIntent();
    }

    public static Map<String, Uri> getDataResultsFromIntent(Intent intent, String remoteInputResultKey) {
        Intent clipDataIntent = getClipDataIntentFromIntent(intent);
        if (clipDataIntent == null) return null;
        Map<String, Uri> results = new HashMap<String, Uri>();
        Bundle extras = clipDataIntent.getExtras();
        if (extras == null) return null;
        for (String key : extras.keySet()) {
            if (key.startsWith(EXTRA_DATA_TYPE_RESULTS_DATA)) {
                String mimeType = key.substring(EXTRA_DATA_TYPE_RESULTS_DATA.length());
                if (mimeType.isEmpty()) continue;
                Bundle b = clipDataIntent.getBundleExtra(key);
                String uriStr = b != null ? b.getString(remoteInputResultKey) : null;
                if (uriStr == null || uriStr.isEmpty()) continue;
                results.put(mimeType, Uri.parse(uriStr));
            }
        }
        return results.isEmpty() ? null : results;
    }

    public static Bundle getResultsFromIntent(Intent intent) {
        Intent clipDataIntent = getClipDataIntentFromIntent(intent);
        if (clipDataIntent == null) return null;
        return clipDataIntent.getExtras() != null ? clipDataIntent.getExtras().getBundle(EXTRA_RESULTS_DATA) : null;
    }

    public static void addResultsToIntent(RemoteInput[] remoteInputs, Intent intent, Bundle results) {
        Intent clipDataIntent = getClipDataIntentFromIntent(intent);
        if (clipDataIntent == null) clipDataIntent = new Intent();
        Bundle resultsBundle = clipDataIntent.getBundleExtra(EXTRA_RESULTS_DATA);
        if (resultsBundle == null) resultsBundle = new Bundle();
        for (RemoteInput remoteInput : remoteInputs) {
            Object result = results.get(remoteInput.getResultKey());
            if (result instanceof CharSequence) resultsBundle.putCharSequence(remoteInput.getResultKey(), (CharSequence) result);
        }
        clipDataIntent.putExtra(EXTRA_RESULTS_DATA, resultsBundle);
        intent.setClipData(ClipData.newIntent(RESULTS_CLIP_LABEL, clipDataIntent));
    }

    public static void addDataResultToIntent(RemoteInput remoteInput, Intent intent, Map<String, Uri> results) {
        Intent clipDataIntent = getClipDataIntentFromIntent(intent);
        if (clipDataIntent == null) clipDataIntent = new Intent();
        for (Map.Entry<String, Uri> entry : results.entrySet()) {
            String key = EXTRA_DATA_TYPE_RESULTS_DATA + entry.getKey();
            Bundle b = clipDataIntent.getBundleExtra(key);
            if (b == null) b = new Bundle();
            b.putString(remoteInput.getResultKey(), entry.getValue().toString());
            clipDataIntent.putExtra(key, b);
        }
        intent.setClipData(ClipData.newIntent(RESULTS_CLIP_LABEL, clipDataIntent));
    }

    public static void setResultsSource(Intent intent, int source) {
        Intent clipDataIntent = getClipDataIntentFromIntent(intent);
        if (clipDataIntent == null) clipDataIntent = new Intent();
        clipDataIntent.putExtra(EXTRA_RESULTS_SOURCE, source);
        intent.setClipData(ClipData.newIntent(RESULTS_CLIP_LABEL, clipDataIntent));
    }

    public static int getResultsSource(Intent intent) {
        Intent clipDataIntent = getClipDataIntentFromIntent(intent);
        if (clipDataIntent == null) return SOURCE_FREE_FORM_INPUT;
        return clipDataIntent.getIntExtra(EXTRA_RESULTS_SOURCE, SOURCE_FREE_FORM_INPUT);
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel out, int flags) { out.writeValue(this); }

    public static final class Builder {
        private final String mResultKey;
        private final Set<String> mAllowedDataTypes = new HashSet<String>();
        private final Bundle mExtras = new Bundle();
        private CharSequence mLabel;
        private CharSequence[] mChoices;
        private boolean mAllowFreeFormTextInput = true;
        private int mEditChoicesBeforeSending = EDIT_CHOICES_BEFORE_SENDING_AUTO;

        public Builder(String resultKey) {
            if (resultKey == null) throw new IllegalArgumentException("Result key can't be null");
            mResultKey = resultKey;
        }

        public Builder setLabel(CharSequence label) { mLabel = label; return this; }

        public Builder setChoices(CharSequence[] choices) { mChoices = choices; return this; }

        public Builder setAllowDataType(String mimeType, boolean doAllow) {
            if (doAllow) mAllowedDataTypes.add(mimeType);
            else mAllowedDataTypes.remove(mimeType);
            return this;
        }

        public Builder setAllowFreeFormInput(boolean allowFreeFormTextInput) {
            mAllowFreeFormTextInput = allowFreeFormTextInput;
            return this;
        }

        public Builder setEditChoicesBeforeSending(int editChoicesBeforeSending) {
            mEditChoicesBeforeSending = editChoicesBeforeSending;
            return this;
        }

        public Builder addExtras(Bundle extras) {
            if (extras != null) mExtras.putAll(extras);
            return this;
        }

        public Bundle getExtras() { return mExtras; }

        public RemoteInput build() {
            if (mEditChoicesBeforeSending == EDIT_CHOICES_BEFORE_SENDING_ENABLED && !mAllowFreeFormTextInput) {
                throw new IllegalArgumentException("setEditChoicesBeforeSending requires setAllowFreeFormInput");
            }
            RemoteInput r = new RemoteInput();
            r.mResultKey = mResultKey;
            r.mLabel = mLabel;
            r.mChoices = mChoices;
            r.mAllowFreeFormInput = mAllowFreeFormTextInput;
            r.mEditChoicesBeforeSending = mEditChoicesBeforeSending;
            r.mExtras = mExtras;
            r.mAllowedDataTypes = mAllowedDataTypes;
            return r;
        }
    }

    public static final Parcelable.Creator<RemoteInput> CREATOR = new Parcelable.Creator<RemoteInput>() {
        public RemoteInput createFromParcel(Parcel in) { return (RemoteInput) in.readValue(null); }
        public RemoteInput[] newArray(int size) { return new RemoteInput[size]; }
    };
}
