package android.widget;

import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import com.android.internal.util.InternalRes;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.WeakHashMap;

/**
 * framework-internal (hidden in AOSP). Provides the contents of the SearchView suggestions
 * drop-down from a searchable activity's suggestions provider.
 */
class SuggestionsAdapter extends ResourceCursorAdapter implements View.OnClickListener {
    private static final String LOG_TAG = "SuggestionsAdapter";
    private static final int QUERY_LIMIT = 50;

    static final int REFINE_NONE = 0;
    static final int REFINE_BY_ENTRY = 1;
    static final int REFINE_ALL = 2;

    private final SearchManager mSearchManager;
    private final SearchView mSearchView;
    private final SearchableInfo mSearchable;
    private final Context mProviderContext;
    private final WeakHashMap<String, Drawable.ConstantState> mOutsideDrawablesCache;
    private final int mCommitIconResId;

    private boolean mClosed = false;
    private int mQueryRefinement = REFINE_BY_ENTRY;

    // URL color
    private ColorStateList mUrlColor;

    static final int INVALID_INDEX = -1;

    // Cached column indexes, updated when the cursor changes.
    private int mText1Col = INVALID_INDEX;
    private int mText2Col = INVALID_INDEX;
    private int mText2UrlCol = INVALID_INDEX;
    private int mIconName1Col = INVALID_INDEX;
    private int mIconName2Col = INVALID_INDEX;
    private int mFlagsCol = INVALID_INDEX;

    /** Delay before querying after the user deleted text, so fast deletes do not each query. */
    private static final long DELETE_KEY_POST_DELAY = 500L;

    public SuggestionsAdapter(Context context, SearchView searchView, SearchableInfo searchable,
            WeakHashMap<String, Drawable.ConstantState> outsideDrawablesCache) {
        super(context, searchView.getSuggestionRowLayout(), null /* no initial cursor */, true /* auto-requery */);
        mSearchManager = (SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE);
        mSearchView = searchView;
        mSearchable = searchable;
        mCommitIconResId = searchView.getSuggestionCommitIconResId();

        // set up provider resources (gives us icons, etc.)
        final Context activityContext = mSearchable.getActivityContext(mContext);
        mProviderContext = mSearchable.getProviderContext(mContext, activityContext);

        mOutsideDrawablesCache = outsideDrawablesCache;

        getFilter().setDelayer(new Filter.Delayer() {
            private int mPreviousLength = 0;

            public long getPostingDelay(CharSequence constraint) {
                if (constraint == null) return 0;

                long delay = constraint.length() < mPreviousLength ? DELETE_KEY_POST_DELAY : 0;
                mPreviousLength = constraint.length();
                return delay;
            }
        });
    }

    /** Enables query refinement for all suggestions, only those that ask for it, or none. */
    public void setQueryRefinement(int refineWhat) { mQueryRefinement = refineWhat; }

    public int getQueryRefinement() { return mQueryRefinement; }

    /** Overridden to always return false, since we cannot be sure that suggestion sources return stable IDs. */
    @Override
    public boolean hasStableIds() { return false; }

