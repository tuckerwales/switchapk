package com.example.outline;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.widget.FrameLayout;
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
        checkRenderNodes();
        /* A RenderNode drawn from onDraw, as Compose draws its layers: a 200 px green square scaled by half about
         * its default (center) pivot and clipped to a 40 px round-rect outline. */
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(300, 300);
        lp.leftMargin = 700;
        lp.topMargin = 180;
        ((FrameLayout) box.getParent()).addView(new NodeView(this), lp);
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

    static class NodeView extends View {
        private final RenderNode node = new RenderNode("layer");

        NodeView(Context c) {
            super(c);
            node.setPosition(50, 50, 250, 250);
            RecordingCanvas rc = node.beginRecording();
            rc.drawColor(0xFF4CAF50);
            node.endRecording();
            node.setScaleX(0.5f);
            node.setScaleY(0.5f);
            Outline o = new Outline();
            o.setRoundRect(0, 0, 200, 200, 40);
            node.setOutline(o);
            node.setClipToOutline(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRenderNode(node);
        }
    }

    /** RenderNode's transform must equal a View's with the same properties (Android computes both the same way). */
    private void checkRenderNodes() {
        float[][] cases = {
            /* w, h, tx, ty, sx, sy, rot, rotX, rotY, pivotX (NaN = default), pivotY */
            {100, 200, 0, 0, 2, 1, 90, 0, 0, Float.NaN, Float.NaN},
            {100, 200, 10, -5, 1.5f, 0.5f, 30, 0, 0, 20, 40},
            {120, 80, 3, 4, 1, 1, 0, 25, -15, Float.NaN, Float.NaN},
        };
        int i = 0;
        for (float[] c : cases) {
            RenderNode n = new RenderNode("check");
            n.setPosition(0, 0, (int) c[0], (int) c[1]);
            View v = new View(this);
            v.layout(0, 0, (int) c[0], (int) c[1]);
            n.setTranslationX(c[2]); v.setTranslationX(c[2]);
            n.setTranslationY(c[3]); v.setTranslationY(c[3]);
            n.setScaleX(c[4]); v.setScaleX(c[4]);
            n.setScaleY(c[5]); v.setScaleY(c[5]);
            n.setRotationZ(c[6]); v.setRotation(c[6]);
            n.setRotationX(c[7]); v.setRotationX(c[7]);
            n.setRotationY(c[8]); v.setRotationY(c[8]);
            if (!Float.isNaN(c[9])) {
                n.setPivotX(c[9]); v.setPivotX(c[9]);
                n.setPivotY(c[10]); v.setPivotY(c[10]);
            }
            Matrix nm = new Matrix();
            n.getMatrix(nm);
            float[] a = new float[9], b = new float[9];
            nm.getValues(a);
            v.getMatrix().getValues(b);
            boolean same = true;
            for (int k = 0; k < 9; k++) same &= Math.abs(a[k] - b[k]) < 1e-3f;
            check("node matrix " + i, same, nm + " vs " + v.getMatrix());
            Matrix inv = new Matrix();
            n.getInverseMatrix(inv);
            float[] pt = {7, 11};
            nm.mapPoints(pt);
            inv.mapPoints(pt);
            check("node inverse " + i, Math.abs(pt[0] - 7) < 1e-2f && Math.abs(pt[1] - 11) < 1e-2f, pt[0] + "," + pt[1]);
            i++;
        }
        RenderNode n = new RenderNode("pivot");
        n.setPosition(0, 0, 60, 40);
        check("node default pivot", n.getPivotX() == 30 && n.getPivotY() == 20 && !n.isPivotExplicitlySet(),
                n.getPivotX() + "," + n.getPivotY());
        check("node identity", n.hasIdentityMatrix() && n.getClipToBounds(), n.hasIdentityMatrix());
        boolean threw = false;
        try {
            n.setCameraDistance(-1);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("node camera distance", threw, threw);
    }
}
