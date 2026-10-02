package com.example.tabs;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** A normal full-screen activity started by an embedded one; returns a value. */
public class PickActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pick);
        findViewById(R.id.ok).setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra("value", 7));
            finish();
        });
    }
}
