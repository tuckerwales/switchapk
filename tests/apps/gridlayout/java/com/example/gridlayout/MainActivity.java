package com.example.gridlayout;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    static final String TAG = "GRID";

    private TextView mDisplay;
    private long mAccumulator;
    private long mEntry;
    private boolean mFresh = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        GridChecks.run(this);
        mDisplay = (TextView) findViewById(R.id.display);
        GridLayout calc = (GridLayout) findViewById(R.id.calc);
        for (int i = 0; i < calc.getChildCount(); i++) {
            final TextView key = (TextView) calc.getChildAt(i);
            if (key == mDisplay) continue;
            key.setOnClickListener(v -> press(key.getText().toString()));
        }
        mDisplay.setOnClickListener(v -> {
            View middle = findViewById(R.id.middle);
            middle.setVisibility(middle.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
        });
        GridLayout weights = (GridLayout) findViewById(R.id.weights);
        weights.getChildAt(0).addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) Log.i(TAG, "weights " + (r - l));
        });
        findViewById(R.id.calc).post(() -> GridChecks.afterLayout(this));
    }

    private void press(String key) {
        switch (key) {
            case "+":
                mAccumulator += mEntry;
                mEntry = 0;
                mFresh = true;
                show(mAccumulator);
                break;
            case "=":
                mAccumulator += mEntry;
                mEntry = 0;
                mFresh = true;
                show(mAccumulator);
                Log.i(TAG, "result " + mAccumulator);
                mAccumulator = 0;
                break;
            case "C":
                mAccumulator = 0;
                mEntry = 0;
                show(0);
                break;
            default:
                mEntry = (mFresh ? 0 : mEntry * 10) + Integer.parseInt(key);
                mFresh = false;
                show(mEntry);
                break;
        }
    }

    private void show(long value) {
        mDisplay.setText(Long.toString(value));
    }
}
