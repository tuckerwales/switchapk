package android.widget;

import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Handler;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;

/**
 * Adapter that exposes the rows of a {@link Cursor} (AOSP CursorAdapter). The cursor must have
 * an "_id" column. Rows are created with {@link #newView} and filled by {@link #bindView}.
 */
public abstract class CursorAdapter extends BaseAdapter implements Filterable,
        CursorFilter.CursorFilterClient, ThemedSpinnerAdapter {
    @Deprecated
    public static final int FLAG_AUTO_REQUERY = 0x01;
    public static final int FLAG_REGISTER_CONTENT_OBSERVER = 0x02;

    protected boolean mDataValid;
    protected boolean mAutoRequery;
    protected Cursor mCursor;
    protected Context mContext;
    protected Context mDropDownContext;
    protected int mRowIDColumn;
    protected ChangeObserver mChangeObserver;
    protected DataSetObserver mDataSetObserver;
    protected CursorFilter mCursorFilter;
    protected FilterQueryProvider mFilterQueryProvider;

    @Deprecated
    public CursorAdapter(Context context, Cursor c) { init(context, c, FLAG_AUTO_REQUERY); }

    public CursorAdapter(Context context, Cursor c, boolean autoRequery) {
        init(context, c, autoRequery ? FLAG_AUTO_REQUERY : FLAG_REGISTER_CONTENT_OBSERVER);
    }

    public CursorAdapter(Context context, Cursor c, int flags) { init(context, c, flags); }

    @Deprecated
    protected void init(Context context, Cursor c, boolean autoRequery) {
        init(context, c, autoRequery ? FLAG_AUTO_REQUERY : FLAG_REGISTER_CONTENT_OBSERVER);
    }

    void init(Context context, Cursor c, int flags) {
        if ((flags & FLAG_AUTO_REQUERY) == FLAG_AUTO_REQUERY) {
            flags |= FLAG_REGISTER_CONTENT_OBSERVER;
            mAutoRequery = true;
        } else {
            mAutoRequery = false;
        }
        boolean cursorPresent = c != null;
        mCursor = c;
        mDataValid = cursorPresent;
        mContext = context;
        mRowIDColumn = cursorPresent ? c.getColumnIndexOrThrow("_id") : -1;
        if ((flags & FLAG_REGISTER_CONTENT_OBSERVER) == FLAG_REGISTER_CONTENT_OBSERVER) {
            mChangeObserver = new ChangeObserver();
            mDataSetObserver = new MyDataSetObserver();
        } else {
            mChangeObserver = null;
            mDataSetObserver = null;
        }
        if (cursorPresent) {
            if (mChangeObserver != null) c.registerContentObserver(mChangeObserver);
            if (mDataSetObserver != null) c.registerDataSetObserver(mDataSetObserver);
        }
    }

    @Override
    public void setDropDownViewTheme(Resources.Theme theme) {
        if (theme == null) mDropDownContext = null;
        else if (theme == mContext.getTheme()) mDropDownContext = mContext;
        else mDropDownContext = new ContextThemeWrapper(mContext, theme);
    }

    @Override
    public Resources.Theme getDropDownViewTheme() {
        return mDropDownContext == null ? null : mDropDownContext.getTheme();
    }

    public Cursor getCursor() { return mCursor; }

    public int getCount() {
        if (mDataValid && mCursor != null) return mCursor.getCount();
        return 0;
    }

    public Object getItem(int position) {
        if (mDataValid && mCursor != null) {
            mCursor.moveToPosition(position);
            return mCursor;
        }
        return null;
    }

    public long getItemId(int position) {
        if (mDataValid && mCursor != null) {
            if (mCursor.moveToPosition(position)) return mCursor.getLong(mRowIDColumn);
            return 0;
        }
        return 0;
    }

    @Override
    public boolean hasStableIds() { return true; }

    public View getView(int position, View convertView, ViewGroup parent) {
        if (!mDataValid) throw new IllegalStateException("this should only be called when the cursor is valid");
        if (!mCursor.moveToPosition(position)) {
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }
        View v = convertView == null ? newView(mContext, mCursor, parent) : convertView;
        bindView(v, mContext, mCursor);
        return v;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        if (mDataValid) {
            Context context = mDropDownContext == null ? mContext : mDropDownContext;
            mCursor.moveToPosition(position);
            View v = convertView == null ? newDropDownView(context, mCursor, parent) : convertView;
            bindView(v, context, mCursor);
            return v;
        }
        return null;
    }

    public abstract View newView(Context context, Cursor cursor, ViewGroup parent);

    public View newDropDownView(Context context, Cursor cursor, ViewGroup parent) {
        return newView(context, cursor, parent);
    }

    public abstract void bindView(View view, Context context, Cursor cursor);

    public void changeCursor(Cursor cursor) {
        Cursor old = swapCursor(cursor);
        if (old != null) old.close();
    }

    public Cursor swapCursor(Cursor newCursor) {
        if (newCursor == mCursor) return null;
        Cursor oldCursor = mCursor;
        if (oldCursor != null) {
            if (mChangeObserver != null) oldCursor.unregisterContentObserver(mChangeObserver);
            if (mDataSetObserver != null) oldCursor.unregisterDataSetObserver(mDataSetObserver);
        }
        mCursor = newCursor;
        if (newCursor != null) {
            if (mChangeObserver != null) newCursor.registerContentObserver(mChangeObserver);
            if (mDataSetObserver != null) newCursor.registerDataSetObserver(mDataSetObserver);
            mRowIDColumn = newCursor.getColumnIndexOrThrow("_id");
            mDataValid = true;
            notifyDataSetChanged();
        } else {
            mRowIDColumn = -1;
            mDataValid = false;
            notifyDataSetInvalidated();
        }
        return oldCursor;
    }

    public CharSequence convertToString(Cursor cursor) {
        return cursor == null ? "" : cursor.toString();
    }

    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        if (mFilterQueryProvider != null) return mFilterQueryProvider.runQuery(constraint);
        return mCursor;
    }

    public Filter getFilter() {
        if (mCursorFilter == null) mCursorFilter = new CursorFilter(this);
        return mCursorFilter;
    }

    public FilterQueryProvider getFilterQueryProvider() { return mFilterQueryProvider; }

    public void setFilterQueryProvider(FilterQueryProvider filterQueryProvider) {
        mFilterQueryProvider = filterQueryProvider;
    }

    protected void onContentChanged() {
        if (mAutoRequery && mCursor != null && !mCursor.isClosed()) {
            mDataValid = mCursor.requery();
        }
    }

    private class ChangeObserver extends ContentObserver {
        ChangeObserver() { super(new Handler()); }

        @Override
        public boolean deliverSelfNotifications() { return true; }

        @Override
        public void onChange(boolean selfChange) { onContentChanged(); }
    }

    private class MyDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            mDataValid = true;
            notifyDataSetChanged();
        }

        @Override
        public void onInvalidated() {
            mDataValid = false;
            notifyDataSetInvalidated();
        }
    }
}
