package com.example.toolbar;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toolbar;

public class MainActivity extends Activity {
    private TextView mStatus;

    private void status(String s) {
        mStatus.setText(s);
        Log.i("TB", s);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        mStatus = (TextView) findViewById(R.id.status);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            status("item " + item.getTitle());
            return true;
        });
        toolbar.setNavigationOnClickListener(v -> status("navigation"));
    }
}
