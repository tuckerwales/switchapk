package android.widget;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.view.View;

/**
 * Maps cursor columns to TextViews and ImageViews of a row layout (AOSP SimpleCursorAdapter).
 * A {@link ViewBinder} can take over the binding of any view.
 */
public class SimpleCursorAdapter extends ResourceCursorAdapter {
    protected int[] mFrom;
    protected int[] mTo;
    private int mStringConversionColumn = -1;
    private CursorToStringConverter mCursorToStringConverter;
    private ViewBinder mViewBinder;
    String[] mOriginalFrom;

    @Deprecated
    public SimpleCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
        super(context, layout, c);
        mTo = to;
        mOriginalFrom = from;
        findColumns(c, from);
    }

    public SimpleCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
        super(context, layout, c, flags);
        mTo = to;
        mOriginalFrom = from;
        findColumns(c, from);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewBinder binder = mViewBinder;
        int count = mTo.length;
        int[] from = mFrom;
        int[] to = mTo;
        for (int i = 0; i < count; i++) {
            View v = view.findViewById(to[i]);
            if (v == null) continue;
            boolean bound = false;
            if (binder != null) bound = binder.setViewValue(v, cursor, from[i]);
            if (bound) continue;
            String text = cursor.getString(from[i]);
            if (text == null) text = "";
            if (v instanceof TextView) {
                setViewText((TextView) v, text);
            } else if (v instanceof ImageView) {
                setViewImage((ImageView) v, text);
            } else {
                throw new IllegalStateException(v.getClass().getName() + " is not a "
                        + " view that can be bounds by this SimpleCursorAdapter");
            }
        }
    }

    public ViewBinder getViewBinder() { return mViewBinder; }

    public void setViewBinder(ViewBinder viewBinder) { mViewBinder = viewBinder; }

    public void setViewImage(ImageView v, String value) {
        try {
            v.setImageResource(Integer.parseInt(value));
        } catch (NumberFormatException nfe) {
            v.setImageURI(Uri.parse(value));
        }
    }

    public void setViewText(TextView v, String text) { v.setText(text); }

    public int getStringConversionColumn() { return mStringConversionColumn; }

    public void setStringConversionColumn(int stringConversionColumn) {
        mStringConversionColumn = stringConversionColumn;
    }

    public CursorToStringConverter getCursorToStringConverter() { return mCursorToStringConverter; }

    public void setCursorToStringConverter(CursorToStringConverter cursorToStringConverter) {
        mCursorToStringConverter = cursorToStringConverter;
    }

    @Override
    public CharSequence convertToString(Cursor cursor) {
        if (mCursorToStringConverter != null) return mCursorToStringConverter.convertToString(cursor);
        if (mStringConversionColumn > -1) return cursor.getString(mStringConversionColumn);
        return super.convertToString(cursor);
    }

    private void findColumns(Cursor c, String[] from) {
        if (c != null) {
            int count = from.length;
            if (mFrom == null || mFrom.length != count) mFrom = new int[count];
            for (int i = 0; i < count; i++) mFrom[i] = c.getColumnIndexOrThrow(from[i]);
        } else {
            mFrom = null;
        }
    }

    @Override
    public Cursor swapCursor(Cursor c) {
        // Find the columns before the super call: it notifies observers, which rebind rows.
        findColumns(c, mOriginalFrom);
        return super.swapCursor(c);
    }

    public void changeCursorAndColumns(Cursor c, String[] from, int[] to) {
        mOriginalFrom = from;
        mTo = to;
        findColumns(c, mOriginalFrom);
        super.changeCursor(c);
    }

    /** Binds one cursor column to one view; return true when the view was handled. */
    public static interface ViewBinder {
        boolean setViewValue(View view, Cursor cursor, int columnIndex);
    }

    /** Converts the current cursor row to the text an AutoCompleteTextView shows. */
    public static interface CursorToStringConverter {
        CharSequence convertToString(Cursor cursor);
    }
}
