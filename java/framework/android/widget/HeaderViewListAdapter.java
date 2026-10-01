package android.widget;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * A {@link ListAdapter} that places fixed header and footer views around another adapter
 * (AOSP HeaderViewListAdapter). {@link #isEmpty} follows the wrapped adapter.
 */
public class HeaderViewListAdapter implements WrapperListAdapter, Filterable {
    private final ListAdapter mAdapter;
    ArrayList<ListView.FixedViewInfo> mHeaderViewInfos;
    ArrayList<ListView.FixedViewInfo> mFooterViewInfos;
    static final ArrayList<ListView.FixedViewInfo> EMPTY_INFO_LIST = new ArrayList<ListView.FixedViewInfo>();
    boolean mAreAllFixedViewsSelectable;
    private final boolean mIsFilterable;

    public HeaderViewListAdapter(ArrayList<ListView.FixedViewInfo> headerViewInfos,
            ArrayList<ListView.FixedViewInfo> footerViewInfos, ListAdapter adapter) {
        mAdapter = adapter;
        mIsFilterable = adapter instanceof Filterable;
        mHeaderViewInfos = headerViewInfos == null ? EMPTY_INFO_LIST : headerViewInfos;
        mFooterViewInfos = footerViewInfos == null ? EMPTY_INFO_LIST : footerViewInfos;
        mAreAllFixedViewsSelectable = areAllListInfosSelectable(mHeaderViewInfos)
                && areAllListInfosSelectable(mFooterViewInfos);
    }

    public int getHeadersCount() { return mHeaderViewInfos.size(); }

    public int getFootersCount() { return mFooterViewInfos.size(); }

    public boolean isEmpty() { return mAdapter == null || mAdapter.isEmpty(); }

    private boolean areAllListInfosSelectable(ArrayList<ListView.FixedViewInfo> infos) {
        if (infos == null) return true;
        for (int i = 0; i < infos.size(); i++) {
            if (!infos.get(i).isSelectable) return false;
        }
        return true;
    }

    public boolean removeHeader(View v) {
        for (int i = 0; i < mHeaderViewInfos.size(); i++) {
            if (mHeaderViewInfos.get(i).view == v) {
                mHeaderViewInfos.remove(i);
                mAreAllFixedViewsSelectable = areAllListInfosSelectable(mHeaderViewInfos)
                        && areAllListInfosSelectable(mFooterViewInfos);
                return true;
            }
        }
        return false;
    }

    public boolean removeFooter(View v) {
        for (int i = 0; i < mFooterViewInfos.size(); i++) {
            if (mFooterViewInfos.get(i).view == v) {
                mFooterViewInfos.remove(i);
                mAreAllFixedViewsSelectable = areAllListInfosSelectable(mHeaderViewInfos)
                        && areAllListInfosSelectable(mFooterViewInfos);
                return true;
            }
        }
        return false;
    }

    public int getCount() {
        int wrapped = mAdapter == null ? 0 : mAdapter.getCount();
        return getFootersCount() + getHeadersCount() + wrapped;
    }

    public boolean areAllItemsEnabled() {
        if (mAdapter != null) return mAreAllFixedViewsSelectable && mAdapter.areAllItemsEnabled();
        return true;
    }

    public boolean isEnabled(int position) {
        int numHeaders = getHeadersCount();
        if (position < numHeaders) return mHeaderViewInfos.get(position).isSelectable;
        int adj = position - numHeaders;
        int adapterCount = mAdapter == null ? 0 : mAdapter.getCount();
        if (mAdapter != null && adj < adapterCount) return mAdapter.isEnabled(adj);
        return mFooterViewInfos.get(adj - adapterCount).isSelectable;
    }

    public Object getItem(int position) {
        int numHeaders = getHeadersCount();
        if (position < numHeaders) return mHeaderViewInfos.get(position).data;
        int adj = position - numHeaders;
        int adapterCount = mAdapter == null ? 0 : mAdapter.getCount();
        if (mAdapter != null && adj < adapterCount) return mAdapter.getItem(adj);
        return mFooterViewInfos.get(adj - adapterCount).data;
    }

    public long getItemId(int position) {
        int numHeaders = getHeadersCount();
        if (mAdapter != null && position >= numHeaders) {
            int adj = position - numHeaders;
            if (adj < mAdapter.getCount()) return mAdapter.getItemId(adj);
        }
        return -1;
    }

    public boolean hasStableIds() { return mAdapter != null && mAdapter.hasStableIds(); }

    public View getView(int position, View convertView, ViewGroup parent) {
        int numHeaders = getHeadersCount();
        if (position < numHeaders) return mHeaderViewInfos.get(position).view;
        int adj = position - numHeaders;
        int adapterCount = mAdapter == null ? 0 : mAdapter.getCount();
        if (mAdapter != null && adj < adapterCount) return mAdapter.getView(adj, convertView, parent);
        return mFooterViewInfos.get(adj - adapterCount).view;
    }

    public int getItemViewType(int position) {
        int numHeaders = getHeadersCount();
        if (mAdapter != null && position >= numHeaders) {
            int adj = position - numHeaders;
            if (adj < mAdapter.getCount()) return mAdapter.getItemViewType(adj);
        }
        return AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER;
    }

    public int getViewTypeCount() {
        if (mAdapter != null) return mAdapter.getViewTypeCount();
        return 1;
    }

    public void registerDataSetObserver(DataSetObserver observer) {
        if (mAdapter != null) mAdapter.registerDataSetObserver(observer);
    }

    public void unregisterDataSetObserver(DataSetObserver observer) {
        if (mAdapter != null) mAdapter.unregisterDataSetObserver(observer);
    }

    public Filter getFilter() {
        if (mIsFilterable) return ((Filterable) mAdapter).getFilter();
        return null;
    }

    public ListAdapter getWrappedAdapter() { return mAdapter; }
}
