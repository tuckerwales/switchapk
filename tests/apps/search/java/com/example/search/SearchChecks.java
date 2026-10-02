package com.example.search;

import android.app.Activity;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.util.Log;
import android.util.SparseArray;
import android.view.inputmethod.EditorInfo;
import android.widget.CursorAdapter;
import android.widget.SearchView;

/** Logic checks for search. Each logs "SVCHECK ok name" or a failure. */
final class SearchChecks {
    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(MainActivity.TAG, "SVCHECK ok " + name);
        else Log.e(MainActivity.TAG, "SVCHECK FAIL " + name + " " + detail);
    }

    static void run(Activity activity) {
        metaData(activity);
        searchables(activity);
        searchView(activity);
        searchable(activity);
    }

    private static void metaData(Activity activity) {
        PackageManager pm = activity.getPackageManager();
        try {
            ActivityInfo ai = pm.getActivityInfo(activity.getComponentName(), PackageManager.GET_META_DATA);
            Bundle md = ai.metaData;
            check("meta-data string", md != null
                    && ".ResultsActivity".equals(md.getString("android.app.default_searchable")), md);
            check("meta-data int", md != null && md.getInt("count") == 7, md);
            check("meta-data float", md != null && md.getFloat("ratio") == 1.5f, md);
            check("meta-data resource", md != null && md.getInt("layout") == R.layout.main, md);
            Bundle app = ai.applicationInfo.metaData;
            check("application meta-data", app != null && app.getBoolean("app.flag"), app);
        } catch (PackageManager.NameNotFoundException e) {
            check("meta-data", false, e);
        }
    }

    private static void searchables(Activity activity) {
        SearchManager sm = (SearchManager) activity.getSystemService(Activity.SEARCH_SERVICE);
        check("search manager", sm != null && sm == activity.getSystemService(SearchManager.class), sm);
        ComponentName results = new ComponentName(activity, ResultsActivity.class);
        SearchableInfo info = sm.getSearchableInfo(results);
        check("searchable info", info != null && results.equals(info.getSearchActivity()), info);
        if (info == null) return;
        check("suggest authority", "com.example.search.suggest".equals(info.getSuggestAuthority())
                && "com.example.search".equals(info.getSuggestPackage()), info.getSuggestAuthority());
        check("suggest selection", " ?".equals(info.getSuggestSelection()) && info.getSuggestPath() == null
                && info.getSuggestThreshold() == 1, info.getSuggestSelection());
        check("hint id", info.getHintId() == R.string.search_hint, info.getHintId());
        check("rewrite from text", info.shouldRewriteQueryFromText() && !info.shouldRewriteQueryFromData(), null);
        check("ime options", info.getImeOptions() == EditorInfo.IME_ACTION_SEARCH, info.getImeOptions());
        check("input type", info.getInputType() == InputType.TYPE_CLASS_TEXT, info.getInputType());
        check("voice mode", info.getVoiceSearchEnabled() && info.getVoiceSearchLaunchRecognizer()
                && !info.getVoiceSearchLaunchWebSearch(), null);
        check("default searchable", info == sm.getSearchableInfo(activity.getComponentName()), null);
        check("unknown activity", sm.getSearchableInfo(new ComponentName(activity, "com.example.search.Nope")) == null,
                null);
        check("no global search", sm.getGlobalSearchActivity() == null
                && sm.getSearchablesInGlobalSearch().isEmpty(), null);
        check("no recognizer", RecognizerIntent.getVoiceDetailsIntent(activity) == null
                && activity.getPackageManager().resolveActivity(
                        new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0) == null, null);
    }

    private static void searchView(Activity activity) {
        SearchView v = new SearchView(activity);
        final StringBuilder events = new StringBuilder();
        v.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            public boolean onQueryTextSubmit(String query) {
                events.append("submit:").append(query).append(' ');
                return true;
            }

            public boolean onQueryTextChange(String newText) {
                events.append("change:").append(newText).append(' ');
                return false;
            }
        });
        final int[] closes = new int[1];
        v.setOnCloseListener(() -> {
            closes[0]++;
            return false;
        });
        check("iconified by default", v.isIconified() && v.isIconifiedByDefault() && v.isIconfiedByDefault(), null);
        check("no default hint", v.getQueryHint() == null, v.getQueryHint());
        v.setQueryHint("Find");
        check("query hint", "Find".equals(v.getQueryHint().toString()), v.getQueryHint());
        v.setIconified(false);
        check("expanded", !v.isIconified(), null);
        v.setQuery("abc", false);
        check("set query", "abc".equals(v.getQuery().toString()) && "change:abc ".equals(events.toString()),
                events);
        v.setQuery("abc", true);
        check("submit", events.toString().equals("change:abc submit:abc "), events);
        v.setQuery("", false);
        v.setIconified(true);
        check("close collapses", v.isIconified() && closes[0] == 1, closes[0]);
        v.setOnCloseListener(() -> true);
        v.setIconified(false);
        v.setIconified(true);
        check("close listener keeps it open", !v.isIconified(), null);
        check("submit button", !v.isSubmitButtonEnabled(), null);
        v.setSubmitButtonEnabled(true);
        check("submit button enabled", v.isSubmitButtonEnabled(), null);
        v.setMaxWidth(300);
        check("max width", v.getMaxWidth() == 300, v.getMaxWidth());
        v.setImeOptions(EditorInfo.IME_ACTION_GO);
        check("ime options round trip", v.getImeOptions() == EditorInfo.IME_ACTION_GO, v.getImeOptions());
        v.setInputType(InputType.TYPE_CLASS_NUMBER);
        check("input type round trip", v.getInputType() == InputType.TYPE_CLASS_NUMBER, v.getInputType());
        check("class name", "android.widget.SearchView".equals(v.getAccessibilityClassName().toString()), null);
        check("no suggestions adapter", v.getSuggestionsAdapter() == null, null);

        v.setIconified(true);
        SparseArray<Parcelable> state = new SparseArray<Parcelable>();
        v.setId(42);
        v.saveHierarchyState(state);
        SearchView w = new SearchView(activity);
        w.setId(42);
        w.setIconified(false);
        w.restoreHierarchyState(state);
        check("state restores expanded", !w.isIconified(), null);

        SearchView a = new SearchView(activity);
        a.onActionViewExpanded();
        check("action view expanded", !a.isIconified()
                && (a.getImeOptions() & EditorInfo.IME_FLAG_NO_FULLSCREEN) != 0, a.getImeOptions());
        a.setQuery("x", false);
        a.onActionViewCollapsed();
        check("action view collapsed", a.isIconified() && a.getQuery().length() == 0
                && (a.getImeOptions() & EditorInfo.IME_FLAG_NO_FULLSCREEN) == 0, a.getImeOptions());
    }

    private static void searchable(Activity activity) {
        SearchManager sm = (SearchManager) activity.getSystemService(Activity.SEARCH_SERVICE);
        SearchView v = new SearchView(activity);
        v.setSearchableInfo(sm.getSearchableInfo(activity.getComponentName()));
        CursorAdapter adapter = v.getSuggestionsAdapter();
        check("suggestions adapter", adapter != null, null);
        check("searchable hint", "Search fruit".equals(String.valueOf(v.getQueryHint())), v.getQueryHint());
        check("searchable input type", v.getInputType() == (InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS), v.getInputType());
        check("searchable ime options", v.getImeOptions() == EditorInfo.IME_ACTION_SEARCH, v.getImeOptions());
        v.setQueryRefinementEnabled(true);
        check("query refinement", v.isQueryRefinementEnabled(), null);
    }
}
