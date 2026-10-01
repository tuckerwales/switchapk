package android.widget;

import android.content.Context;
import android.content.res.Resources;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A {@link BaseAdapter} backed by an array or a list (AOSP ArrayAdapter).
 * The row resource is inflated and its text is the item, or {@code item.toString()}.
 */
public class ArrayAdapter<T> extends BaseAdapter implements Filterable, ThemedSpinnerAdapter {
    private final Object mLock = new Object();
    private final Context mContext;
    private final LayoutInflater mInflater;
    private final int mResource;
    private int mDropDownResource;
    private final int mFieldId;
    private List<T> mObjects;
    private ArrayList<T> mOriginalValues;
    private boolean mNotifyOnChange = true;
    private LayoutInflater mDropDownInflater;
    private ArrayFilter mFilter;

    public ArrayAdapter(Context context, int resource) {
        this(context, resource, 0, new ArrayList<T>());
    }

    public ArrayAdapter(Context context, int resource, int textViewResourceId) {
        this(context, resource, textViewResourceId, new ArrayList<T>());
    }

    public ArrayAdapter(Context context, int resource, T[] objects) {
        this(context, resource, 0, Arrays.asList(objects));
    }

    public ArrayAdapter(Context context, int resource, int textViewResourceId, T[] objects) {
        this(context, resource, textViewResourceId, Arrays.asList(objects));
    }

    public ArrayAdapter(Context context, int resource, List<T> objects) {
        this(context, resource, 0, objects);
    }

    public ArrayAdapter(Context context, int resource, int textViewResourceId, List<T> objects) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mResource = resource;
        mDropDownResource = resource;
        mFieldId = textViewResourceId;
        mObjects = objects;
    }

    public void add(T object) {
        synchronized (mLock) {
            if (mOriginalValues != null) mOriginalValues.add(object);
            else mObjects.add(object);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void addAll(Collection<? extends T> collection) {
        synchronized (mLock) {
            if (mOriginalValues != null) mOriginalValues.addAll(collection);
            else mObjects.addAll(collection);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void addAll(T... items) {
        synchronized (mLock) {
            if (mOriginalValues != null) Collections.addAll(mOriginalValues, items);
            else Collections.addAll(mObjects, items);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void insert(T object, int index) {
        synchronized (mLock) {
            if (mOriginalValues != null) mOriginalValues.add(index, object);
            else mObjects.add(index, object);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void remove(T object) {
        synchronized (mLock) {
            if (mOriginalValues != null) mOriginalValues.remove(object);
            else mObjects.remove(object);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void clear() {
        synchronized (mLock) {
            if (mOriginalValues != null) mOriginalValues.clear();
            else mObjects.clear();
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void sort(Comparator<? super T> comparator) {
        synchronized (mLock) {
            if (mOriginalValues != null) Collections.sort(mOriginalValues, comparator);
            else Collections.sort(mObjects, comparator);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        mNotifyOnChange = true;
    }

    public void setNotifyOnChange(boolean notifyOnChange) { mNotifyOnChange = notifyOnChange; }

    public Context getContext() { return mContext; }

    public int getCount() { return mObjects.size(); }

    public T getItem(int position) { return mObjects.get(position); }

    public int getPosition(T item) { return mObjects.indexOf(item); }

    public long getItemId(int position) { return position; }

    public View getView(int position, View convertView, ViewGroup parent) {
        return createViewFromResource(mInflater, position, convertView, parent, mResource);
    }

    public void setDropDownViewResource(int resource) { mDropDownResource = resource; }

    public void setDropDownViewTheme(Resources.Theme theme) {
        if (theme == null) {
            mDropDownInflater = null;
        } else if (theme == mInflater.getContext().getTheme()) {
            mDropDownInflater = mInflater;
        } else {
            Context themed = new ContextThemeWrapper(mContext, theme);
            mDropDownInflater = LayoutInflater.from(themed);
        }
    }

    public Resources.Theme getDropDownViewTheme() {
        return mDropDownInflater == null ? null : mDropDownInflater.getContext().getTheme();
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = mDropDownInflater == null ? mInflater : mDropDownInflater;
        return createViewFromResource(inflater, position, convertView, parent, mDropDownResource);
    }

    private View createViewFromResource(LayoutInflater inflater, int position, View convertView,
            ViewGroup parent, int resource) {
        View view;
        TextView text;
        if (convertView == null) view = inflater.inflate(resource, parent, false);
        else view = convertView;
        try {
            if (mFieldId == 0) text = (TextView) view;
            else {
                text = view.findViewById(mFieldId);
                if (text == null) {
                    throw new RuntimeException("Failed to find view with ID " + mContext.getResources()
                            .getResourceName(mFieldId) + " in item layout");
                }
            }
        } catch (ClassCastException e) {
            throw new IllegalStateException("ArrayAdapter requires the resource ID to be a TextView", e);
        }
        T item = getItem(position);
        if (item instanceof CharSequence) text.setText((CharSequence) item);
        else text.setText(item == null ? "" : item.toString());
        return view;
    }

    public static ArrayAdapter<CharSequence> createFromResource(Context context, int textArrayResId,
            int textViewResId) {
        CharSequence[] strings = context.getResources().getTextArray(textArrayResId);
        return new ArrayAdapter<CharSequence>(context, textViewResId, strings);
    }

    public Filter getFilter() {
        if (mFilter == null) mFilter = new ArrayFilter();
        return mFilter;
    }

    @Override
    public CharSequence[] getAutofillOptions() {
        CharSequence[] explicit = super.getAutofillOptions();
        if (explicit != null) return explicit;
        if (mObjects == null || mObjects.isEmpty()) return null;
        int count = mObjects.size();
        CharSequence[] options = new CharSequence[count];
        for (int i = 0; i < count; i++) {
            T item = mObjects.get(i);
            if (!(item instanceof CharSequence)) return null;
            options[i] = (CharSequence) item;
        }
        return options;
    }

    private class ArrayFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            FilterResults results = new FilterResults();
            if (mOriginalValues == null) {
                synchronized (mLock) {
                    mOriginalValues = new ArrayList<T>(mObjects);
                }
            }
            if (prefix == null || prefix.length() == 0) {
                ArrayList<T> list;
                synchronized (mLock) {
                    list = new ArrayList<T>(mOriginalValues);
                }
                results.values = list;
                results.count = list.size();
                return results;
            }
            String prefixString = prefix.toString().toLowerCase();
            ArrayList<T> values;
            synchronized (mLock) {
                values = new ArrayList<T>(mOriginalValues);
            }
            ArrayList<T> kept = new ArrayList<T>();
            for (int i = 0; i < values.size(); i++) {
                T value = values.get(i);
                String valueText = value == null ? "" : value.toString().toLowerCase();
                if (valueText.startsWith(prefixString)) {
                    kept.add(value);
                    continue;
                }
                String[] words = valueText.split(" ");
                for (int w = 0; w < words.length; w++) {
                    if (words[w].startsWith(prefixString)) {
                        kept.add(value);
                        break;
                    }
                }
            }
            results.values = kept;
            results.count = kept.size();
            return results;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            mObjects = (List<T>) results.values;
            if (results.count > 0) notifyDataSetChanged();
            else notifyDataSetInvalidated();
        }
    }
}
