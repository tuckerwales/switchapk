package com.example.list;

import android.app.Activity;
import android.database.DataSetObserver;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

/** Logic checks for lists. Each logs "LISTCHECK ok name" or a failure. */
final class ListChecks {
    static final String TAG = "ListTest";
    static int failures;

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "LISTCHECK ok " + name);
        else {
            failures++;
            Log.e(TAG, "LISTCHECK FAIL " + name + " " + detail);
        }
    }

    static void prepare(Activity activity) {
        geometry(activity);
        headerAndClick(activity);
        choice(activity);
        arrayAdapter(activity);
        divider(activity);
        stack(activity);
    }

    private static void layout(ListView list, int w, int h) {
        int ws = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY);
        int hs = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY);
        list.measure(ws, hs);
        list.layout(0, 0, w, h);
    }

    private static void plain(ListView list) {
        list.setPadding(0, 0, 0, 0);
        list.setDivider(null);
        list.setSelector(null);
    }

    private static BaseAdapter rows(final Activity activity, final int count, final int height,
            final int[] reused) {
        return new BaseAdapter() {
            public int getCount() { return count; }

            public Object getItem(int position) { return Integer.valueOf(position); }

            public long getItemId(int position) { return position; }

            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView != null && reused != null) reused[0]++;
                View row = convertView == null ? new View(activity) : convertView;
                row.setLayoutParams(new AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT, height));
                return row;
            }
        };
    }

    private static void geometry(Activity activity) {
        final int[] reused = new int[] {0};
        ListView list = new ListView(activity);
        plain(list);
        list.setAdapter(rows(activity, 20, 100, reused));
        layout(list, 300, 400);
        check("child count", list.getChildCount() == 4, list.getChildCount());
        check("first at rest", list.getFirstVisiblePosition() == 0, list.getFirstVisiblePosition());
        check("last at rest", list.getLastVisiblePosition() == 3, list.getLastVisiblePosition());
        check("point row0", list.pointToPosition(10, 10) == 0, list.pointToPosition(10, 10));
        check("point row1", list.pointToPosition(10, 150) == 1, list.pointToPosition(10, 150));
        check("can scroll down", list.canScrollList(1), "top");
        check("cannot scroll up", !list.canScrollList(-1), "top");
        list.scrollListBy(250);
        int top = list.getChildCount() == 0 ? -1 : list.getChildAt(0).getTop();
        check("scroll 250", list.getFirstVisiblePosition() == 2,
                list.getFirstVisiblePosition() + " top " + top);
        check("recycled", reused[0] > 0, reused[0]);
        list.scrollListBy(100000);
        int lastBottom = list.getChildCount() == 0 ? -1
                : list.getChildAt(list.getChildCount() - 1).getBottom();
        check("clamped last", list.getLastVisiblePosition() == 19, list.getLastVisiblePosition());
        check("clamped bottom", Math.abs(lastBottom - 400) <= 2, lastBottom);
        check("clamped first", list.getFirstVisiblePosition() == 16, list.getFirstVisiblePosition());
        list.setSelection(10);
        layout(list, 300, 400);
        check("selection", list.getFirstVisiblePosition() == 10, list.getFirstVisiblePosition());
    }

    private static void headerAndClick(Activity activity) {
        ListView list = new ListView(activity);
        plain(list);
        final View header = new View(activity);
        header.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, 40));
        list.addHeaderView(header);
        list.setAdapter(rows(activity, 3, 100, null));
        check("header count", list.getHeaderViewsCount() == 1, list.getHeaderViewsCount());
        check("count with header", list.getCount() == 4, list.getCount());
        layout(list, 300, 400);
        check("header child", list.getChildAt(0) == header, list.getChildAt(0));
        final int[] clicked = new int[] {-1};
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                clicked[0] = position;
            }
        });
        list.performItemClick(list.getChildAt(1), 1, list.getItemIdAtPosition(1));
        check("item click", clicked[0] == 1, clicked[0]);
    }

    private static void choice(Activity activity) {
        ListView list = new ListView(activity);
        plain(list);
        list.setAdapter(rows(activity, 5, 100, null));
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setItemChecked(1, true);
        list.setItemChecked(2, true);
        check("single choice", !list.isItemChecked(1) && list.isItemChecked(2)
                && list.getCheckedItemCount() == 1, list.getCheckedItemCount());
    }

    private static void arrayAdapter(Activity activity) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, R.layout.row);
        adapter.add("alpha");
        adapter.add("beta");
        adapter.add("banana");
        check("array count", adapter.getCount() == 3, adapter.getCount());
        check("position", adapter.getPosition("beta") == 1, adapter.getPosition("beta"));
        adapter.remove("alpha");
        check("removed", adapter.getCount() == 2 && adapter.getPosition("alpha") < 0, adapter.getCount());
        View row = adapter.getView(0, null, null);
        boolean rowOk = row instanceof TextView && "beta".equals(((TextView) row).getText().toString());
        check("row text", rowOk, row);
        adapter.getFilter().filter("b");
        boolean kept = adapter.getCount() == 2
                && "beta".equals(adapter.getItem(0)) && "banana".equals(adapter.getItem(1));
        check("filter b", kept, adapter.getCount() + " " + adapter.getItem(0));
        adapter.getFilter().filter("z");
        check("filter z", adapter.getCount() == 0, adapter.getCount());
        adapter.getFilter().filter(null);
        check("filter clear", adapter.getCount() == 2, adapter.getCount());

        final int[] notes = new int[] {0};
        ArrayAdapter<String> quiet = new ArrayAdapter<String>(activity, R.layout.row);
        quiet.registerDataSetObserver(new DataSetObserver() {
            public void onChanged() { notes[0]++; }

            public void onInvalidated() { notes[0]++; }
        });
        quiet.setNotifyOnChange(false);
        quiet.add("x");
        quiet.clear();
        check("notify off", notes[0] == 0 && quiet.getCount() == 0, notes[0] + " count " + quiet.getCount());
    }

    private static void divider(Activity activity) {
        ListView list = new ListView(activity);
        plain(list);
        list.setAdapter(rows(activity, 8, 100, null));
        list.setDividerHeight(8);
        layout(list, 300, 400);
        int gap = -1;
        if (list.getChildCount() >= 2) {
            gap = list.getChildAt(1).getTop() - list.getChildAt(0).getBottom();
        }
        check("divider gap", gap == 8, gap);
    }

    private static void stack(Activity activity) {
        ListView list = new ListView(activity);
        plain(list);
        list.setStackFromBottom(true);
        list.setAdapter(rows(activity, 20, 100, null));
        layout(list, 300, 400);
        int bottom = list.getChildCount() == 0 ? -1
                : list.getChildAt(list.getChildCount() - 1).getBottom();
        check("stack last", list.getLastVisiblePosition() == 19, list.getLastVisiblePosition());
        check("stack bottom", Math.abs(bottom - 400) <= 2, bottom);
    }
}
