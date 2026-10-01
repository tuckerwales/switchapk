package android.widget;

/**
 * Constrains an adapter's data (AOSP Filter).
 * Filtering runs on the calling thread: {@link #performFiltering} then {@link #publishResults}.
 */
public abstract class Filter {
    public Filter() {}

    public final void filter(CharSequence constraint) { filter(constraint, null); }

    public final void filter(CharSequence constraint, FilterListener listener) {
        FilterResults results = performFiltering(constraint);
        if (results == null) results = new FilterResults();
        publishResults(constraint, results);
        if (listener != null) listener.onFilterComplete(results.count);
    }

    protected abstract FilterResults performFiltering(CharSequence constraint);

    protected abstract void publishResults(CharSequence constraint, FilterResults results);

    public CharSequence convertResultToString(Object resultValue) {
        return resultValue == null ? "" : resultValue.toString();
    }

    /** Values published by {@link #performFiltering}. */
    public static class FilterResults {
        public int count;
        public Object values;

        public FilterResults() {}
    }

    /** Told how many values survived a filter pass. */
    public interface FilterListener {
        void onFilterComplete(int count);
    }
}
