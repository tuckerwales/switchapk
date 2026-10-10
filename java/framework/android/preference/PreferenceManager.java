package android.preference;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of AOSP's PreferenceManager (WS4): owns the SharedPreferences a hierarchy persists to, inflates hierarchies
 * from XML, and relays activity results and lifecycle to preferences that asked for them.
 */
@Deprecated
public class PreferenceManager {
    public static final String KEY_HAS_SET_DEFAULT_VALUES = "_has_set_default_values";
    public static final String METADATA_KEY_PREFERENCES = "android.preference";

    private static final int STORAGE_DEFAULT = 0;
    private static final int STORAGE_DEVICE_PROTECTED = 1;

    private Activity mActivity;
    private Fragment mFragment;
    private final Context mContext;
    private long mNextId = 0;
    private int mNextRequestCode;
    private SharedPreferences mSharedPreferences;
    private PreferenceDataStore mPreferenceDataStore;
    private SharedPreferences.Editor mEditor;
    private boolean mNoCommit;
    private String mSharedPreferencesName;
    private int mSharedPreferencesMode;
    private int mStorage = STORAGE_DEFAULT;
    private PreferenceScreen mPreferenceScreen;
    private List<OnActivityResultListener> mActivityResultListeners;
    private List<OnActivityStopListener> mActivityStopListeners;
    private List<OnActivityDestroyListener> mActivityDestroyListeners;
    private List<DialogInterface> mPreferencesScreens;
    private OnPreferenceTreeClickListener mOnPreferenceTreeClickListener;

    /** framework-internal (hidden in AOSP): the manager behind a PreferenceActivity or PreferenceFragment. */
    PreferenceManager(Activity activity, int firstRequestCode) {
        mActivity = activity;
        mNextRequestCode = firstRequestCode;
        mContext = activity;
        init(activity);
    }

    private PreferenceManager(Context context) {
        mContext = context;
        init(context);
    }

    private void init(Context context) { setSharedPreferencesName(getDefaultSharedPreferencesName(context)); }

    void setFragment(Fragment fragment) { mFragment = fragment; }
    Fragment getFragment() { return mFragment; }

    public void setPreferenceDataStore(PreferenceDataStore dataStore) { mPreferenceDataStore = dataStore; }
    public PreferenceDataStore getPreferenceDataStore() { return mPreferenceDataStore; }

    /** No intent-declared preference hierarchies exist (no other packages), so this inflates nothing. */
    PreferenceScreen inflateFromIntent(Intent queryIntent, PreferenceScreen rootPreferences) {
        if (rootPreferences == null) {
            rootPreferences = createPreferenceScreen(mContext);
            rootPreferences.onAttachedToHierarchy(this);
        }
        return rootPreferences;
    }

    /** framework-internal: inflate resId under rootPreferences (or a new root), with commits held until done. */
    public PreferenceScreen inflateFromResource(Context context, int resId, PreferenceScreen rootPreferences) {
        setNoCommit(true);
        final PreferenceInflater inflater = new PreferenceInflater(context, this);
        rootPreferences = (PreferenceScreen) inflater.inflate(resId, rootPreferences, true);
        rootPreferences.onAttachedToHierarchy(this);
        setNoCommit(false);
        return rootPreferences;
    }

    public PreferenceScreen createPreferenceScreen(Context context) {
        final PreferenceScreen preferenceScreen = new PreferenceScreen(context, null);
        preferenceScreen.onAttachedToHierarchy(this);
        return preferenceScreen;
    }

    long getNextId() {
        synchronized (this) {
            return mNextId++;
        }
    }

    public String getSharedPreferencesName() { return mSharedPreferencesName; }

    public void setSharedPreferencesName(String sharedPreferencesName) {
        mSharedPreferencesName = sharedPreferencesName;
        mSharedPreferences = null;
    }

    public int getSharedPreferencesMode() { return mSharedPreferencesMode; }

    public void setSharedPreferencesMode(int sharedPreferencesMode) {
        mSharedPreferencesMode = sharedPreferencesMode;
        mSharedPreferences = null;
    }

