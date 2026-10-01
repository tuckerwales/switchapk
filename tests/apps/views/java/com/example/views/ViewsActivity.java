package com.example.views;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class ViewsActivity extends Activity {
    private static final String TAG = "ViewsTest";
    private View mStatus;
    private FrameLayout mDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mStatus = findViewById(R.id.status);
        ViewStub stub = findViewById(R.id.stub);
        View inflated = stub.inflate();
        Log.i(TAG, "stub inflated id ok: " + (inflated.getId() == R.id.stub_view)
                + ", include id ok: " + (findViewById(R.id.corner_included) != null));
        tile(R.id.tile1, 0xFFF44336);
        tile(R.id.tile2, 0xFFFFEB3B);
        tile(R.id.tile3, 0xFF9C27B0);
        final TileView tile4 = findViewById(R.id.tile4);
        tile4.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                tile4.clicks++;
                tile4.invalidate();
                showDialog();
            }
        });
        findViewById(R.id.tile1).setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                Log.i(TAG, "long click");
                mStatus.setBackgroundColor(0xFF000000);
                return true;
            }
        });
    }

    private void tile(int id, final int color) {
        final TileView t = findViewById(id);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.i(TAG, "click " + getResources().getResourceEntryName(v.getId()));
                t.clicks++;
                t.invalidate();
                mStatus.setBackgroundColor(color);
            }
        });
    }

    private void showDialog() {
        if (mDialog != null) return;
        final float d = getResources().getDisplayMetrics().density;
        mDialog = new FrameLayout(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) dismissDialog();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        mDialog.setBackgroundColor(0xFFFFFFFF);
        TileView close = new TileView(this);
        close.setBackgroundResource(R.drawable.tile_bg);
        close.setFocusable(true);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dismissDialog(); }
        });
        mDialog.addView(close, new FrameLayout.LayoutParams((int) (80 * d), (int) (40 * d), Gravity.CENTER));
        mDialog.setMinimumWidth((int) (200 * d));
        mDialog.setMinimumHeight((int) (120 * d));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        lp.dimAmount = 0.5f;
        getWindowManager().addView(mDialog, lp);
        Log.i(TAG, "dialog shown");
    }

    private void dismissDialog() {
        if (mDialog == null) return;
        getWindowManager().removeView(mDialog);
        mDialog = null;
        Log.i(TAG, "dialog dismissed");
    }
}
