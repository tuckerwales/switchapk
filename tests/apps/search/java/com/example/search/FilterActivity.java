package com.example.search;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

/** A SearchView filtering a list in place, and an iconified SearchView. */
public class FilterActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.filter);
        final String tag = MainActivity.TAG;

        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, SuggestProvider.FRUIT);
        ((ListView) findViewById(R.id.list)).setAdapter(adapter);

        final TextView status = (TextView) findViewById(R.id.status);
        SearchView filter = (SearchView) findViewById(R.id.filter);
        filter.setSubmitButtonEnabled(true);
        filter.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.i(tag, "submit " + query);
                status.setText("Submitted " + query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.i(tag, "change " + newText);
                adapter.getFilter().filter(newText);
                return true;
            }
        });

        SearchView collapsed = (SearchView) findViewById(R.id.collapsed);
        collapsed.setOnSearchClickListener(v -> Log.i(tag, "expanded"));
        collapsed.setOnCloseListener(() -> {
            Log.i(tag, "closed");
            return false;
        });
    }
}
