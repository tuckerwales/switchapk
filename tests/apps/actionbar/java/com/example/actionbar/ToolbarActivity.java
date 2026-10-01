package com.example.actionbar;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toolbar;

/** Activity.setActionBar(Toolbar): the options menu and title go to a Toolbar in the layout. */
public class ToolbarActivity extends Activity {
    private TextView mStatus;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.toolbar);
        mStatus = (TextView) findViewById(R.id.status);
        setActionBar((Toolbar) findViewById(R.id.toolbar));
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Log.i("AB", "toolbar title " + getActionBar().getTitle());
        // No decor action bar here, so the window shows the mode in its own context bar.
        findViewById(R.id.mode).setOnClickListener(v -> {
            ActionMode mode = startActionMode(new ActionMode.Callback() {
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    menu.add("Pin");
                    mode.setTitle("Standalone");
                    return true;
                }

                public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }

                public boolean onActionItemClicked(ActionMode mode, MenuItem item) { return false; }

                public void onDestroyActionMode(ActionMode mode) { Log.i("AB", "standalone destroyed"); }
            });
            Log.i("AB", "standalone started " + (mode != null));
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Edit").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        menu.add(0, 2, 0, "Archive");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Log.i("AB", "toolbar home");
            finish();
            return true;
        }
        mStatus.setText("toolbar " + item.getTitle());
        Log.i("AB", "toolbar " + item.getTitle());
        return true;
    }
}
