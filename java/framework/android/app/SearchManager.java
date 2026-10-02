package android.app;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Access to the search facilities: the in-app search dialog and searchable activity
 * meta-data. There is one app on the device, so the searchables are the app's own
 * activities that handle ACTION_SEARCH and carry "android.app.searchable" meta-data;
 * global and web search are not available.
 */
public class SearchManager implements DialogInterface.OnDismissListener, DialogInterface.OnCancelListener {
    private static final String TAG = "SearchManager";

    public static final char MENU_KEY = 's';
    public static final int MENU_KEYCODE = 47;

    public static final String QUERY = "query";
    public static final String USER_QUERY = "user_query";
    public static final String APP_DATA = "app_data";
    /** framework-internal (hidden in AOSP). */
    public static final String SEARCH_MODE = "search_mode";
    public static final String ACTION_KEY = "action_key";
    public static final String EXTRA_DATA_KEY = "intent_extra_data_key";
    public static final String EXTRA_SELECT_QUERY = "select_query";
    public static final String EXTRA_NEW_SEARCH = "new_search";
    public static final String EXTRA_WEB_SEARCH_PENDINGINTENT = "web_search_pendingintent";
    public static final String ACTION_MSG = "action_msg";
    public static final int FLAG_QUERY_REFINEMENT = 1 << 0;
    public static final String CURSOR_EXTRA_KEY_IN_PROGRESS = "in_progress";

    public static final String SUGGEST_URI_PATH_QUERY = "search_suggest_query";
    public static final String SUGGEST_MIME_TYPE = "vnd.android.cursor.dir/vnd.android.search.suggest";
    public static final String SUGGEST_URI_PATH_SHORTCUT = "search_suggest_shortcut";
    public static final String SHORTCUT_MIME_TYPE = "vnd.android.cursor.item/vnd.android.search.suggest";

    public static final String SUGGEST_COLUMN_FORMAT = "suggest_format";
    public static final String SUGGEST_COLUMN_TEXT_1 = "suggest_text_1";
    public static final String SUGGEST_COLUMN_TEXT_2 = "suggest_text_2";
    public static final String SUGGEST_COLUMN_TEXT_2_URL = "suggest_text_2_url";
    public static final String SUGGEST_COLUMN_ICON_1 = "suggest_icon_1";
    public static final String SUGGEST_COLUMN_ICON_2 = "suggest_icon_2";
    public static final String SUGGEST_COLUMN_RESULT_CARD_IMAGE = "suggest_result_card_image";
    public static final String SUGGEST_COLUMN_INTENT_ACTION = "suggest_intent_action";
    public static final String SUGGEST_COLUMN_INTENT_DATA = "suggest_intent_data";
    public static final String SUGGEST_COLUMN_INTENT_EXTRA_DATA = "suggest_intent_extra_data";
    public static final String SUGGEST_COLUMN_INTENT_DATA_ID = "suggest_intent_data_id";
    public static final String SUGGEST_COLUMN_QUERY = "suggest_intent_query";
    public static final String SUGGEST_COLUMN_SHORTCUT_ID = "suggest_shortcut_id";
    public static final String SUGGEST_COLUMN_SPINNER_WHILE_REFRESHING = "suggest_spinner_while_refreshing";
    public static final String SUGGEST_COLUMN_CONTENT_TYPE = "suggest_content_type";
    public static final String SUGGEST_COLUMN_IS_LIVE = "suggest_is_live";
    public static final String SUGGEST_COLUMN_VIDEO_WIDTH = "suggest_video_width";
    public static final String SUGGEST_COLUMN_VIDEO_HEIGHT = "suggest_video_height";
    public static final String SUGGEST_COLUMN_AUDIO_CHANNEL_CONFIG = "suggest_audio_channel_config";
    public static final String SUGGEST_COLUMN_PURCHASE_PRICE = "suggest_purchase_price";
    public static final String SUGGEST_COLUMN_RENTAL_PRICE = "suggest_rental_price";
    public static final String SUGGEST_COLUMN_RATING_STYLE = "suggest_rating_style";
    public static final String SUGGEST_COLUMN_RATING_SCORE = "suggest_rating_score";
    public static final String SUGGEST_COLUMN_PRODUCTION_YEAR = "suggest_production_year";
    public static final String SUGGEST_COLUMN_DURATION = "suggest_duration";
    public static final String SUGGEST_COLUMN_FLAGS = "suggest_flags";
    public static final String SUGGEST_COLUMN_LAST_ACCESS_HINT = "suggest_last_access_hint";
    public static final String SUGGEST_NEVER_MAKE_SHORTCUT = "_-1";
    public static final String SUGGEST_PARAMETER_LIMIT = "limit";