    /** Uses the search suggestions provider to obtain a live cursor (on the filter thread). */
    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        String query = (constraint == null) ? "" : constraint.toString();
        Cursor cursor = null;
        if (mSearchView.getVisibility() != View.VISIBLE || mSearchView.getWindowVisibility() != View.VISIBLE) {
            return null;
        }
        try {
            cursor = mSearchManager.getSuggestions(mSearchable, query, QUERY_LIMIT);
            // trigger fill window so the spinner stays up until the results are copied over and
            // closer to being ready
            if (cursor != null) {
                cursor.getCount();
                return cursor;
            }
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "Search suggestions query threw an exception.", e);
        }
        // If cursor is null or an exception was thrown, return null.
        // changeCursor doesn't get called if cursor is null
        return null;
    }

    public void close() {
        changeCursor(null);
        mClosed = true;
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        updateSpinnerState(getCursor());
    }

    @Override
    public void notifyDataSetInvalidated() {
        super.notifyDataSetInvalidated();
        updateSpinnerState(getCursor());
    }

    private void updateSpinnerState(Cursor cursor) {
        Bundle extras = cursor != null ? cursor.getExtras() : null;
        // Check if the Cursor indicates that the query is not complete and show the spinner
        if (extras != null && extras.getBoolean(SearchManager.CURSOR_EXTRA_KEY_IN_PROGRESS)) {
            return;
        }
    }

    /** Cache columns. */
    @Override
    public void changeCursor(Cursor c) {
        if (mClosed) {
            Log.w(LOG_TAG, "Tried to change cursor after adapter was closed.");
            if (c != null) c.close();
            return;
        }

        try {
            super.changeCursor(c);

            if (c != null) {
                mText1Col = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1);
                mText2Col = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_2);
                mText2UrlCol = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_2_URL);
                mIconName1Col = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_ICON_1);
                mIconName2Col = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_ICON_2);
                mFlagsCol = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_FLAGS);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "error changing cursor and caching columns", e);
        }
    }

    /** Tags the view with cached child view look-ups. */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        final View v = super.newView(context, cursor, parent);
        v.setTag(new ChildViewCache(v));

        // Set up icon.
        final ImageView iconRefine = (ImageView) v.findViewById(InternalRes.viewId("edit_query"));
        iconRefine.setImageResource(mCommitIconResId);

        return v;
    }

    /** Cache of the child views of drop-down list items, to avoid looking up the children each time. */
    private static final class ChildViewCache {
        public final TextView mText1;
        public final TextView mText2;
        public final ImageView mIcon1;
        public final ImageView mIcon2;
        public final ImageView mIconRefine;

        public ChildViewCache(View v) {
            mText1 = (TextView) v.findViewById(android.R.id.text1);
            mText2 = (TextView) v.findViewById(android.R.id.text2);
            mIcon1 = (ImageView) v.findViewById(android.R.id.icon1);
            mIcon2 = (ImageView) v.findViewById(android.R.id.icon2);
            mIconRefine = (ImageView) v.findViewById(InternalRes.viewId("edit_query"));
        }
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ChildViewCache views = (ChildViewCache) view.getTag();

        int flags = 0;
        if (mFlagsCol != INVALID_INDEX) flags = cursor.getInt(mFlagsCol);
        if (views.mText1 != null) {
            String text1 = getStringOrNull(cursor, mText1Col);
            setViewText(views.mText1, text1);
        }
        if (views.mText2 != null) {
            // First check TEXT_2_URL
            CharSequence text2 = getStringOrNull(cursor, mText2UrlCol);
            if (text2 != null) {
                text2 = formatUrl(context, text2);
            } else {
                text2 = getStringOrNull(cursor, mText2Col);
            }

            // If no second line of text is indicated, allow the first line of text
            // to be up to two lines if it wants to be.
            if (TextUtils.isEmpty(text2)) {
                if (views.mText1 != null) {
                    views.mText1.setSingleLine(false);
                    views.mText1.setMaxLines(2);
                }
            } else {
                if (views.mText1 != null) {
                    views.mText1.setSingleLine(true);
                    views.mText1.setMaxLines(1);
                }
            }
            setViewText(views.mText2, text2);
        }

        if (views.mIcon1 != null) setViewDrawable(views.mIcon1, getIcon1(cursor), View.INVISIBLE);
        if (views.mIcon2 != null) setViewDrawable(views.mIcon2, getIcon2(cursor), View.GONE);
        if (mQueryRefinement == REFINE_ALL
                || (mQueryRefinement == REFINE_BY_ENTRY && (flags & SearchManager.FLAG_QUERY_REFINEMENT) != 0)) {
            views.mIconRefine.setVisibility(View.VISIBLE);
            views.mIconRefine.setTag(views.mText1.getText());
            views.mIconRefine.setOnClickListener(this);
        } else {
            views.mIconRefine.setVisibility(View.GONE);
        }
    }

    public void onClick(View v) {
        Object tag = v.getTag();
        if (tag instanceof CharSequence) mSearchView.onQueryRefine((CharSequence) tag);
    }

    private CharSequence formatUrl(Context context, CharSequence url) {
        if (mUrlColor == null) {
            // Lazily get the URL color from the current theme.
            TypedValue colorValue = new TypedValue();
            context.getTheme().resolveAttribute(InternalRes.attr("textColorSearchUrl"), colorValue, true);
            mUrlColor = context.getColorStateList(colorValue.resourceId);
        }

        SpannableString text = new SpannableString(url);
        text.setSpan(new TextAppearanceSpan(null, 0, 0, mUrlColor, null), 0, url.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private void setViewText(TextView v, CharSequence text) {
        // Set the text even if it's null, since we need to clear any previous text.
        v.setText(text);

        if (TextUtils.isEmpty(text)) {
            v.setVisibility(View.GONE);
        } else {
            v.setVisibility(View.VISIBLE);
        }
    }

    private Drawable getIcon1(Cursor cursor) {
        if (mIconName1Col == INVALID_INDEX) return null;
        String value = cursor.getString(mIconName1Col);
        Drawable drawable = getDrawableFromResourceValue(value);
        if (drawable != null) return drawable;
        return getDefaultIcon1(cursor);
    }

    private Drawable getIcon2(Cursor cursor) {
        if (mIconName2Col == INVALID_INDEX) return null;
        String value = cursor.getString(mIconName2Col);
        return getDrawableFromResourceValue(value);
    }

    /** Sets the drawable in an image view, makes sure the view is only visible if there is a drawable. */
    private void setViewDrawable(ImageView v, Drawable drawable, int nullVisibility) {
        // Set the icon even if the drawable is null, since we need to clear any
        // previous icon.
        v.setImageDrawable(drawable);

        if (drawable == null) {
            v.setVisibility(nullVisibility);
        } else {
            v.setVisibility(View.VISIBLE);

            // Animated drawables only start when setVisible goes from false to true.
            drawable.setVisible(false, false);
            drawable.setVisible(true, false);
        }
    }

    /**
     * Gets the text to show in the query field when a suggestion is selected: the
     * suggestion's query column, else its intent data or first line when the searchable
     * asks to rewrite from them.
     */
    @Override
    public CharSequence convertToString(Cursor cursor) {
        if (cursor == null) return null;

        String query = getColumnString(cursor, SearchManager.SUGGEST_COLUMN_QUERY);
        if (query != null) return query;

        if (mSearchable.shouldRewriteQueryFromData()) {
            String data = getColumnString(cursor, SearchManager.SUGGEST_COLUMN_INTENT_DATA);
            if (data != null) return data;
        }

        if (mSearchable.shouldRewriteQueryFromText()) {
            String text1 = getColumnString(cursor, SearchManager.SUGGEST_COLUMN_TEXT_1);
            if (text1 != null) return text1;
        }

        return null;
    }

    /** This method is overridden purely to provide a bit of protection against flaky content providers. */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        try {
            return super.getView(position, convertView, parent);
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "Search suggestions cursor threw exception.", e);
            // Put exception string in item title
            View v = newView(mContext, mCursor, parent);
            if (v != null) {
                ChildViewCache views = (ChildViewCache) v.getTag();
                TextView tv = views.mText1;
                tv.setText(e.toString());
            }
            return v;
        }
    }

    /** This method is overridden purely to provide a bit of protection against flaky content providers. */
    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        try {
            return super.getDropDownView(position, convertView, parent);
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "Search suggestions cursor threw exception.", e);
            // Put exception string in item title
            final Context context = mDropDownContext == null ? mContext : mDropDownContext;
            final View v = newDropDownView(context, mCursor, parent);
            if (v != null) {
                final ChildViewCache views = (ChildViewCache) v.getTag();
                final TextView tv = views.mText1;
                tv.setText(e.toString());
            }
            return v;
        }
    }

    /**
     * Gets a drawable given a value provided by a suggestion provider: a resource id in the
     * provider's package, or an android.resource, content or file URI. Cached by URI.
     */
    private Drawable getDrawableFromResourceValue(String drawableId) {
        if (drawableId == null || drawableId.length() == 0 || "0".equals(drawableId)) return null;
        try {
            // First, see if it's just an integer
            int resourceId = Integer.parseInt(drawableId);
            // It's an int, look for it in the cache
            String drawableUri = ContentResolver.SCHEME_ANDROID_RESOURCE
                    + "://" + mProviderContext.getPackageName() + "/" + resourceId;
            // Must use URI as cache key, since ints are app-specific
            Drawable drawable = checkIconCache(drawableUri);
            if (drawable != null) return drawable;
            // Not cached, find it by resource ID
            drawable = mProviderContext.getDrawable(resourceId);
            // Stick it in the cache, using the URI as key
            storeInIconCache(drawableUri, drawable);
            return drawable;
        } catch (NumberFormatException nfe) {
            // It's not an integer, use it as a URI
            Drawable drawable = checkIconCache(drawableId);
            if (drawable != null) return drawable;
            Uri uri = Uri.parse(drawableId);
            drawable = getDrawable(uri);
            storeInIconCache(drawableId, drawable);
            return drawable;
        } catch (Resources.NotFoundException nfe) {
            // It was an integer, but it couldn't be found, bail out
            Log.w(LOG_TAG, "Icon resource not found: " + drawableId);
            return null;
        }
    }

    /** Gets a drawable by URI, without using the cache. */
    private Drawable getDrawable(Uri uri) {
        try {
            String scheme = uri.getScheme();
            if (ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)) {
                // Load drawables through Resources, to get the source density information
                ContentResolver.OpenResourceIdResult r = mProviderContext.getContentResolver().getResourceId(uri);
                try {
                    return r.r.getDrawable(r.id, mProviderContext.getTheme());
                } catch (Resources.NotFoundException ex) {
                    throw new FileNotFoundException("Resource does not exist: " + uri);
                }
            } else {
                // Let the ContentResolver handle content and file URIs.
                InputStream stream = mProviderContext.getContentResolver().openInputStream(uri);
                if (stream == null) throw new FileNotFoundException("Failed to open " + uri);
                try {
                    return Drawable.createFromStream(stream, null);
                } finally {
                    try {
                        stream.close();
                    } catch (IOException ex) {
                        Log.e(LOG_TAG, "Error closing icon stream for " + uri, ex);
                    }
                }
            }
        } catch (FileNotFoundException fnfe) {
            Log.w(LOG_TAG, "Icon not found: " + uri + ", " + fnfe.getMessage());
            return null;
        }
    }

    private Drawable checkIconCache(String resourceUri) {
        Drawable.ConstantState cached = mOutsideDrawablesCache.get(resourceUri);
        if (cached == null) return null;
        return cached.newDrawable();
    }

    private void storeInIconCache(String resourceUri, Drawable drawable) {
        if (drawable != null) mOutsideDrawablesCache.put(resourceUri, drawable.getConstantState());
    }

    /** Gets the left-hand side icon that will be used for the current suggestion if the suggestion contains none. */
    private Drawable getDefaultIcon1(Cursor cursor) {
        // Check the component that gave us the suggestion
        Drawable drawable = getActivityIconWithCache(mSearchable.getSearchActivity());
        if (drawable != null) return drawable;

        // Fall back to a default icon
        return mContext.getPackageManager().getDefaultActivityIcon();
    }

    /** Gets the activity or application icon for an activity, using the icon cache. */
    private Drawable getActivityIconWithCache(ComponentName component) {
        // First check the icon cache
        String componentIconKey = component.flattenToShortString();
        // Using containsKey() since we also store null values.
        if (mOutsideDrawablesCache.containsKey(componentIconKey)) {
            Drawable.ConstantState cached = mOutsideDrawablesCache.get(componentIconKey);
            return cached == null ? null : cached.newDrawable(mProviderContext.getResources());
        }
        // Then try the activity or application icon
        Drawable drawable = getActivityIcon(component);
        // Stick it in the cache so we don't do this lookup again.
        Drawable.ConstantState toCache = drawable == null ? null : drawable.getConstantState();
        mOutsideDrawablesCache.put(componentIconKey, toCache);
        return drawable;
    }

    /** Gets the activity or application icon for an activity, without the cache. */
    private Drawable getActivityIcon(ComponentName component) {
        PackageManager pm = mContext.getPackageManager();
        final ActivityInfo activityInfo;
        try {
            activityInfo = pm.getActivityInfo(component, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException ex) {
            Log.w(LOG_TAG, ex.toString());
            return null;
        }
        int iconId = activityInfo.getIconResource();
        if (iconId == 0) return null;
        String pkg = component.getPackageName();
        Drawable drawable = pm.getDrawable(pkg, iconId, activityInfo.applicationInfo);
        if (drawable == null) {
            Log.w(LOG_TAG, "Invalid icon resource " + iconId + " for " + component.flattenToShortString());
            return null;
        }
        return drawable;
    }

    /** Gets the value of a string column by name, or null when absent. */
    public static String getColumnString(Cursor cursor, String columnName) {
        int col = cursor.getColumnIndex(columnName);
        return getStringOrNull(cursor, col);
    }

    private static String getStringOrNull(Cursor cursor, int col) {
        if (col == INVALID_INDEX) return null;
        try {
            return cursor.getString(col);
        } catch (Exception e) {
            Log.e(LOG_TAG, "unexpected error retrieving valid column from cursor, did the remote process die?", e);
            return null;
        }
    }
}
