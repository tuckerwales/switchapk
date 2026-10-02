package com.android.internal.widget;

import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;

/**
 * framework-internal. Base class for the adapter of {@link ViewPager} (AOSP
 * com.android.internal.widget.PagerAdapter, a copy of the support library one).
 */
public abstract class PagerAdapter {
    private DataSetObservable mObservable = new DataSetObservable();

    public static final int POSITION_UNCHANGED = -1;
    public static final int POSITION_NONE = -2;

    public abstract int getCount();

    public void startUpdate(ViewGroup container) {
        startUpdate((View) container);
    }

    public Object instantiateItem(ViewGroup container, int position) {
        return instantiateItem((View) container, position);
    }

    public void destroyItem(ViewGroup container, int position, Object object) {
        destroyItem((View) container, position, object);
    }

    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        setPrimaryItem((View) container, position, object);
    }

    public void finishUpdate(ViewGroup container) {
        finishUpdate((View) container);
    }

    /** @deprecated use {@link #startUpdate(ViewGroup)} */
    public void startUpdate(View container) {
    }

    /** @deprecated use {@link #instantiateItem(ViewGroup, int)} */
    public Object instantiateItem(View container, int position) {
        throw new UnsupportedOperationException("Required method instantiateItem was not overridden");
    }

    /** @deprecated use {@link #destroyItem(ViewGroup, int, Object)} */
    public void destroyItem(View container, int position, Object object) {
        throw new UnsupportedOperationException("Required method destroyItem was not overridden");
    }

    /** @deprecated use {@link #setPrimaryItem(ViewGroup, int, Object)} */
    public void setPrimaryItem(View container, int position, Object object) {
    }

    /** @deprecated use {@link #finishUpdate(ViewGroup)} */
    public void finishUpdate(View container) {
    }

    public abstract boolean isViewFromObject(View view, Object object);

    public Parcelable saveState() {
        return null;
    }

    public void restoreState(Parcelable state, ClassLoader loader) {
    }

    public int getItemPosition(Object object) {
        return POSITION_UNCHANGED;
    }

    public void notifyDataSetChanged() {
        mObservable.notifyChanged();
    }

    public void registerDataSetObserver(DataSetObserver observer) {
        mObservable.registerObserver(observer);
    }

    public void unregisterDataSetObserver(DataSetObserver observer) {
        mObservable.unregisterObserver(observer);
    }

    public CharSequence getPageTitle(int position) {
        return null;
    }

    public float getPageWidth(int position) {
        return 1.f;
    }
}
