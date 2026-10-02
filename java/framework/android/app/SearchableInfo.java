package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import android.view.inputmethod.EditorInfo;
import java.io.IOException;
import java.util.HashMap;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Searchability meta-data for an activity, read from its "android.app.searchable"
 * meta-data XML (a searchable element with optional actionkey children).
 */
public final class SearchableInfo implements Parcelable {
    private static final String LOG_TAG = "SearchableInfo";

    private static final String MD_LABEL_SEARCHABLE = "android.app.searchable";
    private static final String MD_XML_ELEMENT_SEARCHABLE = "searchable";
    private static final String MD_XML_ELEMENT_SEARCHABLE_ACTION_KEY = "actionkey";

    private static final int SEARCH_MODE_BADGE_LABEL = 0x04;
    private static final int SEARCH_MODE_BADGE_ICON = 0x08;
    private static final int SEARCH_MODE_QUERY_REWRITE_FROM_DATA = 0x10;
    private static final int SEARCH_MODE_QUERY_REWRITE_FROM_TEXT = 0x20;

    private static final int VOICE_SEARCH_SHOW_BUTTON = 1;
    private static final int VOICE_SEARCH_LAUNCH_WEB_SEARCH = 2;
    private static final int VOICE_SEARCH_LAUNCH_RECOGNIZER = 4;

    // com.android.internal.R.styleable.Searchable
    private static final int[] SEARCHABLE_ATTRS = {
        android.R.attr.label, android.R.attr.hint, android.R.attr.icon, android.R.attr.searchMode,
        android.R.attr.searchButtonText, android.R.attr.inputType, android.R.attr.imeOptions,
        android.R.attr.includeInGlobalSearch, android.R.attr.queryAfterZeroResults,
        android.R.attr.autoUrlDetect, android.R.attr.searchSettingsDescription,
        android.R.attr.searchSuggestAuthority, android.R.attr.searchSuggestPath,
        android.R.attr.searchSuggestSelection, android.R.attr.searchSuggestIntentAction,
        android.R.attr.searchSuggestIntentData, android.R.attr.searchSuggestThreshold,
        android.R.attr.voiceSearchMode, android.R.attr.voiceLanguageModel,
        android.R.attr.voicePromptText, android.R.attr.voiceLanguage, android.R.attr.voiceMaxResults,
    };
    private static final int S_LABEL = 0;
    private static final int S_HINT = 1;
    private static final int S_ICON = 2;
    private static final int S_SEARCH_MODE = 3;
    private static final int S_SEARCH_BUTTON_TEXT = 4;
    private static final int S_INPUT_TYPE = 5;
    private static final int S_IME_OPTIONS = 6;
    private static final int S_INCLUDE_IN_GLOBAL_SEARCH = 7;
    private static final int S_QUERY_AFTER_ZERO_RESULTS = 8;
    private static final int S_AUTO_URL_DETECT = 9;
    private static final int S_SETTINGS_DESCRIPTION = 10;
    private static final int S_SUGGEST_AUTHORITY = 11;
    private static final int S_SUGGEST_PATH = 12;
    private static final int S_SUGGEST_SELECTION = 13;
    private static final int S_SUGGEST_INTENT_ACTION = 14;
    private static final int S_SUGGEST_INTENT_DATA = 15;
    private static final int S_SUGGEST_THRESHOLD = 16;
    private static final int S_VOICE_SEARCH_MODE = 17;
    private static final int S_VOICE_LANGUAGE_MODEL = 18;
    private static final int S_VOICE_PROMPT_TEXT = 19;
    private static final int S_VOICE_LANGUAGE = 20;
    private static final int S_VOICE_MAX_RESULTS = 21;

    // com.android.internal.R.styleable.SearchableActionKey
    private static final int[] ACTION_KEY_ATTRS = {
        android.R.attr.keycode, android.R.attr.queryActionMsg, android.R.attr.suggestActionMsg,
        android.R.attr.suggestActionMsgColumn,
    };

