package android.widget;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.view.View;

/**
 * Maps group and children cursor columns to TextViews and ImageViews of the group and child
 * layouts (AOSP SimpleCursorTreeAdapter). Column indices are looked up on the first bind of each
 * kind; a {@link ViewBinder} can take over the binding of any view.
 */
public abstract class SimpleCursorTreeAdapter extends ResourceCursorTreeAdapter {
    private String[] mGroupFromNames;
    private int[] mGroupFrom;
    private int[] mGroupTo;
    private String[] mChildFromNames;
    private int[] mChildFrom;
    private int[] mChildTo;
    private ViewBinder mViewBinder;

    public SimpleCursorTreeAdapter(Context context, Cursor cursor, int collapsedGroupLayout,
            int expandedGroupLayout, String[] groupFrom, int[] groupTo, int childLayout,
            int lastChildLayout, String[] childFrom, int[] childTo) {
        super(context, cursor, collapsedGroupLayout, expandedGroupLayout, childLayout, lastChildLayout);
        init(groupFrom, groupTo, childFrom, childTo);
    }

    public SimpleCursorTreeAdapter(Context context, Cursor cursor, int collapsedGroupLayout,
            int expandedGroupLayout, String[] groupFrom, int[] groupTo, int childLayout,
            String[] childFrom, int[] childTo) {
        super(context, cursor, collapsedGroupLayout, expandedGroupLayout, childLayout);
        init(groupFrom, groupTo, childFrom, childTo);
    }

    public SimpleCursorTreeAdapter(Context context, Cursor cursor, int groupLayout, String[] groupFrom,
            int[] groupTo, int childLayout, String[] childFrom, int[] childTo) {
        super(context, cursor, groupLayout, childLayout);
        init(groupFrom, groupTo, childFrom, childTo);
    }

    private void init(String[] groupFromNames, int[] groupTo, String[] childFromNames, int[] childTo) {
        mGroupFromNames = groupFromNames;
        mGroupTo = groupTo;
        mChildFromNames = childFromNames;
        mChildTo = childTo;
    }

    public ViewBinder getViewBinder() { return mViewBinder; }

    public void setViewBinder(ViewBinder viewBinder) { mViewBinder = viewBinder; }

    private void bindView(View view, Context context, Cursor cursor, int[] from, int[] to) {
        final ViewBinder binder = mViewBinder;
        for (int i = 0; i < to.length; i++) {
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
                throw new IllegalStateException("SimpleCursorTreeAdapter can bind values"
                        + " only to TextView and ImageView!");
            }
        }
    }

    private void initFromColumns(Cursor cursor, String[] fromColumnNames, int[] fromColumns) {
        for (int i = fromColumnNames.length - 1; i >= 0; i--) {
            fromColumns[i] = cursor.getColumnIndexOrThrow(fromColumnNames[i]);
        }
    }

    @Override
    protected void bindChildView(View view, Context context, Cursor cursor, boolean isLastChild) {
        if (mChildFrom == null) {
            mChildFrom = new int[mChildFromNames.length];
            initFromColumns(cursor, mChildFromNames, mChildFrom);
        }
        bindView(view, context, cursor, mChildFrom, mChildTo);
    }

    @Override
    protected void bindGroupView(View view, Context context, Cursor cursor, boolean isExpanded) {
        if (mGroupFrom == null) {
            mGroupFrom = new int[mGroupFromNames.length];
            initFromColumns(cursor, mGroupFromNames, mGroupFrom);
        }
        bindView(view, context, cursor, mGroupFrom, mGroupTo);
    }

    /** Sets a resource id, or failing that a URI, as the image of an ImageView. */
    protected void setViewImage(ImageView v, String value) {
        try {
            v.setImageResource(Integer.parseInt(value));
        } catch (NumberFormatException nfe) {
            v.setImageURI(Uri.parse(value));
        }
    }

    public void setViewText(TextView v, String text) { v.setText(text); }

    /** Binds one cursor column to one view; return true when the view was handled. */
    public static interface ViewBinder {
        boolean setViewValue(View view, Cursor cursor, int columnIndex);
    }
}