    public void setStorageDefault() {
        mStorage = STORAGE_DEFAULT;
        mSharedPreferences = null;
    }

    public void setStorageDeviceProtected() {
        mStorage = STORAGE_DEVICE_PROTECTED;
        mSharedPreferences = null;
    }

    public boolean isStorageDefault() { return mStorage == STORAGE_DEFAULT; }
    public boolean isStorageDeviceProtected() { return mStorage == STORAGE_DEVICE_PROTECTED; }

    public SharedPreferences getSharedPreferences() {
        if (mPreferenceDataStore != null) return null;
        if (mSharedPreferences == null) {
            Context storageContext = mStorage == STORAGE_DEVICE_PROTECTED
                    ? mContext.createDeviceProtectedStorageContext() : mContext;
            mSharedPreferences = storageContext.getSharedPreferences(mSharedPreferencesName, mSharedPreferencesMode);
        }
        return mSharedPreferences;
    }

    public static SharedPreferences getDefaultSharedPreferences(Context context) {
        return context.getSharedPreferences(getDefaultSharedPreferencesName(context), Context.MODE_PRIVATE);
    }

    public static String getDefaultSharedPreferencesName(Context context) {
        return context.getPackageName() + "_preferences";
    }

    PreferenceScreen getPreferenceScreen() { return mPreferenceScreen; }

    boolean setPreferences(PreferenceScreen preferenceScreen) {
        if (preferenceScreen != mPreferenceScreen) {
            mPreferenceScreen = preferenceScreen;
            return true;
        }
        return false;
    }

    public Preference findPreference(CharSequence key) {
        if (mPreferenceScreen == null) return null;
        return mPreferenceScreen.findPreference(key);
    }

    public static void setDefaultValues(Context context, int resId, boolean readAgain) {
        setDefaultValues(context, getDefaultSharedPreferencesName(context), Context.MODE_PRIVATE, resId, readAgain);
    }

    public static void setDefaultValues(Context context, String sharedPreferencesName, int sharedPreferencesMode,
            int resId, boolean readAgain) {
        final SharedPreferences defaultValueSp =
                context.getSharedPreferences(KEY_HAS_SET_DEFAULT_VALUES, Context.MODE_PRIVATE);
        if (readAgain || !defaultValueSp.getBoolean(KEY_HAS_SET_DEFAULT_VALUES, false)) {
            final PreferenceManager pm = new PreferenceManager(context);
            pm.setSharedPreferencesName(sharedPreferencesName);
            pm.setSharedPreferencesMode(sharedPreferencesMode);
            pm.inflateFromResource(context, resId, null);
            defaultValueSp.edit().putBoolean(KEY_HAS_SET_DEFAULT_VALUES, true).apply();
        }
    }

    SharedPreferences.Editor getEditor() {
        if (mPreferenceDataStore != null) return null;
        if (mNoCommit) {
            if (mEditor == null) mEditor = getSharedPreferences().edit();
            return mEditor;
        }
        return getSharedPreferences().edit();
    }

    boolean shouldCommit() { return !mNoCommit; }

    private void setNoCommit(boolean noCommit) {
        if (!noCommit && mEditor != null) mEditor.apply();
        mNoCommit = noCommit;
    }

    Activity getActivity() { return mActivity; }
    Context getContext() { return mContext; }

    void registerOnActivityResultListener(OnActivityResultListener listener) {
        synchronized (this) {
            if (mActivityResultListeners == null) mActivityResultListeners = new ArrayList<OnActivityResultListener>();
            if (!mActivityResultListeners.contains(listener)) mActivityResultListeners.add(listener);
        }
    }

    void unregisterOnActivityResultListener(OnActivityResultListener listener) {
        synchronized (this) {
            if (mActivityResultListeners != null) mActivityResultListeners.remove(listener);
        }
    }

