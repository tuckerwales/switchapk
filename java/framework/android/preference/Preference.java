package android.preference;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.AbsSavedState;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Port of AOSP's android.preference.Preference (WS4). Values persist to the PreferenceManager's SharedPreferences
 * (or a PreferenceDataStore); the row is inflated from the theme's preference layout (preference_material).
 */
@Deprecated
public class Preference implements Comparable<Preference> {
    public static final int DEFAULT_ORDER = Integer.MAX_VALUE;

    // com.android.internal.R.styleable.Preference, in our order
    private static final int[] ATTRS = {
        android.R.attr.icon, android.R.attr.key, android.R.attr.title, android.R.attr.summary, android.R.attr.order,
        android.R.attr.fragment, android.R.attr.layout, android.R.attr.widgetLayout, android.R.attr.enabled,
        android.R.attr.selectable, android.R.attr.dependency, android.R.attr.persistent, android.R.attr.defaultValue,
        android.R.attr.shouldDisableView, android.R.attr.recycleEnabled, android.R.attr.singleLineTitle,
        android.R.attr.iconSpaceReserved,
    };
    private static final int A_ICON = 0, A_KEY = 1, A_TITLE = 2, A_SUMMARY = 3, A_ORDER = 4, A_FRAGMENT = 5,
            A_LAYOUT = 6, A_WIDGET_LAYOUT = 7, A_ENABLED = 8, A_SELECTABLE = 9, A_DEPENDENCY = 10, A_PERSISTENT = 11,
            A_DEFAULT_VALUE = 12, A_SHOULD_DISABLE_VIEW = 13, A_RECYCLE_ENABLED = 14, A_SINGLE_LINE_TITLE = 15,
            A_ICON_SPACE_RESERVED = 16;

    private final Context mContext;
    private PreferenceManager mPreferenceManager;
    private PreferenceDataStore mPreferenceDataStore;
    private long mId;
    private boolean mHasId;
    private OnPreferenceChangeListener mOnChangeListener;
    private OnPreferenceClickListener mOnClickListener;
    private int mOrder = DEFAULT_ORDER;
    private CharSequence mTitle;
    private int mTitleRes;
    private CharSequence mSummary;
    private int mIconResId;
    private Drawable mIcon;
    private String mKey;
    private Intent mIntent;
    private String mFragment;
    private Bundle mExtras;
    private boolean mEnabled = true;
    private boolean mSelectable = true;
    private boolean mRequiresKey;
    private boolean mPersistent = true;
    private String mDependencyKey;
    private Object mDefaultValue;
    private boolean mDependencyMet = true;
    private boolean mParentDependencyMet = true;
    private boolean mShouldDisableView = true;
    private int mLayoutResId;
    private int mWidgetLayoutResId;
    private boolean mRecycleEnabled = true;
    private boolean mSingleLineTitle = true;
    private boolean mIconSpaceReserved;
    private OnPreferenceChangeInternalListener mListener;
    private List<Preference> mDependents;
    private PreferenceGroup mParentGroup;
    private boolean mBaseMethodCalled;

    /** framework-internal: the adapter's change callback (AOSP's hidden OnPreferenceChangeInternalListener). */
    interface OnPreferenceChangeInternalListener {
        void onPreferenceChange(Preference preference);
        void onPreferenceHierarchyChange(Preference preference);
    }

    public interface OnPreferenceChangeListener {
        boolean onPreferenceChange(Preference preference, Object newValue);
    }

    public interface OnPreferenceClickListener {
        boolean onPreferenceClick(Preference preference);
    }

