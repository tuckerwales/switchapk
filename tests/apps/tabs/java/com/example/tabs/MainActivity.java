package com.example.tabs;

import android.app.TabActivity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.TabHost;
import android.widget.TextView;

/** A TabActivity with a view-id tab, a factory tab and two tabs running embedded activities. */
public class MainActivity extends TabActivity {
    static final String TAG = "TABS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final TabHost host = getTabHost();
        host.addTab(host.newTabSpec("text").setIndicator("Text").setContent(R.id.text_tab));
        host.addTab(host.newTabSpec("factory").setIndicator("Factory").setContent(tag -> {
            TextView v = new TextView(this);
            v.setText("Made by the factory for " + tag);
            v.setGravity(Gravity.CENTER);
            v.setTextSize(24);
            v.setBackgroundColor(0xFF2196F3);
            return v;
        }));
        host.addTab(host.newTabSpec("child")
                .setIndicator("Child", getDrawable(android.R.drawable.ic_menu_info_details))
                .setContent(new Intent(this, ChildActivity.class).putExtra("name", "child")
                        .putExtra("color", 0xFF4CAF50)));
        host.addTab(host.newTabSpec("other").setIndicator("Other")
                .setContent(new Intent(this, ChildActivity.class).putExtra("name", "other")
                        .putExtra("color", 0xFF9C27B0)));
        final TextView status = (TextView) findViewById(R.id.status);
        host.setOnTabChangedListener(tag -> {
            Log.i(TAG, "tab " + tag);
            status.setText("Current tab: " + tag + " (" + host.getCurrentTab() + ")");
        });
        status.setText("Current tab: " + host.getCurrentTabTag());
        status.setTextColor(Color.BLACK);
        if (savedInstanceState == null) TabChecks.run(this);
        else Log.i(TAG, "recreated on tab " + host.getCurrentTabTag());
    }
}
