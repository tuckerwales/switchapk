package android.preference;

import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Port of AOSP's PreferenceGroupAdapter: the rows of a PreferenceGroup flattened (groups shown on the same screen as
 * their children are expanded; nested PreferenceScreens are single rows), recycling rows by layout.
 */
class PreferenceGroupAdapter extends BaseAdapter implements Preference.OnPreferenceChangeInternalListener {
    private final PreferenceGroup mPreferenceGroup;
    private List<Preference> mPreferenceList;
    private final ArrayList<PreferenceLayout> mPreferenceLayouts = new ArrayList<PreferenceLayout>();
    private final PreferenceLayout mTempPreferenceLayout = new PreferenceLayout();
    private boolean mHasReturnedViewTypeCount = false;
    private volatile boolean mIsSyncing = false;
    private final Handler mHandler = new Handler();

    private final Runnable mSyncRunnable = new Runnable() {
        public void run() { syncMyPreferences(); }
    };

    private static final class PreferenceLayout implements Comparable<PreferenceLayout> {
        int resId;
        int widgetResId;
        String name;

        public int compareTo(PreferenceLayout other) {
            int compareNames = name.compareTo(other.name);
            if (compareNames != 0) return compareNames;
            if (resId != other.resId) return resId - other.resId;
            return widgetResId - other.widgetResId;
        }
    }

    PreferenceGroupAdapter(PreferenceGroup preferenceGroup) {
        mPreferenceGroup = preferenceGroup;
        mPreferenceGroup.setOnPreferenceChangeInternalListener(this);
        mPreferenceList = new ArrayList<Preference>();
        syncMyPreferences();
    }

    private void syncMyPreferences() {
        synchronized (this) {
            if (mIsSyncing) return;
            mIsSyncing = true;
        }
        List<Preference> newPreferenceList = new ArrayList<Preference>(mPreferenceList.size());
        flattenPreferenceGroup(newPreferenceList, mPreferenceGroup);
        mPreferenceList = newPreferenceList;
        notifyDataSetChanged();
        synchronized (this) {
            mIsSyncing = false;
            notifyAll();
        }
    }

    private void flattenPreferenceGroup(List<Preference> preferences, PreferenceGroup group) {
        group.sortPreferences();
        final int groupSize = group.getPreferenceCount();
        for (int i = 0; i < groupSize; i++) {
            final Preference preference = group.getPreference(i);
            preferences.add(preference);
            if (!mHasReturnedViewTypeCount && preference.canRecycleLayout()) addPreferenceClassName(preference);
            if (preference instanceof PreferenceGroup) {
                final PreferenceGroup preferenceAsGroup = (PreferenceGroup) preference;
                if (preferenceAsGroup.isOnSameScreenAsChildren()) flattenPreferenceGroup(preferences, preferenceAsGroup);
            }
            preference.setOnPreferenceChangeInternalListener(this);
        }
    }

    private PreferenceLayout createPreferenceLayout(Preference preference, PreferenceLayout in) {
        PreferenceLayout pl = in != null ? in : new PreferenceLayout();
        pl.name = preference.getClass().getName();
        pl.resId = preference.getLayoutResource();
        pl.widgetResId = preference.getWidgetLayoutResource();
        return pl;
    }

    private void addPreferenceClassName(Preference preference) {
        final PreferenceLayout pl = createPreferenceLayout(preference, null);
        int insertPos = Collections.binarySearch(mPreferenceLayouts, pl);
        if (insertPos < 0) {
            insertPos = insertPos * -1 - 1;
            mPreferenceLayouts.add(insertPos, pl);
        }
    }

    public int getCount() { return mPreferenceList.size(); }

    public Preference getItem(int position) {
        if (position < 0 || position >= getCount()) return null;
        return mPreferenceList.get(position);
    }

    public long getItemId(int position) {
        if (position < 0 || position >= getCount()) return android.widget.AdapterView.INVALID_ROW_ID;
        return getItem(position).getId();
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        final Preference preference = getItem(position);
        mTempPreferenceLayout.name = "";
        createPreferenceLayout(preference, mTempPreferenceLayout);
        if (Collections.binarySearch(mPreferenceLayouts, mTempPreferenceLayout) < 0
                || getItemViewType(position) == getHighlightItemViewType()) {
            convertView = null;
        }
        return preference.getView(convertView, parent);
    }

    private int getHighlightItemViewType() { return getViewTypeCount() - 1; }

    @Override
    public boolean isEnabled(int position) {
        if (position < 0 || position >= getCount()) return true;
        return getItem(position).isSelectable();
    }

    @Override
    public boolean areAllItemsEnabled() { return false; }

    public void onPreferenceChange(Preference preference) { notifyDataSetChanged(); }

    public void onPreferenceHierarchyChange(Preference preference) {
        mHandler.removeCallbacks(mSyncRunnable);
        mHandler.post(mSyncRunnable);
    }

    @Override
    public boolean hasStableIds() { return true; }

    @Override
    public int getItemViewType(int position) {
        if (!mHasReturnedViewTypeCount) mHasReturnedViewTypeCount = true;
        final Preference preference = getItem(position);
        if (!preference.canRecycleLayout()) return Adapter.IGNORE_ITEM_VIEW_TYPE;
        mTempPreferenceLayout.name = "";
        createPreferenceLayout(preference, mTempPreferenceLayout);
        int viewType = Collections.binarySearch(mPreferenceLayouts, mTempPreferenceLayout);
        return viewType < 0 ? Adapter.IGNORE_ITEM_VIEW_TYPE : viewType;
    }

    @Override
    public int getViewTypeCount() {
        if (!mHasReturnedViewTypeCount) mHasReturnedViewTypeCount = true;
        return Math.max(1, mPreferenceLayouts.size()) + 1;
    }
}
