package com.example.tabs;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

/** Runs embedded in a tab; logs its lifecycle and starts PickActivity for a result. */
public class ChildActivity extends Activity {
    static final int PICK = 7;
    private String mName;
    private boolean mChecked;
    private int mPicked = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mName = getIntent().getStringExtra("name");
        if (savedInstanceState != null) mPicked = savedInstanceState.getInt("picked");
        // Android saves an embedded activity's state only while it is resumed, and an API 28+
        // group saves after stopping, so after a configuration change this is never restored.
        Log.i(MainActivity.TAG, mName + " create " + (savedInstanceState != null ? "restored " + mPicked : "new")
                + " " + getLastNonConfigurationInstance());
        setContentView(R.layout.child);
        if (mPicked >= 0) ((TextView) findViewById(R.id.result)).setText("Picked " + mPicked);
        findViewById(R.id.child_root).setBackgroundColor(getIntent().getIntExtra("color", 0));
        ((TextView) findViewById(R.id.label)).setText("Embedded activity " + mName);
        findViewById(R.id.pick).setOnClickListener(v ->
                startActivityForResult(new Intent(this, PickActivity.class), PICK));
        TabChecks.child(this, mName);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(MainActivity.TAG, mName + " start");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(MainActivity.TAG, mName + " resume");
        if (!mChecked) {
            mChecked = true;
            // The group records this activity as current only after its first onResume returns.
            new Handler(Looper.getMainLooper()).post(() -> TabChecks.childResumed(this, mName));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(MainActivity.TAG, mName + " pause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(MainActivity.TAG, mName + " stop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(MainActivity.TAG, mName + " destroy");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        int value = data != null ? data.getIntExtra("value", -1) : -1;
        Log.i(MainActivity.TAG, mName + " result " + requestCode + " " + (resultCode == RESULT_OK) + " " + value);
        mPicked = value;
        ((TextView) findViewById(R.id.result)).setText("Picked " + value);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("picked", mPicked);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return "kept " + mName;
    }
}
