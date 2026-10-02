package com.example.outline;

import android.app.Activity;
import android.graphics.Outline;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;

/**
 * A square yellow child inside a view clipped to a 48dp round rect.
 * The square's corners should show the page, and the middle should stay yellow.
 */
public class MainActivity extends Activity {
    private static final String TAG = "Outline";

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "CLIP ok " + name);
        else Log.e(TAG, "CLIP FAIL " + name + " " + detail);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final View box = findViewById(R.id.box);
        final float radius = 48f * getResources().getDisplayMetrics().density;
        box.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        box.setClipToOutline(true);
        box.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                if (box.getWidth() < 100) return;
                box.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                Outline outline = new Outline();
                box.getOutlineProvider().getOutline(box, outline);
                check("flag", box.getClipToOutline(), box.getClipToOutline());
                check("radius", outline.getRadius() == radius, outline.getRadius());
                check("canClip", outline.canClip() && !outline.isEmpty(), outline.isEmpty());
                int[] loc = new int[2];
                box.getLocationOnScreen(loc);
                Log.i(TAG, "CLIPPOS " + loc[0] + " " + loc[1] + " " + box.getWidth() + " " + box.getHeight()
                        + " " + Math.round(radius));
            }
        });
    }
}
