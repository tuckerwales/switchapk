package com.example.actionbar;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

/** A window decor action bar: title, subtitle, up, action items, overflow, an action mode, hide/show. */
public class MainActivity extends Activity {
    private TextView mStatus;
    private ActionMode mMode;

    void status(String s) {
        mStatus.setText(s);
        Log.i("AB", s);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        mStatus = (TextView) findViewById(R.id.status);
        final ActionBar bar = getActionBar();
        Log.i("AB", "bar " + (bar != null));
        bar.setSubtitle("2 unread");
        bar.setDisplayHomeAsUpEnabled(true);
        bar.addOnMenuVisibilityListener(visible -> Log.i("AB", "menu visible " + visible));
        findViewById(R.id.mode).setOnClickListener(v -> startMode());
        final Button toggle = (Button) findViewById(R.id.toggle);
        toggle.setOnClickListener(v -> {
            if (bar.isShowing()) {
                bar.hide();
                toggle.setText("Show bar");
            } else {
                bar.show();
                toggle.setText("Hide bar");
            }
            status("showing " + bar.isShowing());
        });
        findViewById(R.id.next).setOnClickListener(v -> startActivity(new Intent(this, ToolbarActivity.class)));
    }

    private void startMode() {
        mMode = startActionMode(new ActionMode.Callback() {
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.mode, menu);
                mode.setTitle("1 selected");
                return true;
            }

            public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }

            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                status("mode " + item.getTitle());
                mode.finish();
                return true;
            }

            public void onDestroyActionMode(ActionMode mode) {
                Log.i("AB", "mode destroyed");
                mMode = null;
            }
        });
        status("mode started " + (mMode != null));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            status("home");
            return true;
        }
        status("item " + item.getTitle());
        return true;
    }
}