    public Preference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        mContext = context;
        mLayoutResId = context.getResources().getIdentifier("preference", "layout", "android");
        TypedArray a = context.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, defStyleRes);
        mIconResId = a.getResourceId(A_ICON, 0);
        mKey = a.getString(A_KEY);
        mTitleRes = a.getResourceId(A_TITLE, 0);
        mTitle = a.getText(A_TITLE);
        mSummary = a.getText(A_SUMMARY);
        mOrder = a.getInt(A_ORDER, mOrder);
        mFragment = a.getString(A_FRAGMENT);
        mLayoutResId = a.getResourceId(A_LAYOUT, mLayoutResId);
        mWidgetLayoutResId = a.getResourceId(A_WIDGET_LAYOUT, mWidgetLayoutResId);
        mEnabled = a.getBoolean(A_ENABLED, true);
        mSelectable = a.getBoolean(A_SELECTABLE, true);
        mPersistent = a.getBoolean(A_PERSISTENT, mPersistent);
        mDependencyKey = a.getString(A_DEPENDENCY);
        if (a.hasValue(A_DEFAULT_VALUE)) mDefaultValue = onGetDefaultValue(a, A_DEFAULT_VALUE);
        mShouldDisableView = a.getBoolean(A_SHOULD_DISABLE_VIEW, mShouldDisableView);
        mRecycleEnabled = a.getBoolean(A_RECYCLE_ENABLED, mRecycleEnabled);
        mSingleLineTitle = a.getBoolean(A_SINGLE_LINE_TITLE, mSingleLineTitle);
        mIconSpaceReserved = a.getBoolean(A_ICON_SPACE_RESERVED, mIconSpaceReserved);
        a.recycle();
    }

    public Preference(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public Preference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.preferenceStyle);
    }

    public Preference(Context context) { this(context, null); }

    protected Object onGetDefaultValue(TypedArray a, int index) { return null; }

    public void setIntent(Intent intent) { mIntent = intent; }
    public Intent getIntent() { return mIntent; }
    public void setFragment(String fragment) { mFragment = fragment; }
    public String getFragment() { return mFragment; }
    public void setPreferenceDataStore(PreferenceDataStore dataStore) { mPreferenceDataStore = dataStore; }

    public PreferenceDataStore getPreferenceDataStore() {
        if (mPreferenceDataStore != null) return mPreferenceDataStore;
        if (mPreferenceManager != null) return mPreferenceManager.getPreferenceDataStore();
        return null;
    }

    public Bundle getExtras() {
        if (mExtras == null) mExtras = new Bundle();
        return mExtras;
    }

    public Bundle peekExtras() { return mExtras; }
    public void setLayoutResource(int layoutResId) {
        if (layoutResId != mLayoutResId) mRecycleEnabled = false;
        mLayoutResId = layoutResId;
    }
    public int getLayoutResource() { return mLayoutResId; }
    public void setWidgetLayoutResource(int widgetLayoutResId) {
        if (widgetLayoutResId != mWidgetLayoutResId) mRecycleEnabled = false;
        mWidgetLayoutResId = widgetLayoutResId;
    }
    public int getWidgetLayoutResource() { return mWidgetLayoutResId; }

    public View getView(View convertView, ViewGroup parent) {
        if (convertView == null) convertView = onCreateView(parent);
        onBindView(convertView);
        return convertView;
    }

    protected View onCreateView(ViewGroup parent) {
        final LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View layout = inflater.inflate(mLayoutResId, parent, false);
        final ViewGroup widgetFrame = (ViewGroup) layout.findViewById(android.R.id.widget_frame);
        if (widgetFrame != null) {
            if (mWidgetLayoutResId != 0) inflater.inflate(mWidgetLayoutResId, widgetFrame);
            else widgetFrame.setVisibility(View.GONE);
        }
        return layout;
    }

    protected void onBindView(View view) {
        final TextView titleView = (TextView) view.findViewById(android.R.id.title);
        if (titleView != null) {
            final CharSequence title = getTitle();
            if (!TextUtils.isEmpty(title)) {
                titleView.setText(title);
                titleView.setVisibility(View.VISIBLE);
                if (mSingleLineTitle) titleView.setSingleLine(true);
            } else {
                titleView.setVisibility(View.GONE);
            }
        }
        final TextView summaryView = (TextView) view.findViewById(android.R.id.summary);
        if (summaryView != null) {
            final CharSequence summary = getSummary();
            if (!TextUtils.isEmpty(summary)) {
                summaryView.setText(summary);
                summaryView.setVisibility(View.VISIBLE);
            } else {
                summaryView.setVisibility(View.GONE);
            }
        }
        final ImageView imageView = (ImageView) view.findViewById(android.R.id.icon);
        if (imageView != null) {
            if (mIconResId != 0 || mIcon != null) {
                if (mIcon == null) mIcon = getContext().getDrawable(mIconResId);
                if (mIcon != null) imageView.setImageDrawable(mIcon);
            }
            if (mIcon != null) imageView.setVisibility(View.VISIBLE);
            else imageView.setVisibility(mIconSpaceReserved ? View.INVISIBLE : View.GONE);
        }
        final View imageFrame = view.findViewById(android.R.id.icon_frame);
        if (imageFrame != null) {
            if (mIcon != null) imageFrame.setVisibility(View.VISIBLE);
            else imageFrame.setVisibility(mIconSpaceReserved ? View.INVISIBLE : View.GONE);
        }
        if (mShouldDisableView) setEnabledStateOnViews(view, isEnabled());
    }

    private void setEnabledStateOnViews(View v, boolean enabled) {
        v.setEnabled(enabled);
        if (v instanceof ViewGroup) {
            final ViewGroup vg = (ViewGroup) v;
            for (int i = vg.getChildCount() - 1; i >= 0; i--) setEnabledStateOnViews(vg.getChildAt(i), enabled);
        }
    }

    public void setOrder(int order) {
        if (order != mOrder) {
            mOrder = order;
            notifyHierarchyChanged();
        }
    }

    public int getOrder() { return mOrder; }

    public void setTitle(CharSequence title) {
        if (title == null && mTitle != null || title != null && !title.equals(mTitle)) {
            mTitleRes = 0;
            mTitle = title;
            notifyChanged();
        }
    }

    public void setTitle(int titleResId) {
        setTitle(mContext.getString(titleResId));
        mTitleRes = titleResId;
    }

    public int getTitleRes() { return mTitleRes; }
    public CharSequence getTitle() { return mTitle; }

    public void setIcon(Drawable icon) {
        if ((icon == null && mIcon != null) || (icon != null && mIcon != icon)) {
            mIcon = icon;
            notifyChanged();
        }
    }

    public void setIcon(int iconResId) {
        if (mIconResId != iconResId) {
            mIconResId = iconResId;
            setIcon(mContext.getDrawable(iconResId));
        }
    }

    public Drawable getIcon() {
        if (mIcon == null && mIconResId != 0) mIcon = getContext().getDrawable(mIconResId);
        return mIcon;
    }

    public CharSequence getSummary() { return mSummary; }

    public void setSummary(CharSequence summary) {
        if (summary == null && mSummary != null || summary != null && !summary.equals(mSummary)) {
            mSummary = summary;
            notifyChanged();
        }
    }

    public void setSummary(int summaryResId) { setSummary(mContext.getString(summaryResId)); }

    public void setEnabled(boolean enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;
            notifyDependencyChange(shouldDisableDependents());
            notifyChanged();
        }
    }

    public boolean isEnabled() { return mEnabled && mDependencyMet && mParentDependencyMet; }

    public void setSelectable(boolean selectable) {
        if (mSelectable != selectable) {
            mSelectable = selectable;
            notifyChanged();
        }
    }

    public boolean isSelectable() { return mSelectable; }

    public void setShouldDisableView(boolean shouldDisableView) {
        mShouldDisableView = shouldDisableView;
        notifyChanged();
    }

    public boolean getShouldDisableView() { return mShouldDisableView; }
    public void setRecycleEnabled(boolean enabled) {
        mRecycleEnabled = enabled;
        notifyChanged();
    }
    public boolean isRecycleEnabled() { return mRecycleEnabled; }
    public void setSingleLineTitle(boolean singleLineTitle) {
        mSingleLineTitle = singleLineTitle;
        notifyChanged();
    }
    public boolean isSingleLineTitle() { return mSingleLineTitle; }
    public void setIconSpaceReserved(boolean iconSpaceReserved) {
        mIconSpaceReserved = iconSpaceReserved;
        notifyChanged();
    }
    public boolean isIconSpaceReserved() { return mIconSpaceReserved; }

    long getId() { return mId; }

    protected void onClick() {}

    public void setKey(String key) {
        mKey = key;
        if (mRequiresKey && !hasKey()) requireKey();
    }

    public String getKey() { return mKey; }

    void requireKey() {
        if (mKey == null) throw new IllegalStateException("Preference does not have a key assigned.");
        mRequiresKey = true;
    }

    public boolean hasKey() { return !TextUtils.isEmpty(mKey); }
    public boolean isPersistent() { return mPersistent; }
    protected boolean shouldPersist() { return mPreferenceManager != null && isPersistent() && hasKey(); }
    public void setPersistent(boolean persistent) { mPersistent = persistent; }

    protected boolean callChangeListener(Object newValue) {
        return mOnChangeListener == null || mOnChangeListener.onPreferenceChange(this, newValue);
    }

    public void setOnPreferenceChangeListener(OnPreferenceChangeListener l) { mOnChangeListener = l; }
    public OnPreferenceChangeListener getOnPreferenceChangeListener() { return mOnChangeListener; }
    public void setOnPreferenceClickListener(OnPreferenceClickListener l) { mOnClickListener = l; }
    public OnPreferenceClickListener getOnPreferenceClickListener() { return mOnClickListener; }

    /** framework-internal: a click on the row (AOSP's hidden performClick(PreferenceScreen)). */
    void performClick(PreferenceScreen preferenceScreen) {
        if (!isEnabled()) return;
        onClick();
        if (mOnClickListener != null && mOnClickListener.onPreferenceClick(this)) return;
        PreferenceManager preferenceManager = getPreferenceManager();
        if (preferenceManager != null) {
            PreferenceManager.OnPreferenceTreeClickListener listener =
                    preferenceManager.getOnPreferenceTreeClickListener();
            if (preferenceScreen != null && listener != null
                    && listener.onPreferenceTreeClick(preferenceScreen, this)) {
                return;
            }
        }
        if (mIntent != null) getContext().startActivity(mIntent);
    }

    public Context getContext() { return mContext; }

    public SharedPreferences getSharedPreferences() {
        if (mPreferenceManager == null || getPreferenceDataStore() != null) return null;
        return mPreferenceManager.getSharedPreferences();
    }

    public SharedPreferences.Editor getEditor() {
        if (mPreferenceManager == null || getPreferenceDataStore() != null) return null;
        return mPreferenceManager.getEditor();
    }

    public boolean shouldCommit() { return mPreferenceManager != null && mPreferenceManager.shouldCommit(); }

    public int compareTo(Preference another) {
        if (mOrder != another.mOrder) return mOrder - another.mOrder;
        if (mTitle == another.mTitle) return 0;
        if (mTitle == null) return 1;
        if (another.mTitle == null) return -1;
        return mTitle.toString().compareToIgnoreCase(another.mTitle.toString());
    }

    final void setOnPreferenceChangeInternalListener(OnPreferenceChangeInternalListener listener) {
        mListener = listener;
    }

    protected void notifyChanged() {
        if (mListener != null) mListener.onPreferenceChange(this);
    }

    protected void notifyHierarchyChanged() {
        if (mListener != null) mListener.onPreferenceHierarchyChange(this);
    }

    public PreferenceManager getPreferenceManager() { return mPreferenceManager; }

    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        mPreferenceManager = preferenceManager;
        if (!mHasId) {
            mId = preferenceManager.getNextId();
            mHasId = true;
        }
        dispatchSetInitialValue();
    }

    /** framework-internal: attach with a fixed id (PreferenceScreen roots). */
    void onAttachedToHierarchy(PreferenceManager preferenceManager, long id) {
        mId = id;
        mHasId = true;
        try {
            onAttachedToHierarchy(preferenceManager);
        } finally {
            mHasId = false;
        }
    }

    void assignParent(PreferenceGroup parentGroup) { mParentGroup = parentGroup; }

    protected void onAttachedToActivity() { registerDependency(); }

    private void registerDependency() {
        if (TextUtils.isEmpty(mDependencyKey)) return;
        Preference preference = findPreferenceInHierarchy(mDependencyKey);
        if (preference != null) {
            preference.registerDependent(this);
        } else {
            throw new IllegalStateException("Dependency \"" + mDependencyKey + "\" not found for preference \""
                    + mKey + "\" (title: \"" + mTitle + "\"");
        }
    }

    private void unregisterDependency() {
        if (mDependencyKey != null) {
            final Preference oldDependency = findPreferenceInHierarchy(mDependencyKey);
            if (oldDependency != null) oldDependency.unregisterDependent(this);
        }
    }

    protected Preference findPreferenceInHierarchy(String key) {
        if (TextUtils.isEmpty(key) || mPreferenceManager == null) return null;
        return mPreferenceManager.findPreference(key);
    }

    private void registerDependent(Preference dependent) {
        if (mDependents == null) mDependents = new ArrayList<Preference>();
        mDependents.add(dependent);
        dependent.onDependencyChanged(this, shouldDisableDependents());
    }

    private void unregisterDependent(Preference dependent) {
        if (mDependents != null) mDependents.remove(dependent);
    }

    public void notifyDependencyChange(boolean disableDependents) {
        final List<Preference> dependents = mDependents;
        if (dependents == null) return;
        for (int i = 0; i < dependents.size(); i++) dependents.get(i).onDependencyChanged(this, disableDependents);
    }

    public void onDependencyChanged(Preference dependency, boolean disableDependent) {
        if (mDependencyMet == disableDependent) {
            mDependencyMet = !disableDependent;
            notifyDependencyChange(shouldDisableDependents());
            notifyChanged();
        }
    }

    public void onParentChanged(Preference parent, boolean disableChild) {
        if (mParentDependencyMet == disableChild) {
            mParentDependencyMet = !disableChild;
            notifyDependencyChange(shouldDisableDependents());
            notifyChanged();
        }
    }

    public boolean shouldDisableDependents() { return !isEnabled(); }

    public void setDependency(String dependencyKey) {
        unregisterDependency();
        mDependencyKey = dependencyKey;
        registerDependency();
    }

    public String getDependency() { return mDependencyKey; }
    public PreferenceGroup getParent() { return mParentGroup; }
    protected void onPrepareForRemoval() { unregisterDependency(); }
    public void setDefaultValue(Object defaultValue) { mDefaultValue = defaultValue; }

    private void dispatchSetInitialValue() {
        if (getPreferenceDataStore() != null) {
            onSetInitialValue(true, mDefaultValue);
            return;
        }
        final boolean shouldPersist = shouldPersist();
        if (!shouldPersist || !getSharedPreferences().contains(mKey)) {
            if (mDefaultValue != null) onSetInitialValue(false, mDefaultValue);
        } else {
            onSetInitialValue(true, null);
        }
    }

    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {}

    private void tryCommit(SharedPreferences.Editor editor) {
        if (mPreferenceManager.shouldCommit()) editor.apply();
    }

    protected boolean persistString(String value) {
        if (!shouldPersist()) return false;
        if (TextUtils.equals(value, getPersistedString(null))) return true;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) {
            dataStore.putString(mKey, value);
        } else {
            SharedPreferences.Editor editor = mPreferenceManager.getEditor();
            editor.putString(mKey, value);
            tryCommit(editor);
        }
        return true;
    }

    protected String getPersistedString(String defaultReturnValue) {
        if (!shouldPersist()) return defaultReturnValue;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) return dataStore.getString(mKey, defaultReturnValue);
        return mPreferenceManager.getSharedPreferences().getString(mKey, defaultReturnValue);
    }

    public boolean persistStringSet(Set<String> values) {
        if (!shouldPersist()) return false;
        if (values.equals(getPersistedStringSet(null))) return true;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) {
            dataStore.putStringSet(mKey, values);
        } else {
            SharedPreferences.Editor editor = mPreferenceManager.getEditor();
            editor.putStringSet(mKey, values);
            tryCommit(editor);
        }
        return true;
    }

    public Set<String> getPersistedStringSet(Set<String> defaultReturnValue) {
        if (!shouldPersist()) return defaultReturnValue;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) return dataStore.getStringSet(mKey, defaultReturnValue);
        return mPreferenceManager.getSharedPreferences().getStringSet(mKey, defaultReturnValue);
    }

    protected boolean persistInt(int value) {
        if (!shouldPersist()) return false;
        if (value == getPersistedInt(~value)) return true;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) {
            dataStore.putInt(mKey, value);
        } else {
            SharedPreferences.Editor editor = mPreferenceManager.getEditor();
            editor.putInt(mKey, value);
            tryCommit(editor);
        }
        return true;
    }

    protected int getPersistedInt(int defaultReturnValue) {
        if (!shouldPersist()) return defaultReturnValue;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) return dataStore.getInt(mKey, defaultReturnValue);
        return mPreferenceManager.getSharedPreferences().getInt(mKey, defaultReturnValue);
    }

    protected boolean persistFloat(float value) {
        if (!shouldPersist()) return false;
        if (value == getPersistedFloat(Float.NaN)) return true;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) {
            dataStore.putFloat(mKey, value);
        } else {
            SharedPreferences.Editor editor = mPreferenceManager.getEditor();
            editor.putFloat(mKey, value);
            tryCommit(editor);
        }
        return true;
    }

    protected float getPersistedFloat(float defaultReturnValue) {
        if (!shouldPersist()) return defaultReturnValue;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) return dataStore.getFloat(mKey, defaultReturnValue);
        return mPreferenceManager.getSharedPreferences().getFloat(mKey, defaultReturnValue);
    }

    protected boolean persistLong(long value) {
        if (!shouldPersist()) return false;
        if (value == getPersistedLong(~value)) return true;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) {
            dataStore.putLong(mKey, value);
        } else {
            SharedPreferences.Editor editor = mPreferenceManager.getEditor();
            editor.putLong(mKey, value);
            tryCommit(editor);
        }
        return true;
    }

    protected long getPersistedLong(long defaultReturnValue) {
        if (!shouldPersist()) return defaultReturnValue;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) return dataStore.getLong(mKey, defaultReturnValue);
        return mPreferenceManager.getSharedPreferences().getLong(mKey, defaultReturnValue);
    }

    protected boolean persistBoolean(boolean value) {
        if (!shouldPersist()) return false;
        if (value == getPersistedBoolean(!value)) return true;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) {
            dataStore.putBoolean(mKey, value);
        } else {
            SharedPreferences.Editor editor = mPreferenceManager.getEditor();
            editor.putBoolean(mKey, value);
            tryCommit(editor);
        }
        return true;
    }

    protected boolean getPersistedBoolean(boolean defaultReturnValue) {
        if (!shouldPersist()) return defaultReturnValue;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) return dataStore.getBoolean(mKey, defaultReturnValue);
        return mPreferenceManager.getSharedPreferences().getBoolean(mKey, defaultReturnValue);
    }

    boolean canRecycleLayout() { return mRecycleEnabled; }

    @Override
    public String toString() { return getFilterableStringBuilder().toString(); }

    StringBuilder getFilterableStringBuilder() {
        StringBuilder sb = new StringBuilder();
        CharSequence title = getTitle();
        if (!TextUtils.isEmpty(title)) sb.append(title).append(' ');
        CharSequence summary = getSummary();
        if (!TextUtils.isEmpty(summary)) sb.append(summary).append(' ');
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        return sb;
    }

    public void saveHierarchyState(Bundle container) { dispatchSaveInstanceState(container); }

    void dispatchSaveInstanceState(Bundle container) {
        if (hasKey()) {
            mBaseMethodCalled = false;
            Parcelable state = onSaveInstanceState();
            if (!mBaseMethodCalled) {
                throw new IllegalStateException("Derived class did not call super.onSaveInstanceState()");
            }
            if (state != null) container.putParcelable(mKey, state);
        }
    }

    protected Parcelable onSaveInstanceState() {
        mBaseMethodCalled = true;
        return BaseSavedState.EMPTY_STATE;
    }

    public void restoreHierarchyState(Bundle container) { dispatchRestoreInstanceState(container); }

    void dispatchRestoreInstanceState(Bundle container) {
        if (hasKey()) {
            Parcelable state = container.getParcelable(mKey);
            if (state != null) {
                mBaseMethodCalled = false;
                onRestoreInstanceState(state);
                if (!mBaseMethodCalled) {
                    throw new IllegalStateException("Derived class did not call super.onRestoreInstanceState()");
                }
            }
        }
    }

    protected void onRestoreInstanceState(Parcelable state) {
        mBaseMethodCalled = true;
        if (state != BaseSavedState.EMPTY_STATE && state != null) {
            throw new IllegalArgumentException("Wrong state class -- expecting Preference State");
        }
    }

    public static class BaseSavedState extends AbsSavedState {
        public BaseSavedState(Parcel source) { super(source); }
        public BaseSavedState(Parcelable superState) { super(superState); }

        public static final Parcelable.Creator<BaseSavedState> CREATOR = new Parcelable.Creator<BaseSavedState>() {
            public BaseSavedState createFromParcel(Parcel in) { return new BaseSavedState(in); }
            public BaseSavedState[] newArray(int size) { return new BaseSavedState[size]; }
        };
    }
}