    public static final String INTENT_ACTION_GLOBAL_SEARCH = "android.search.action.GLOBAL_SEARCH";
    public static final String INTENT_ACTION_SEARCH_SETTINGS = "android.search.action.SEARCH_SETTINGS";
    public static final String INTENT_ACTION_WEB_SEARCH_SETTINGS = "android.search.action.WEB_SEARCH_SETTINGS";
    public static final String INTENT_ACTION_SEARCHABLES_CHANGED = "android.search.action.SEARCHABLES_CHANGED";
    public static final String INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED =
            "android.search.action.GLOBAL_SEARCH_ACTIVITY_CHANGED";
    public static final String INTENT_ACTION_SEARCH_SETTINGS_CHANGED = "android.search.action.SETTINGS_CHANGED";

    private static final String MD_LABEL_DEFAULT_SEARCHABLE = "android.app.default_searchable";
    private static final String MD_SEARCHABLE_SYSTEM_SEARCH = "*";

    /** Searchable activities of the app, keyed by component; built on first use. */
    private static HashMap<ComponentName, SearchableInfo> sSearchablesMap;
    private static ArrayList<SearchableInfo> sSearchablesList;

    private final Context mContext;

    /** The search dialog, created on first use. */
    private SearchDialog mSearchDialog;

    OnDismissListener mDismissListener = null;
    OnCancelListener mCancelListener = null;

    /** See {@link #setOnDismissListener} for configuring your activity to monitor search UI state. */
    public interface OnDismissListener {
        /** This method will be called when the search UI is dismissed. */
        public void onDismiss();
    }

    /** See {@link #setOnCancelListener} for configuring your activity to monitor search UI state. */
    public interface OnCancelListener {
        /** This method will be called when the search UI is canceled. */
        public void onCancel();
    }

    /** framework-internal (hidden in AOSP). */
    SearchManager(Context context, Handler handler) {
        mContext = context;
    }

    /**
     * Launch search UI: the search dialog for the given (or a default) searchable activity.
     * Global search is not available, so a global request does nothing.
     */
    public void startSearch(String initialQuery, boolean selectInitialQuery, ComponentName launchActivity,
            Bundle appSearchData, boolean globalSearch) {
        startSearch(initialQuery, selectInitialQuery, launchActivity, appSearchData, globalSearch, null);
    }

    /** framework-internal (hidden in AOSP). As above, with the bounds of the view that started it. */
    public void startSearch(String initialQuery, boolean selectInitialQuery, ComponentName launchActivity,
            Bundle appSearchData, boolean globalSearch, Rect sourceBounds) {
        if (globalSearch) {
            startGlobalSearch(initialQuery, selectInitialQuery, appSearchData, sourceBounds);
            return;
        }

        ensureSearchDialog();

        mSearchDialog.show(initialQuery, selectInitialQuery, launchActivity, appSearchData);
    }

    private void ensureSearchDialog() {
        if (mSearchDialog == null) {
            mSearchDialog = new SearchDialog(mContext, this);
            mSearchDialog.setOnCancelListener(this);
            mSearchDialog.setOnDismissListener(this);
        }
    }

