package com.example.list;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ListView;

public class ListActivity extends Activity {
    static final int RED = 0xFFE53935;
    static final int BLUE = 0xFF1565C0;
    static final int GREEN = 0xFF4CAF50;
    static final int GOLD = 0xFFFFC107;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        ListChecks.prepare(this);
        View status = findViewById(R.id.status);
        status.setBackgroundColor(ListChecks.failures > 0 ? 0xFFF44336 : GREEN);

        final ListView list = (ListView) findViewById(R.id.list);
        list.setPadding(0, 0, 0, 0);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(null);
        list.setVerticalScrollBarEnabled(false);
        list.setHorizontalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        final float density = getResources().getDisplayMetrics().density;
        final int rowHeight = (int) (160f * density + 0.5f);
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return 12; }

            public Object getItem(int position) { return Integer.valueOf(position); }

            public long getItemId(int position) { return position; }

            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView == null ? new View(ListActivity.this) : convertView;
                row.setLayoutParams(new AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT, rowHeight));
                row.setBackgroundColor(colorFor(position));
                return row;
            }
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            int lastTop = Integer.MIN_VALUE;

            public void onScrollStateChanged(AbsListView view, int scrollState) {}

            public void onScroll(AbsListView view, int first, int visible, int total) {
                int top = view.getChildCount() == 0 ? 0 : view.getChildAt(0).getTop();
                if (lastTop == Integer.MIN_VALUE || Math.abs(top - lastTop) >= 80) {
                    lastTop = top;
                    Log.i(ListChecks.TAG, "first " + first + " top " + top);
                }
            }
        });
    }

    static int colorFor(int position) {
        int band = (position / 2) % 4;
        if (band == 0) return RED;
        if (band == 1) return BLUE;
        if (band == 2) return GREEN;
        return GOLD;
    }
}
