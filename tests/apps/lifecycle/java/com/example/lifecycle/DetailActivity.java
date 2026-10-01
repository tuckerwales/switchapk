package com.example.lifecycle;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Returns item * 2 on Done; Next relaunches itself single-top with item + 1. */
public class DetailActivity extends Activity {
    static final int[] ITEM_COLORS = { 0xFF43A047, 0xFF00ACC1 };

    private int mItem;
    private View mBand;
    private TextView mText;

    private static void log(String event) { LifeApp.log("Detail." + event); }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        log("onCreate " + (state != null ? "restored" : "fresh") + " caller=" + getCallingActivity());
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        mBand = new View(this);
        root.addView(mBand, new LinearLayout.LayoutParams(-1, 120));
        mText = new TextView(this);
        mText.setTextSize(18);
        root.addView(mText);
        Button done = new Button(this);
        done.setText("Done");
        done.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setResult(RESULT_OK, new Intent().putExtra("picked", mItem * 2));
                finish();
            }
        });
        root.addView(done, new LinearLayout.LayoutParams(-2, -2));
        Button next = new Button(this);
        next.setText("Next");
        next.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(DetailActivity.this, DetailActivity.class);
                intent.putExtra("item", mItem + 1);
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
        });
        root.addView(next, new LinearLayout.LayoutParams(-2, -2));
        setContentView(root);
        show(getIntent());
    }

    private void show(Intent intent) {
        mItem = intent.getIntExtra("item", 0);
        mBand.setBackgroundColor(ITEM_COLORS[(mItem - 3) & 1]);
        mText.setText("item " + mItem);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        log("onNewIntent item=" + intent.getIntExtra("item", 0));
        setIntent(intent);
        show(intent);
    }

    @Override
    protected void onStart() { super.onStart(); log("onStart"); }

    @Override
    protected void onResume() { super.onResume(); log("onResume"); }

    @Override
    protected void onPause() { super.onPause(); log("onPause"); }

    @Override
    protected void onStop() { super.onStop(); log("onStop"); }

    @Override
    protected void onDestroy() { super.onDestroy(); log("onDestroy"); }
}
