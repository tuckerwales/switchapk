package android.widget;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps the values of a list of maps to the views of a row layout (AOSP SimpleAdapter).
 * Checkable views take Booleans, TextViews text, ImageViews a resource id or a URI string.
 */
public class SimpleAdapter extends BaseAdapter implements Filterable, ThemedSpinnerAdapter {
    private final LayoutInflater mInflater;
    private int[] mTo;
    private String[] mFrom;
    private ViewBinder mViewBinder;
    private List<? extends Map<String, ?>> mData;
    private int mResource;
    private int mDropDownResource;
    private LayoutInflater mDropDownInflater;
    private SimpleFilter mFilter;
    private ArrayList<Map<String, ?>> mUnfilteredData;

    public SimpleAdapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
        mData = data;
        mResource = mDropDownResource = resource;
        mFrom = from;
        mTo = to;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public int getCount() { return mData.size(); }

    public Object getItem(int position) { return mData.get(position); }

    public long getItemId(int position) { return position; }

    public View getView(int position, View convertView, ViewGroup parent) {
        return createViewFromResource(mInflater, position, convertView, parent, mResource);
    }

    private View createViewFromResource(LayoutInflater inflater, int position, View convertView, ViewGroup parent,
            int resource) {
        View v = convertView == null ? inflater.inflate(resource, parent, false) : convertView;
        bindView(position, v);
        return v;
    }

    public void setDropDownViewResource(int resource) { mDropDownResource = resource; }

    @Override
    public void setDropDownViewTheme(Resources.Theme theme) {
        if (theme == null) {
            mDropDownInflater = null;
        } else if (theme == mInflater.getContext().getTheme()) {
            mDropDownInflater = mInflater;
        } else {
            Context context = new ContextThemeWrapper(mInflater.getContext(), theme);
            mDropDownInflater = LayoutInflater.from(context);
        }
    }

    @Override
    public Resources.Theme getDropDownViewTheme() {
        return mDropDownInflater == null ? null : mDropDownInflater.getContext().getTheme();
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = mDropDownInflater == null ? mInflater : mDropDownInflater;
        return createViewFromResource(inflater, position, convertView, parent, mDropDownResource);
    }

    private void bindView(int position, View view) {
        Map<String, ?> dataSet = mData.get(position);
        if (dataSet == null) return;
        ViewBinder binder = mViewBinder;
        String[] from = mFrom;
        int[] to = mTo;
        int count = to.length;
        for (int i = 0; i < count; i++) {
            View v = view.findViewById(to[i]);
            if (v == null) continue;
            Object data = dataSet.get(from[i]);
            String text = data == null ? "" : data.toString();
            if (text == null) text = "";
            boolean bound = false;
            if (binder != null) bound = binder.setViewValue(v, data, text);
            if (bound) continue;
            if (v instanceof Checkable) {
                if (data instanceof Boolean) {
                    ((Checkable) v).setChecked((Boolean) data);
                } else if (v instanceof TextView) {
                    // Note: keep the instanceof TextView check at the bottom of these ifs since
                    // a TextView can be a Checkable.
                    setViewText((TextView) v, text);
                } else {
                    throw new IllegalStateException(v.getClass().getName()
                            + " should be bound to a Boolean, not a "
                            + (data == null ? "<unknown type>" : data.getClass()));
                }
            } else if (v instanceof TextView) {
                setViewText((TextView) v, text);
            } else if (v instanceof ImageView) {
                if (data instanceof Integer) setViewImage((ImageView) v, (Integer) data);
                else setViewImage((ImageView) v, text);
            } else {
                throw new IllegalStateException(v.getClass().getName() + " is not a "
                        + " view that can be bounds by this SimpleAdapter");
            }
        }
    }

    public ViewBinder getViewBinder() { return mViewBinder; }

    public void setViewBinder(ViewBinder viewBinder) { mViewBinder = viewBinder; }

    public void setViewImage(ImageView v, int value) { v.setImageResource(value); }

    public void setViewImage(ImageView v, String value) {
        try {
            v.setImageResource(Integer.parseInt(value));
        } catch (NumberFormatException nfe) {
            v.setImageURI(Uri.parse(value));
        }
    }

    public void setViewText(TextView v, String text) { v.setText(text); }

    public Filter getFilter() {
        if (mFilter == null) mFilter = new SimpleFilter();
        return mFilter;
    }

    /** Binds one value to one view; return true when the view was handled. */
    public static interface ViewBinder {
        boolean setViewValue(View view, Object data, String textRepresentation);
    }

    /** Keeps the rows where a word of a bound value starts with the prefix. */
    private class SimpleFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            FilterResults results = new FilterResults();
            if (mUnfilteredData == null) mUnfilteredData = new ArrayList<Map<String, ?>>(mData);
            if (prefix == null || prefix.length() == 0) {
                ArrayList<Map<String, ?>> list = mUnfilteredData;
                results.values = list;
                results.count = list.size();
            } else {
                String prefixString = prefix.toString().toLowerCase();
                ArrayList<Map<String, ?>> unfilteredValues = mUnfilteredData;
                int count = unfilteredValues.size();
                ArrayList<Map<String, ?>> newValues = new ArrayList<Map<String, ?>>(count);
                for (int i = 0; i < count; i++) {
                    Map<String, ?> h = unfilteredValues.get(i);
                    if (h == null) continue;
                    int len = mTo.length;
                    boolean added = false;
                    for (int j = 0; j < len && !added; j++) {
                        Object value = h.get(mFrom[j]);
                        if (value == null) continue;
                        String str = value.toString();
                        String[] words = str.split(" ");
                        for (int k = 0; k < words.length; k++) {
                            if (words[k].toLowerCase().startsWith(prefixString)) {
                                newValues.add(h);
                                added = true;
                                break;
                            }
                        }
                    }
                }
                results.values = newValues;
                results.count = newValues.size();
            }
            return results;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            mData = (List<Map<String, ?>>) results.values;
            if (results.count > 0) notifyDataSetChanged();
            else notifyDataSetInvalidated();
        }
    }
}
