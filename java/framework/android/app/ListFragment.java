package android.app;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * A fragment that shows a ListView (port of AOSP ListFragment). The default
 * view is built in code (AOSP inflates the framework list_content layout,
 * which needs ProgressBar).
 */
@Deprecated
public class ListFragment extends Fragment {
    private final Handler mHandler = new Handler();

    private final Runnable mRequestFocus = new Runnable() {
        public void run() { mList.focusableViewAvailable(mList); }
    };

    private final AdapterView.OnItemClickListener mOnClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            onListItemClick((ListView) parent, v, position, id);
        }
    };

    ListAdapter mAdapter;
    ListView mList;
    View mEmptyView;
    TextView mStandardEmptyView;
    View mProgressContainer;
    View mListContainer;
    CharSequence mEmptyText;
    boolean mListShown;

    public ListFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final Context context = inflater.getContext();
        FrameLayout root = new FrameLayout(context);
        LinearLayout progress = new LinearLayout(context);
        progress.setId(PROGRESS_CONTAINER_ID);
        progress.setOrientation(LinearLayout.VERTICAL);
        progress.setVisibility(View.GONE);
        progress.setGravity(Gravity.CENTER);
        root.addView(progress, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout lframe = new FrameLayout(context);
        lframe.setId(LIST_CONTAINER_ID);
        TextView tv = new TextView(context);
        tv.setId(android.R.id.empty);
        tv.setGravity(Gravity.CENTER);
        lframe.addView(tv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        ListView lv = new ListView(context);
        lv.setId(android.R.id.list);
        lv.setDrawSelectorOnTop(false);
        lframe.addView(lv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(lframe, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private static final int PROGRESS_CONTAINER_ID = 0x00ff0002;
    private static final int LIST_CONTAINER_ID = 0x00ff0003;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ensureList();
    }

    @Override
    public void onDestroyView() {
        mHandler.removeCallbacks(mRequestFocus);
        mList = null;
        mListShown = false;
        mEmptyView = mProgressContainer = mListContainer = null;
        mStandardEmptyView = null;
        super.onDestroyView();
    }

    public void onListItemClick(ListView l, View v, int position, long id) {}

    public void setListAdapter(ListAdapter adapter) {
        boolean hadAdapter = mAdapter != null;
        mAdapter = adapter;
        if (mList != null) {
            mList.setAdapter(adapter);
            if (!mListShown && !hadAdapter) setListShown(true, getView().getWindowToken() != null);
        }
    }

    public void setSelection(int position) {
        ensureList();
        mList.setSelection(position);
    }

    public int getSelectedItemPosition() {
        ensureList();
        return mList.getSelectedItemPosition();
    }

    public long getSelectedItemId() {
        ensureList();
        return mList.getSelectedItemId();
    }

    public ListView getListView() {
        ensureList();
        return mList;
    }

    public void setEmptyText(CharSequence text) {
        ensureList();
        if (mStandardEmptyView == null) throw new IllegalStateException("Can't be used with a custom content view");
        mStandardEmptyView.setText(text);
        if (mEmptyText == null) mList.setEmptyView(mStandardEmptyView);
        mEmptyText = text;
    }

    public void setListShown(boolean shown) { setListShown(shown, true); }

    public void setListShownNoAnimation(boolean shown) { setListShown(shown, false); }

    private void setListShown(boolean shown, boolean animate) {
        ensureList();
        if (mProgressContainer == null) throw new IllegalStateException("Can't be used with a custom content view");
        if (mListShown == shown) return;
        mListShown = shown;
        if (shown) {
            mProgressContainer.setVisibility(View.GONE);
            mListContainer.setVisibility(View.VISIBLE);
        } else {
            mProgressContainer.setVisibility(View.VISIBLE);
            mListContainer.setVisibility(View.GONE);
        }
    }

    public ListAdapter getListAdapter() { return mAdapter; }

    private void ensureList() {
        if (mList != null) return;
        View root = getView();
        if (root == null) throw new IllegalStateException("Content view not yet created");
        if (root instanceof ListView) {
            mList = (ListView) root;
        } else {
            mStandardEmptyView = (TextView) root.findViewById(android.R.id.empty);
            if (mStandardEmptyView == null) mEmptyView = root.findViewById(android.R.id.empty);
            else mStandardEmptyView.setVisibility(View.GONE);
            mProgressContainer = root.findViewById(PROGRESS_CONTAINER_ID);
            mListContainer = root.findViewById(LIST_CONTAINER_ID);
            View rawListView = root.findViewById(android.R.id.list);
            if (!(rawListView instanceof ListView)) {
                throw new RuntimeException("Content has view with id attribute 'android.R.id.list' that is not a ListView class");
            }
            mList = (ListView) rawListView;
            if (mList == null) {
                throw new RuntimeException("Your content must have a ListView whose id attribute is 'android.R.id.list'");
            }
            if (mEmptyView != null) {
                mList.setEmptyView(mEmptyView);
            } else if (mEmptyText != null) {
                mStandardEmptyView.setText(mEmptyText);
                mList.setEmptyView(mStandardEmptyView);
            }
        }
        mListShown = true;
        mList.setOnItemClickListener(mOnClickListener);
        if (mAdapter != null) {
            ListAdapter adapter = mAdapter;
            mAdapter = null;
            setListAdapter(adapter);
        } else {
            if (mProgressContainer != null) setListShown(false, false);
        }
        mHandler.post(mRequestFocus);
    }
}
