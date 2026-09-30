package android.content;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public class IntentFilter implements Parcelable {
    public static final int SYSTEM_HIGH_PRIORITY = 1000;
    public static final int SYSTEM_LOW_PRIORITY = -1000;
    public static final int MATCH_CATEGORY_MASK = 0xfff0000;
    public static final int MATCH_ADJUSTMENT_MASK = 0x000ffff;
    public static final int MATCH_ADJUSTMENT_NORMAL = 0x8000;
    public static final int MATCH_CATEGORY_EMPTY = 0x0100000;
    public static final int MATCH_CATEGORY_SCHEME = 0x0200000;
    public static final int MATCH_CATEGORY_HOST = 0x0300000;
    public static final int MATCH_CATEGORY_PORT = 0x0400000;
    public static final int MATCH_CATEGORY_PATH = 0x0500000;
    public static final int MATCH_CATEGORY_SCHEME_SPECIFIC_PART = 0x0580000;
    public static final int MATCH_CATEGORY_TYPE = 0x0600000;
    public static final int NO_MATCH_TYPE = -1;
    public static final int NO_MATCH_DATA = -2;
    public static final int NO_MATCH_ACTION = -3;
    public static final int NO_MATCH_CATEGORY = -4;

    private int mPriority;
    private final ArrayList<String> mActions = new ArrayList<String>();
    private ArrayList<String> mCategories;
    private ArrayList<String> mDataSchemes;
    private ArrayList<String> mDataTypes;
    private ArrayList<String> mDataHosts;
    private ArrayList<String> mDataPaths;

    public static class MalformedMimeTypeException extends android.util.AndroidException {
        public MalformedMimeTypeException() {}
        public MalformedMimeTypeException(String name) { super(name); }
    }

    public static IntentFilter create(String action, String dataType) {
        try {
            return new IntentFilter(action, dataType);
        } catch (MalformedMimeTypeException e) {
            throw new RuntimeException("Bad MIME type", e);
        }
    }

    public IntentFilter() {}
    public IntentFilter(String action) { addAction(action); }

    public IntentFilter(String action, String dataType) throws MalformedMimeTypeException {
        addAction(action);
        addDataType(dataType);
    }

    public IntentFilter(IntentFilter o) {
        mPriority = o.mPriority;
        mActions.addAll(o.mActions);
        if (o.mCategories != null) mCategories = new ArrayList<String>(o.mCategories);
        if (o.mDataSchemes != null) mDataSchemes = new ArrayList<String>(o.mDataSchemes);
        if (o.mDataTypes != null) mDataTypes = new ArrayList<String>(o.mDataTypes);
        if (o.mDataHosts != null) mDataHosts = new ArrayList<String>(o.mDataHosts);
        if (o.mDataPaths != null) mDataPaths = new ArrayList<String>(o.mDataPaths);
    }

    public final void setPriority(int priority) { mPriority = priority; }
    public final int getPriority() { return mPriority; }
    public final void addAction(String action) { if (!mActions.contains(action)) mActions.add(action.intern()); }
    public final int countActions() { return mActions.size(); }
    public final String getAction(int index) { return mActions.get(index); }
    public final boolean hasAction(String action) { return action != null && mActions.contains(action); }
    public final boolean matchAction(String action) { return hasAction(action); }
    public final Iterator<String> actionsIterator() { return mActions.iterator(); }

    public final void addDataType(String type) throws MalformedMimeTypeException {
        if (mDataTypes == null) mDataTypes = new ArrayList<String>();
        if (type.indexOf('/') < 0) throw new MalformedMimeTypeException(type);
        mDataTypes.add(type);
    }

    public final boolean hasDataType(String type) { return mDataTypes != null && mDataTypes.contains(type); }
    public final int countDataTypes() { return mDataTypes != null ? mDataTypes.size() : 0; }
    public final String getDataType(int index) { return mDataTypes.get(index); }

    public final void addDataScheme(String scheme) {
        if (mDataSchemes == null) mDataSchemes = new ArrayList<String>();
        if (!mDataSchemes.contains(scheme)) mDataSchemes.add(scheme.intern());
    }

    public final int countDataSchemes() { return mDataSchemes != null ? mDataSchemes.size() : 0; }
    public final String getDataScheme(int index) { return mDataSchemes.get(index); }
    public final boolean hasDataScheme(String scheme) { return mDataSchemes != null && mDataSchemes.contains(scheme); }

    public final void addDataAuthority(String host, String port) {
        if (mDataHosts == null) mDataHosts = new ArrayList<String>();
        mDataHosts.add(host);
    }

    public final void addDataPath(String path, int type) {
        if (mDataPaths == null) mDataPaths = new ArrayList<String>();
        mDataPaths.add(path);
    }

    public final void addCategory(String category) {
        if (mCategories == null) mCategories = new ArrayList<String>();
        if (!mCategories.contains(category)) mCategories.add(category.intern());
    }

    public final int countCategories() { return mCategories != null ? mCategories.size() : 0; }
    public final String getCategory(int index) { return mCategories.get(index); }
    public final boolean hasCategory(String category) { return mCategories != null && mCategories.contains(category); }
    public final Iterator<String> categoriesIterator() { return mCategories != null ? mCategories.iterator() : null; }

    public final String matchCategories(Set<String> categories) {
        if (categories == null) return null;
        for (String c : categories) if (mCategories == null || !mCategories.contains(c)) return c;
        return null;
    }

    public final int matchData(String type, String scheme, Uri data) {
        if (mDataTypes == null && mDataSchemes == null) return (type == null && data == null) ? (MATCH_CATEGORY_EMPTY + MATCH_ADJUSTMENT_NORMAL) : NO_MATCH_DATA;
        int match = MATCH_CATEGORY_EMPTY;
        if (mDataSchemes != null) {
            if (scheme == null || !mDataSchemes.contains(scheme)) return NO_MATCH_DATA;
            match = MATCH_CATEGORY_SCHEME;
            if (mDataHosts != null) {
                String host = data != null ? data.getHost() : null;
                if (host == null || !mDataHosts.contains(host)) return NO_MATCH_DATA;
                match = MATCH_CATEGORY_HOST;
            }
        }
        if (mDataTypes != null) {
            if (type == null) return NO_MATCH_TYPE;
            boolean ok = false;
            for (String t : mDataTypes) {
                if (t.equals(type) || t.equals("*/*") || (t.endsWith("/*") && type.startsWith(t.substring(0, t.length() - 1)))) ok = true;
            }
            if (!ok) return NO_MATCH_TYPE;
            match = MATCH_CATEGORY_TYPE;
        }
        return match + MATCH_ADJUSTMENT_NORMAL;
    }

    public final int match(String action, String type, String scheme, Uri data, Set<String> categories, String logTag) {
        if (action != null && !matchAction(action)) return NO_MATCH_ACTION;
        int dataMatch = matchData(type, scheme, data);
        if (dataMatch < 0) return dataMatch;
        String categoryMismatch = matchCategories(categories);
        if (categoryMismatch != null) return NO_MATCH_CATEGORY;
        return dataMatch;
    }

    public final int match(ContentResolver resolver, Intent intent, boolean resolve, String logTag) {
        return match(intent.getAction(), intent.getType(), intent.getScheme(), intent.getData(), intent.getCategories(), logTag);
    }

    public int describeContents() { return 0; }
    public final void writeToParcel(Parcel dest, int flags) { dest.writeValue(new IntentFilter(this)); }

    public static final Parcelable.Creator<IntentFilter> CREATOR = new Parcelable.Creator<IntentFilter>() {
        public IntentFilter createFromParcel(Parcel source) { return (IntentFilter) source.readValue(null); }
        public IntentFilter[] newArray(int size) { return new IntentFilter[size]; }
    };
}
