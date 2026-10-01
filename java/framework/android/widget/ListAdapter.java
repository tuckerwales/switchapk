package android.widget;

/**
 * An {@link Adapter} that also reports which rows can be chosen (AOSP ListAdapter).
 */
public interface ListAdapter extends Adapter {
    boolean areAllItemsEnabled();

    boolean isEnabled(int position);
}
