package com.example.search;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/** The searchable activity: shows the query it was started with. */
public class ResultsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.results);
        Intent intent = getIntent();
        String query = intent.getStringExtra(SearchManager.QUERY);
        Log.i(MainActivity.TAG, "results " + intent.getAction() + " " + query + " user="
                + intent.getCharSequenceExtra(SearchManager.USER_QUERY));
        ((TextView) findViewById(R.id.query)).setText("Results for " + query);
    }
}