    private final int mLabelId;
    private final ComponentName mSearchActivity;
    private final int mHintId;
    private final int mSearchMode;
    private final int mIconId;
    private final int mSearchButtonText;
    private final int mSearchInputType;
    private final int mSearchImeOptions;
    private final boolean mIncludeInGlobalSearch;
    private final boolean mQueryAfterZeroResults;
    private final boolean mAutoUrlDetect;
    private final int mSettingsDescriptionId;
    private final String mSuggestAuthority;
    private final String mSuggestPath;
    private final String mSuggestSelection;
    private final String mSuggestIntentAction;
    private final String mSuggestIntentData;
    private final int mSuggestThreshold;
    private HashMap<Integer, ActionKeyInfo> mActionKeys = null;
    private final String mSuggestProviderPackage;

    private final int mVoiceSearchMode;
    private final int mVoiceLanguageModeId;
    private final int mVoicePromptTextId;
    private final int mVoiceLanguageId;
    private final int mVoiceMaxResults;

    public String getSuggestAuthority() { return mSuggestAuthority; }

    public String getSuggestPackage() { return mSuggestProviderPackage; }

    public ComponentName getSearchActivity() { return mSearchActivity; }

    /** framework-internal (hidden in AOSP). */
    public boolean useBadgeLabel() { return 0 != (mSearchMode & SEARCH_MODE_BADGE_LABEL); }

    /** framework-internal (hidden in AOSP). */
    public boolean useBadgeIcon() { return (0 != (mSearchMode & SEARCH_MODE_BADGE_ICON)) && (mIconId != 0); }

    public boolean shouldRewriteQueryFromData() { return 0 != (mSearchMode & SEARCH_MODE_QUERY_REWRITE_FROM_DATA); }

    public boolean shouldRewriteQueryFromText() { return 0 != (mSearchMode & SEARCH_MODE_QUERY_REWRITE_FROM_TEXT); }

    public int getSettingsDescriptionId() { return mSettingsDescriptionId; }

    public String getSuggestPath() { return mSuggestPath; }

    public String getSuggestSelection() { return mSuggestSelection; }

    public String getSuggestIntentAction() { return mSuggestIntentAction; }

    public String getSuggestIntentData() { return mSuggestIntentData; }

    public int getSuggestThreshold() { return mSuggestThreshold; }

    /** framework-internal (hidden in AOSP). The context of the searchable activity's package. */
    public Context getActivityContext(Context context) { return createActivityContext(context, mSearchActivity); }

    private static Context createActivityContext(Context context, ComponentName activity) {
        try {
            return context.createPackageContext(activity.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "Package not found " + activity.getPackageName());
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Can't make context for " + activity.getPackageName(), e);
        }
        return null;
    }

    /** framework-internal (hidden in AOSP). The context of the suggestion provider's package. */
    public Context getProviderContext(Context context, Context activityContext) {
        if (mSearchActivity.getPackageName().equals(mSuggestProviderPackage)) return activityContext;
        if (mSuggestProviderPackage != null) {
            try {
                return context.createPackageContext(mSuggestProviderPackage, 0);
            } catch (PackageManager.NameNotFoundException e) {
                // unknown package
            } catch (SecurityException e) {
                // no permission
            }
        }
        return null;
    }

