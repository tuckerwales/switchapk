package android.widget;

/** A {@link ListAdapter} that wraps another one (AOSP WrapperListAdapter). */
public interface WrapperListAdapter extends ListAdapter {
    ListAdapter getWrappedAdapter();
}
