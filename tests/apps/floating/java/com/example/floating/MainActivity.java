package com.example.floating;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

/**
 * Starts a floating action mode on a selection rectangle. The toolbar
 * should sit above that rectangle until Copy is tapped.
 */
public class MainActivity extends Activity {
    private static final String TAG = "Float";
    private boolean mLoggedCopy;
    private boolean mDestroyed;
    private int mRectWidth;

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "FLOAT ok " + name);
        else Log.e(TAG, "FLOAT FAIL " + name + " " + detail);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final View selection = findViewById(R.id.selection);
        final TextView status = findViewById(R.id.status);
        selection.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (selection.getWidth() < 100) return;
                selection.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                ActionMode.Callback2 callback = new ActionMode.Callback2() {
                    @Override
                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                        mode.setTitle("Selection");
                        menu.add(0, 1, 0, "Copy");
                        menu.add(0, 2, 0, "Share");
                        return true;
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }

                    @Override
                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                        if (item.getItemId() == 1 && !mLoggedCopy) {
                            mLoggedCopy = true;
                            status.setText("copied");
                            check("copy", true, null);
                            mode.finish();
                        }
                        return true;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode mode) {
                        mDestroyed = true;
                        check("destroyed", mLoggedCopy, mLoggedCopy);
                    }

                    @Override
                    public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                        outRect.set(0, 0, view.getWidth(), view.getHeight());
                        mRectWidth = outRect.width();
                    }
                };
                ActionMode mode = selection.startActionMode(callback, ActionMode.TYPE_FLOATING);
                check("started", mode != null, mode);
                check("type", mode != null && mode.getType() == ActionMode.TYPE_FLOATING,
                        mode == null ? null : mode.getType());
                check("title", mode != null && "Selection".equals(String.valueOf(mode.getTitle())),
                        mode == null ? null : mode.getTitle());
                MenuItem copy = mode == null ? null : mode.getMenu().findItem(1);
                MenuItem share = mode == null ? null : mode.getMenu().findItem(2);
                check("copy item", copy != null && "Copy".equals(String.valueOf(copy.getTitle())),
                        copy == null ? null : copy.getTitle());
                check("share item", share != null && "Share".equals(String.valueOf(share.getTitle())),
                        share == null ? null : share.getTitle());
                check("rect", mRectWidth == selection.getWidth(), mRectWidth);
                if (mode != null) {
                    mode.hide(ActionMode.DEFAULT_HIDE_DURATION);
                    check("hide", !mDestroyed, mDestroyed);
                    mode.invalidateContentRect();
                } else {
                    check("hide", false, "no mode");
                }
                int[] loc = new int[2];
                selection.getLocationOnScreen(loc);
                Log.i(TAG, "FLOATPOS selection " + loc[0] + " " + loc[1] + " " + selection.getWidth()
                        + " " + selection.getHeight());
            }
        });
    }
}
