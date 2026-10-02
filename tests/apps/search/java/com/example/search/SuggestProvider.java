package com.example.search;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;
import java.util.Locale;

/** Search suggestions: fruit whose name starts with the query. */
public class SuggestProvider extends ContentProvider {
    static final String[] FRUIT = {
        "Apple", "Apricot", "Avocado", "Banana", "Blackberry", "Blueberry", "Cherry", "Coconut",
        "Grape", "Kiwi", "Lemon", "Lime", "Mango", "Melon", "Orange", "Papaya", "Peach", "Pear", "Plum",
    };

    @Override
    public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String query = selectionArgs != null && selectionArgs.length > 0 ? selectionArgs[0] : "";
        String limit = uri.getQueryParameter(SearchManager.SUGGEST_PARAMETER_LIMIT);
        Log.i(MainActivity.TAG, "suggest " + uri.getLastPathSegment() + " [" + query + "] limit " + limit);
        MatrixCursor c = new MatrixCursor(new String[] {
            BaseColumns._ID, SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_QUERY, SearchManager.SUGGEST_COLUMN_FLAGS,
        });
        String q = query.toLowerCase(Locale.US);
        for (int i = 0; i < FRUIT.length; i++) {
            if (!FRUIT[i].toLowerCase(Locale.US).startsWith(q)) continue;
            c.addRow(new Object[] {i, FRUIT[i], FRUIT[i].length() + " letters", FRUIT[i].toLowerCase(Locale.US),
                i == 3 ? SearchManager.FLAG_QUERY_REFINEMENT : 0});
        }
        return c;
    }

    @Override
    public String getType(Uri uri) { return SearchManager.SUGGEST_MIME_TYPE; }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