    private SearchableInfo(Context activityContext, AttributeSet attr, final ComponentName cName) {
        mSearchActivity = cName;

        TypedArray a = activityContext.obtainStyledAttributes(attr, SEARCHABLE_ATTRS);
        mSearchMode = a.getInt(S_SEARCH_MODE, 0);
        mLabelId = a.getResourceId(S_LABEL, 0);
        mHintId = a.getResourceId(S_HINT, 0);
        mIconId = a.getResourceId(S_ICON, 0);
        mSearchButtonText = a.getResourceId(S_SEARCH_BUTTON_TEXT, 0);
        mSearchInputType = a.getInt(S_INPUT_TYPE, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        mSearchImeOptions = a.getInt(S_IME_OPTIONS, EditorInfo.IME_ACTION_GO);
        mIncludeInGlobalSearch = a.getBoolean(S_INCLUDE_IN_GLOBAL_SEARCH, false);
        mQueryAfterZeroResults = a.getBoolean(S_QUERY_AFTER_ZERO_RESULTS, false);
        mAutoUrlDetect = a.getBoolean(S_AUTO_URL_DETECT, false);
        mSettingsDescriptionId = a.getResourceId(S_SETTINGS_DESCRIPTION, 0);
        mSuggestAuthority = a.getString(S_SUGGEST_AUTHORITY);
        mSuggestPath = a.getString(S_SUGGEST_PATH);
        mSuggestSelection = a.getString(S_SUGGEST_SELECTION);
        mSuggestIntentAction = a.getString(S_SUGGEST_INTENT_ACTION);
        mSuggestIntentData = a.getString(S_SUGGEST_INTENT_DATA);
        mSuggestThreshold = a.getInt(S_SUGGEST_THRESHOLD, 0);
        mVoiceSearchMode = a.getInt(S_VOICE_SEARCH_MODE, 0);
        mVoiceLanguageModeId = a.getResourceId(S_VOICE_LANGUAGE_MODEL, 0);
        mVoicePromptTextId = a.getResourceId(S_VOICE_PROMPT_TEXT, 0);
        mVoiceLanguageId = a.getResourceId(S_VOICE_LANGUAGE, 0);
        mVoiceMaxResults = a.getInt(S_VOICE_MAX_RESULTS, 0);
        a.recycle();

        String suggestProviderPackage = null;
        if (mSuggestAuthority != null) {
            PackageManager pm = activityContext.getPackageManager();
            ProviderInfo pi = pm.resolveContentProvider(mSuggestAuthority, 0);
            if (pi != null) suggestProviderPackage = pi.packageName;
        }
        mSuggestProviderPackage = suggestProviderPackage;

        if (mLabelId == 0) {
            throw new IllegalArgumentException("Search label must be a resource reference.");
        }
    }

    /** framework-internal (hidden in AOSP). Key handling for a searchable actionkey element. */
    public static class ActionKeyInfo implements Parcelable {
        private final int mKeyCode;
        private final String mQueryActionMsg;
        private final String mSuggestActionMsg;
        private final String mSuggestActionMsgColumn;

        ActionKeyInfo(Context activityContext, AttributeSet attr) {
            TypedArray a = activityContext.obtainStyledAttributes(attr, ACTION_KEY_ATTRS);
            mKeyCode = a.getInt(0, 0);
            mQueryActionMsg = a.getString(1);
            mSuggestActionMsg = a.getString(2);
            mSuggestActionMsgColumn = a.getString(3);
            a.recycle();

            if (mKeyCode == 0) {
                throw new IllegalArgumentException("No keycode.");
            } else if ((mQueryActionMsg == null) && (mSuggestActionMsg == null)
                    && (mSuggestActionMsgColumn == null)) {
                throw new IllegalArgumentException("No message information.");
            }
        }

        private ActionKeyInfo(Parcel in) {
            mKeyCode = in.readInt();
            mQueryActionMsg = in.readString();
            mSuggestActionMsg = in.readString();
            mSuggestActionMsgColumn = in.readString();
        }

        /** framework-internal (hidden in AOSP). */
        public int getKeyCode() { return mKeyCode; }

        /** framework-internal (hidden in AOSP). */
        public String getQueryActionMsg() { return mQueryActionMsg; }

        /** framework-internal (hidden in AOSP). */
        public String getSuggestActionMsg() { return mSuggestActionMsg; }

        /** framework-internal (hidden in AOSP). */
        public String getSuggestActionMsgColumn() { return mSuggestActionMsgColumn; }

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mKeyCode);
            dest.writeString(mQueryActionMsg);
            dest.writeString(mSuggestActionMsg);
            dest.writeString(mSuggestActionMsgColumn);
        }
    }

    /** framework-internal (hidden in AOSP). The actionkey for a key code, or null. */
    public ActionKeyInfo findActionKey(int keyCode) {
        if (mActionKeys == null) return null;
        return mActionKeys.get(keyCode);
    }

    private void addActionKey(ActionKeyInfo keyInfo) {
        if (mActionKeys == null) mActionKeys = new HashMap<Integer, ActionKeyInfo>();
        mActionKeys.put(keyInfo.getKeyCode(), keyInfo);
    }

    /** framework-internal (hidden in AOSP). Reads an activity's searchable meta-data, or null. */
    public static SearchableInfo getActivityMetaData(Context context, ActivityInfo activityInfo, int userId) {
        XmlResourceParser xml = activityInfo.loadXmlMetaData(context.getPackageManager(), MD_LABEL_SEARCHABLE);
        if (xml == null) return null;
        ComponentName cName = new ComponentName(activityInfo.packageName, activityInfo.name);
        SearchableInfo searchable = getActivityMetaData(context, xml, cName);
        xml.close();
        return searchable;
    }

    private static SearchableInfo getActivityMetaData(Context context, XmlPullParser xml, final ComponentName cName) {
        SearchableInfo result = null;
        Context activityContext = createActivityContext(context, cName);
        if (activityContext == null) return null;
        try {
            int tagType = xml.next();
            while (tagType != XmlPullParser.END_DOCUMENT) {
                if (tagType == XmlPullParser.START_TAG) {
                    if (xml.getName().equals(MD_XML_ELEMENT_SEARCHABLE)) {
                        AttributeSet attr = Xml.asAttributeSet(xml);
                        if (attr != null) {
                            try {
                                result = new SearchableInfo(activityContext, attr, cName);
                            } catch (IllegalArgumentException ex) {
                                Log.w(LOG_TAG, "Invalid searchable metadata for " + cName.flattenToShortString()
                                        + ": " + ex.getMessage());
                                return null;
                            }
                        }
                    } else if (xml.getName().equals(MD_XML_ELEMENT_SEARCHABLE_ACTION_KEY)) {
                        if (result == null) return null;
                        AttributeSet attr = Xml.asAttributeSet(xml);
                        if (attr != null) {
                            try {
                                result.addActionKey(new ActionKeyInfo(activityContext, attr));
                            } catch (IllegalArgumentException ex) {
                                Log.w(LOG_TAG, "Invalid action key for " + cName.flattenToShortString()
                                        + ": " + ex.getMessage());
                                return null;
                            }
                        }
                    }
                }
                tagType = xml.next();
            }
        } catch (XmlPullParserException e) {
            Log.w(LOG_TAG, "Reading searchable metadata for " + cName.flattenToShortString(), e);
            return null;
        } catch (IOException e) {
            Log.w(LOG_TAG, "Reading searchable metadata for " + cName.flattenToShortString(), e);
            return null;
        }
        return result;
    }

    /** framework-internal (hidden in AOSP). */
    public int getLabelId() { return mLabelId; }

    public int getHintId() { return mHintId; }

    /** framework-internal (hidden in AOSP). */
    public int getIconId() { return mIconId; }

    public boolean getVoiceSearchEnabled() { return 0 != (mVoiceSearchMode & VOICE_SEARCH_SHOW_BUTTON); }

    public boolean getVoiceSearchLaunchWebSearch() { return 0 != (mVoiceSearchMode & VOICE_SEARCH_LAUNCH_WEB_SEARCH); }

    public boolean getVoiceSearchLaunchRecognizer() { return 0 != (mVoiceSearchMode & VOICE_SEARCH_LAUNCH_RECOGNIZER); }

    public int getVoiceLanguageModeId() { return mVoiceLanguageModeId; }

    public int getVoicePromptTextId() { return mVoicePromptTextId; }

    public int getVoiceLanguageId() { return mVoiceLanguageId; }

    public int getVoiceMaxResults() { return mVoiceMaxResults; }

    /** framework-internal (hidden in AOSP). */
    public int getSearchButtonText() { return mSearchButtonText; }

    public int getInputType() { return mSearchInputType; }

    public int getImeOptions() { return mSearchImeOptions; }

    public boolean shouldIncludeInGlobalSearch() { return mIncludeInGlobalSearch; }

    public boolean queryAfterZeroResults() { return mQueryAfterZeroResults; }

    public boolean autoUrlDetect() { return mAutoUrlDetect; }

    public static final Parcelable.Creator<SearchableInfo> CREATOR = new Parcelable.Creator<SearchableInfo>() {
        public SearchableInfo createFromParcel(Parcel in) { return new SearchableInfo(in); }

        public SearchableInfo[] newArray(int size) { return new SearchableInfo[size]; }
    };

    SearchableInfo(Parcel in) {
        mLabelId = in.readInt();
        mSearchActivity = ComponentName.readFromParcel(in);
        mHintId = in.readInt();
        mSearchMode = in.readInt();
        mIconId = in.readInt();
        mSearchButtonText = in.readInt();
        mSearchInputType = in.readInt();
        mSearchImeOptions = in.readInt();
        mIncludeInGlobalSearch = in.readInt() != 0;
        mQueryAfterZeroResults = in.readInt() != 0;
        mAutoUrlDetect = in.readInt() != 0;
        mSettingsDescriptionId = in.readInt();
        mSuggestAuthority = in.readString();
        mSuggestPath = in.readString();
        mSuggestSelection = in.readString();
        mSuggestIntentAction = in.readString();
        mSuggestIntentData = in.readString();
        mSuggestThreshold = in.readInt();
        for (int count = in.readInt(); count > 0; count--) addActionKey(new ActionKeyInfo(in));
        mSuggestProviderPackage = in.readString();
        mVoiceSearchMode = in.readInt();
        mVoiceLanguageModeId = in.readInt();
        mVoicePromptTextId = in.readInt();
        mVoiceLanguageId = in.readInt();
        mVoiceMaxResults = in.readInt();
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mLabelId);
        ComponentName.writeToParcel(mSearchActivity, dest);
        dest.writeInt(mHintId);
        dest.writeInt(mSearchMode);
        dest.writeInt(mIconId);
        dest.writeInt(mSearchButtonText);
        dest.writeInt(mSearchInputType);
        dest.writeInt(mSearchImeOptions);
        dest.writeInt(mIncludeInGlobalSearch ? 1 : 0);
        dest.writeInt(mQueryAfterZeroResults ? 1 : 0);
        dest.writeInt(mAutoUrlDetect ? 1 : 0);
        dest.writeInt(mSettingsDescriptionId);
        dest.writeString(mSuggestAuthority);
        dest.writeString(mSuggestPath);
        dest.writeString(mSuggestSelection);
        dest.writeString(mSuggestIntentAction);
        dest.writeString(mSuggestIntentData);
        dest.writeInt(mSuggestThreshold);
        if (mActionKeys == null) {
            dest.writeInt(0);
        } else {
            dest.writeInt(mActionKeys.size());
            for (ActionKeyInfo actionKey : mActionKeys.values()) actionKey.writeToParcel(dest, flags);
        }
        dest.writeString(mSuggestProviderPackage);
        dest.writeInt(mVoiceSearchMode);
        dest.writeInt(mVoiceLanguageModeId);
        dest.writeInt(mVoicePromptTextId);
        dest.writeInt(mVoiceLanguageId);
        dest.writeInt(mVoiceMaxResults);
    }
}