    void dispatchActivityResult(int requestCode, int resultCode, Intent data) {
        List<OnActivityResultListener> list;
        synchronized (this) {
            if (mActivityResultListeners == null) return;
            list = new ArrayList<OnActivityResultListener>(mActivityResultListeners);
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).onActivityResult(requestCode, resultCode, data)) break;
        }
    }

    /** framework-internal (hidden in AOSP) */
    public void registerOnActivityStopListener(OnActivityStopListener listener) {
        synchronized (this) {
            if (mActivityStopListeners == null) mActivityStopListeners = new ArrayList<OnActivityStopListener>();
            if (!mActivityStopListeners.contains(listener)) mActivityStopListeners.add(listener);
        }
    }

    /** framework-internal (hidden in AOSP) */
    public void unregisterOnActivityStopListener(OnActivityStopListener listener) {
        synchronized (this) {
            if (mActivityStopListeners != null) mActivityStopListeners.remove(listener);
        }
    }

    void dispatchActivityStop() {
        List<OnActivityStopListener> list;
        synchronized (this) {
            if (mActivityStopListeners == null) return;
            list = new ArrayList<OnActivityStopListener>(mActivityStopListeners);
        }
        for (int i = 0; i < list.size(); i++) list.get(i).onActivityStop();
    }

    void registerOnActivityDestroyListener(OnActivityDestroyListener listener) {
        synchronized (this) {
            if (mActivityDestroyListeners == null) {
                mActivityDestroyListeners = new ArrayList<OnActivityDestroyListener>();
            }
            if (!mActivityDestroyListeners.contains(listener)) mActivityDestroyListeners.add(listener);
        }
    }

    void unregisterOnActivityDestroyListener(OnActivityDestroyListener listener) {
        synchronized (this) {
            if (mActivityDestroyListeners != null) mActivityDestroyListeners.remove(listener);
        }
    }

    void dispatchActivityDestroy() {
        List<OnActivityDestroyListener> list = null;
        synchronized (this) {
            if (mActivityDestroyListeners != null) {
                list = new ArrayList<OnActivityDestroyListener>(mActivityDestroyListeners);
            }
        }
        if (list != null) {
            for (int i = 0; i < list.size(); i++) list.get(i).onActivityDestroy();
        }
        dismissAllScreens();
    }

    int getNextRequestCode() {
        synchronized (this) {
            return mNextRequestCode++;
        }
    }

    void addPreferencesScreen(DialogInterface screen) {
        synchronized (this) {
            if (mPreferencesScreens == null) mPreferencesScreens = new ArrayList<DialogInterface>();
            mPreferencesScreens.add(screen);
        }
    }

    void removePreferencesScreen(DialogInterface screen) {
        synchronized (this) {
            if (mPreferencesScreens == null) return;
            mPreferencesScreens.remove(screen);
        }
    }

    void dispatchNewIntent(Intent intent) { dismissAllScreens(); }

    private void dismissAllScreens() {
        ArrayList<DialogInterface> screensToDismiss;
        synchronized (this) {
            if (mPreferencesScreens == null) return;
            screensToDismiss = new ArrayList<DialogInterface>(mPreferencesScreens);
            mPreferencesScreens.clear();
        }
        for (int i = screensToDismiss.size() - 1; i >= 0; i--) screensToDismiss.get(i).dismiss();
    }

    void setOnPreferenceTreeClickListener(OnPreferenceTreeClickListener listener) {
        mOnPreferenceTreeClickListener = listener;
    }

    OnPreferenceTreeClickListener getOnPreferenceTreeClickListener() { return mOnPreferenceTreeClickListener; }

    /** framework-internal (hidden in AOSP): PreferenceActivity and PreferenceFragment hear row clicks. */
    interface OnPreferenceTreeClickListener {
        boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference);
    }

    public interface OnActivityResultListener {
        boolean onActivityResult(int requestCode, int resultCode, Intent data);
    }

    public interface OnActivityStopListener {
        void onActivityStop();
    }

    public interface OnActivityDestroyListener {
        void onActivityDestroy();
    }
}
