package android.widget;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/** A {@link CursorAdapter} whose rows are inflated from a layout resource (AOSP). */
public abstract class ResourceCursorAdapter extends CursorAdapter {
    private int mLayout;
    private int mDropDownLayout;
    private LayoutInflater mInflater;
    private LayoutInflater mDropDownInflater;

    @Deprecated
    public ResourceCursorAdapter(Context context, int layout, Cursor c) {
        super(context, c);
        mLayout = mDropDownLayout = layout;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mDropDownInflater = mInflater;
    }

    @Deprecated
    public ResourceCursorAdapter(Context context, int layout, Cursor c, boolean autoRequery) {
        super(context, c, autoRequery);
        mLayout = mDropDownLayout = layout;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mDropDownInflater = mInflater;
    }

    public ResourceCursorAdapter(Context context, int layout, Cursor c, int flags) {
        super(context, c, flags);
        mLayout = mDropDownLayout = layout;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mDropDownInflater = mInflater;
    }

    @Override
    public void setDropDownViewTheme(Resources.Theme theme) {
        super.setDropDownViewTheme(theme);
        if (theme == null) mDropDownInflater = null;
        else if (theme == mInflater.getContext().getTheme()) mDropDownInflater = mInflater;
        else mDropDownInflater = LayoutInflater.from(mDropDownContext);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return mInflater.inflate(mLayout, parent, false);
    }

    @Override
    public View newDropDownView(Context context, Cursor cursor, ViewGroup parent) {
        LayoutInflater inflater = mDropDownInflater != null ? mDropDownInflater : mInflater;
        return inflater.inflate(mDropDownLayout, parent, false);
    }

    public void setViewResource(int layout) { mLayout = layout; }

    public void setDropDownViewResource(int dropDownLayout) { mDropDownLayout = dropDownLayout; }
}
