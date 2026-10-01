package android.widget;

import android.database.Cursor;

/** Runs the query for a {@link CursorAdapter}'s filter (AOSP). */
public interface FilterQueryProvider {
    Cursor runQuery(CharSequence constraint);
}
