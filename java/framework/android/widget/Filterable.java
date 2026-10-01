package android.widget;

/** An object that can be filtered, usually an adapter (AOSP Filterable). */
public interface Filterable {
    Filter getFilter();
}
