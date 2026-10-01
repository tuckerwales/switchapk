package com.example.grid;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

/** GridView (auto_fit columns, spacing, recycling while scrolling, D-pad), TableLayout and AbsoluteLayout. */
public class MainActivity extends Activity {
    private TextView mStatus;

    void status(String s) {
        mStatus.setText(s);
        Log.i("GRID", s);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        mStatus = (TextView) findViewById(R.id.status);
        final GridView grid = (GridView) findViewById(R.id.grid);
        final float density = getResources().getDisplayMetrics().density;
        grid.setAdapter(new BaseAdapter() {
            public int getCount() { return 50; }

            public Object getItem(int position) { return position; }

            public long getItemId(int position) { return position; }

            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) convertView;
                if (tv == null) {
                    tv = new TextView(MainActivity.this);
                    tv.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (72 * density)));
                    tv.setGravity(Gravity.CENTER);
                    tv.setTextColor(Color.WHITE);
                    tv.setTextSize(20);
                }
                tv.setText(String.valueOf(position));
                tv.setBackgroundColor(position % 2 == 0 ? 0xFF5C6BC0 : 0xFF26A69A);
                return tv;
            }
        });
        grid.setOnItemClickListener((parent, view, position, id) -> status("click " + position));
        grid.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Log.i("GRID", "selected " + position);
            }

            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        grid.post(() -> Log.i("GRID", "columns " + grid.getNumColumns() + " width " + grid.getColumnWidth()
                + " spacing " + grid.getHorizontalSpacing()));
    }
}
