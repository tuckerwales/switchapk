package android.widget;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A {@link CursorTreeAdapter} that inflates its group and child views from layout resources
 * (AOSP ResourceCursorTreeAdapter): one layout each for collapsed and expanded groups, and for
 * ordinary and last children.
 */
public abstract class ResourceCursorTreeAdapter extends CursorTreeAdapter {
    private int mCollapsedGroupLayout;
    private int mExpandedGroupLayout;
    private int mChildLayout;
    private int mLastChildLayout;
    private LayoutInflater mInflater;

    public ResourceCursorTreeAdapter(Context context, Cursor cursor, int collapsedGroupLayout,
            int expandedGroupLayout, int childLayout, int lastChildLayout) {
        super(cursor, context);
        mCollapsedGroupLayout = collapsedGroupLayout;
        mExpandedGroupLayout = expandedGroupLayout;
        mChildLayout = childLayout;
        mLastChildLayout = lastChildLayout;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public ResourceCursorTreeAdapter(Context context, Cursor cursor, int collapsedGroupLayout,
            int expandedGroupLayout, int childLayout) {
        this(context, cursor, collapsedGroupLayout, expandedGroupLayout, childLayout, childLayout);
    }

    public ResourceCursorTreeAdapter(Context context, Cursor cursor, int groupLayout, int childLayout) {
        this(context, cursor, groupLayout, groupLayout, childLayout, childLayout);
    }

    @Override
    public View newChildView(Context context, Cursor cursor, boolean isLastChild, ViewGroup parent) {
        return mInflater.inflate((isLastChild) ? mLastChildLayout : mChildLayout, parent, false);
    }

    @Override
    public View newGroupView(Context context, Cursor cursor, boolean isExpanded, ViewGroup parent) {
        return mInflater.inflate((isExpanded) ? mExpandedGroupLayout : mCollapsedGroupLayout, parent, false);
    }
}