    /** Starts the global search activity; there is none on the Switch. */
    void startGlobalSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData, Rect sourceBounds) {
        Log.w(TAG, "No global search activity found.");
    }

    /** Gets the name of the global search activity; there is none on the Switch. */
    public ComponentName getGlobalSearchActivity() { return null; }

    /** framework-internal (hidden in AOSP). Gets the name of the web search activity; there is none. */
    public ComponentName getWebSearchActivity() { return null; }

    /**
     * Similar to {@link #startSearch} but actually fires off the search query after invoking
     * the search dialog.
     */
    public void triggerSearch(String query, ComponentName launchActivity, Bundle appSearchData) {
        if (query == null || TextUtils.getTrimmedLength(query) == 0) {
            Log.w(TAG, "triggerSearch called with empty query, ignoring.");
            return;
        }
        startSearch(query, false, launchActivity, appSearchData, false);
        mSearchDialog.launchQuerySearch();
    }

    /** Terminate search UI. */
    public void stopSearch() {
        if (mSearchDialog != null) mSearchDialog.cancel();
    }

    /** framework-internal (hidden in AOSP). Whether the search UI is visible. */
    public boolean isVisible() { return mSearchDialog == null ? false : mSearchDialog.isShowing(); }

    public void setOnDismissListener(final OnDismissListener listener) { mDismissListener = listener; }

    public void setOnCancelListener(OnCancelListener listener) { mCancelListener = listener; }

    /** @deprecated This method is an obsolete internal implementation detail. Do not use. */
    @Deprecated
    public void onCancel(DialogInterface dialog) {
        if (mCancelListener != null) mCancelListener.onCancel();
    }

    /** @deprecated This method is an obsolete internal implementation detail. Do not use. */
    @Deprecated
    public void onDismiss(DialogInterface dialog) {
        if (mDismissListener != null) mDismissListener.onDismiss();
    }

    /**
     * Gets information about a searchable activity: the activity itself if it is searchable,
     * else the one named by its (or the application's) "android.app.default_searchable"
     * meta-data. Null when there is none.
     */
    public SearchableInfo getSearchableInfo(ComponentName componentName) {
        if (componentName == null) return null;
        HashMap<ComponentName, SearchableInfo> map = searchables(mContext);

        // Step 1. Is the activity searchable itself?
        SearchableInfo result = map.get(componentName);
        if (result != null) return result;

        // Step 2. See if the current activity references a searchable.
        PackageManager pm = mContext.getPackageManager();
        ActivityInfo ai;
        try {
            ai = pm.getActivityInfo(componentName, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error getting activity info " + e);
            return null;
        }
        String refActivityName = null;
        Bundle md = ai.metaData;
        if (md != null) refActivityName = md.getString(MD_LABEL_DEFAULT_SEARCHABLE);
        // Step 3. None found, try the application.
        if (refActivityName == null && ai.applicationInfo != null) {
            md = ai.applicationInfo.metaData;
            if (md != null) refActivityName = md.getString(MD_LABEL_DEFAULT_SEARCHABLE);
        }

        if (refActivityName != null) {
            // This value is deprecated, return null
            if (refActivityName.equals(MD_SEARCHABLE_SYSTEM_SEARCH)) return null;
            String pkg = componentName.getPackageName();
            ComponentName referredActivity;
            if (refActivityName.charAt(0) == '.') {
                referredActivity = new ComponentName(pkg, pkg + refActivityName);
            } else {
                referredActivity = new ComponentName(pkg, refActivityName);
            }
            return map.get(referredActivity);
        }
        return null;
    }

    /** Returns a list of the searchable activities that can be included in global search. */
    public List<SearchableInfo> getSearchablesInGlobalSearch() {
        searchables(mContext);
        ArrayList<SearchableInfo> out = new ArrayList<SearchableInfo>();
        for (int i = 0; i < sSearchablesList.size(); i++) {
            SearchableInfo info = sSearchablesList.get(i);
            if (info.shouldIncludeInGlobalSearch()) out.add(info);
        }
        return out;
    }

    /** Activities handling ACTION_SEARCH that carry searchable meta-data, read once. */
    private static synchronized HashMap<ComponentName, SearchableInfo> searchables(Context context) {
        if (sSearchablesMap != null) return sSearchablesMap;
        HashMap<ComponentName, SearchableInfo> map = new HashMap<ComponentName, SearchableInfo>();
        ArrayList<SearchableInfo> list = new ArrayList<SearchableInfo>();
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> infos = pm.queryIntentActivities(new Intent(Intent.ACTION_SEARCH),
                PackageManager.GET_META_DATA);
        if (infos != null) {
            for (int i = 0; i < infos.size(); i++) {
                ActivityInfo ai = infos.get(i).activityInfo;
                if (ai == null) continue;
                // Skip activities without searchable meta-data before parsing anything.
                if (ai.metaData == null || ai.metaData.getInt("android.app.searchable") == 0) continue;
                SearchableInfo searchable = SearchableInfo.getActivityMetaData(context, ai, 0);
                if (searchable != null) {
                    list.add(searchable);
                    map.put(searchable.getSearchActivity(), searchable);
                }
            }
        }
        sSearchablesList = list;
        sSearchablesMap = map;
        return map;
    }

    /** framework-internal (hidden in AOSP). Gets a cursor with search suggestions. */
    public Cursor getSuggestions(SearchableInfo searchable, String query) {
        return getSuggestions(searchable, query, -1);
    }

    /**
     * framework-internal (hidden in AOSP). Gets a cursor with search suggestions from the
     * searchable's suggestions provider: content://authority[/path]/search_suggest_query,
     * with the query in the selection arguments or appended to the path.
     */
    public Cursor getSuggestions(SearchableInfo searchable, String query, int limit) {
        if (searchable == null) return null;

        String authority = searchable.getSuggestAuthority();
        if (authority == null) return null;

        Uri.Builder uriBuilder = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority);

        // if content path provided, insert it now
        final String contentPath = searchable.getSuggestPath();
        if (contentPath != null) uriBuilder.appendEncodedPath(contentPath);

        // append standard suggestion query path
        uriBuilder.appendPath(SearchManager.SUGGEST_URI_PATH_QUERY);

        // get the query selection, may be null
        String selection = searchable.getSuggestSelection();
        // inject query, either as selection args or inline
        String[] selArgs = null;
        if (selection != null) { // use selection if provided
            selArgs = new String[] {query};
        } else { // no selection, use REST pattern
            uriBuilder.appendPath(query);
        }

        if (limit > 0) uriBuilder.appendQueryParameter(SUGGEST_PARAMETER_LIMIT, String.valueOf(limit));

        Uri uri = uriBuilder.build();

        // finally, make the query
        return mContext.getContentResolver().query(uri, null, selection, selArgs, null);
    }
}
