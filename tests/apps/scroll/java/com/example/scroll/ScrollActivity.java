package com.example.scroll;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

public class ScrollActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        ScrollChecks.prepare(this);
        View status = findViewById(R.id.status);
        status.setBackgroundColor(ScrollChecks.failures > 0 ? 0xFFF44336 : 0xFF4CAF50);

        ScrollView vertical = (ScrollView) findViewById(R.id.vscroll);
        HorizontalScrollView horizontal = (HorizontalScrollView) findViewById(R.id.hscroll);
        vertical.setPadding(0, 0, 0, 0);
        horizontal.setPadding(0, 0, 0, 0);
        vertical.setVerticalScrollBarEnabled(false);
        vertical.setHorizontalScrollBarEnabled(false);
        horizontal.setVerticalScrollBarEnabled(false);
        horizontal.setHorizontalScrollBarEnabled(false);
        vertical.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            int last = -1;

            public void onScrollChange(View v, int scrollX, int scrollY, int oldX, int oldY) {
                if (Math.abs(scrollY - last) >= 100) {
                    last = scrollY;
                    Log.i(ScrollChecks.TAG, "vscroll " + scrollY);
                }
            }
        });
        horizontal.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            int last = -1;

            public void onScrollChange(View v, int scrollX, int scrollY, int oldX, int oldY) {
                if (Math.abs(scrollX - last) >= 100) {
                    last = scrollX;
                    Log.i(ScrollChecks.TAG, "hscroll " + scrollX);
                }
            }
        });
    }
}
