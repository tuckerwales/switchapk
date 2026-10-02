package com.example.search;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SearchView;

public class MainActivity extends Activity {
    static final String TAG = "SEARCH";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        SearchChecks.run(this);
        findViewById(R.id.dialog).setOnClickListener(v -> onSearchRequested());
        findViewById(R.id.filter).setOnClickListener(v -> startActivity(new Intent(this, FilterActivity.class)));
        SearchManager sm = (SearchManager) getSystemService(SEARCH_SERVICE);
        sm.setOnDismissListener(() -> Log.i(TAG, "dialog dismissed"));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        MenuItem item = menu.findItem(R.id.search);
        SearchView view = (SearchView) item.getActionView();
        SearchManager sm = (SearchManager) getSystemService(SEARCH_SERVICE);
        view.setSearchableInfo(sm.getSearchableInfo(getComponentName()));
        item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                Log.i(TAG, "action view expanded");
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                Log.i(TAG, "action view collapsed");
                return true;
            }
        });
        return true;
    }
}
