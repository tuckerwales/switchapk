package com.example.curves;

import android.app.Activity;
import android.graphics.Path;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.AnimationUtils;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.CycleInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.animation.ObjectAnimator;
import android.widget.ImageView;

/**
 * Slides six squares 240px over 4s. The shot is the halfway frame, where each
 * interpolator sits in a different place. A vector stroke morphs along the same clock.
 */
public class MainActivity extends Activity {
    private static final String TAG = "Curves";

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "CURVES ok " + name);
        else Log.e(TAG, "CURVES FAIL " + name + " " + detail);
    }

    static boolean near(float value, float want) {
        return Math.abs(value - want) < 0.01f;
    }

    private void slide(int id, Interpolator interp) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(findViewById(id), "translationX", 0f, 240f);
        anim.setDuration(4000);
        anim.setInterpolator(interp);
        anim.start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Interpolator over = AnimationUtils.loadInterpolator(this, R.anim.overshoot);
        Interpolator ant = AnimationUtils.loadInterpolator(this, R.anim.anticipate);
        Interpolator bounce = AnimationUtils.loadInterpolator(this, R.anim.bounce);
        Interpolator cycle = AnimationUtils.loadInterpolator(this, R.anim.cycle);
        Interpolator pathXml = AnimationUtils.loadInterpolator(this, R.anim.path);
        Interpolator ao = AnimationUtils.loadInterpolator(this, R.anim.anticipate_overshoot);

        check("over", near(over.getInterpolation(0.5f), new OvershootInterpolator(2f).getInterpolation(0.5f))
                && near(over.getInterpolation(0.5f), 1.125f), Float.valueOf(over.getInterpolation(0.5f)));
        check("ant", near(ant.getInterpolation(0.5f), new AnticipateInterpolator(2f).getInterpolation(0.5f))
                && near(ant.getInterpolation(0.5f), -0.125f), Float.valueOf(ant.getInterpolation(0.5f)));
        check("bounce", near(bounce.getInterpolation(0.5f), new BounceInterpolator().getInterpolation(0.5f))
                && near(bounce.getInterpolation(0.5f), 0.7016f), Float.valueOf(bounce.getInterpolation(0.5f)));
        check("cycleQ", near(cycle.getInterpolation(0.25f), new CycleInterpolator(1f).getInterpolation(0.25f))
                && near(cycle.getInterpolation(0.25f), 1f), Float.valueOf(cycle.getInterpolation(0.25f)));
        check("cycleH", near(cycle.getInterpolation(0.5f), 0f), Float.valueOf(cycle.getInterpolation(0.5f)));

        Path curve = new Path();
        curve.moveTo(0f, 0f);
        curve.quadTo(0f, 1f, 1f, 1f);
        PathInterpolator fromPath = new PathInterpolator(curve);
        PathInterpolator fromCtl = new PathInterpolator(0f, 1f);
        float half = fromCtl.getInterpolation(0.5f);
        check("pathHalf", half > 0.8f && near(half, fromPath.getInterpolation(0.5f)), Float.valueOf(half));
        check("pathEnds", near(fromCtl.getInterpolation(0f), 0f) && near(fromCtl.getInterpolation(1f), 1f),
                Float.valueOf(fromCtl.getInterpolation(1f)));
        check("pathXml", near(pathXml.getInterpolation(0.5f), half), Float.valueOf(pathXml.getInterpolation(0.5f)));

        float aoQuarter = new AnticipateOvershootInterpolator().getInterpolation(0.25f);
        check("ao", near(ao.getInterpolation(0.25f), aoQuarter) && near(aoQuarter, -0.125f),
                Float.valueOf(ao.getInterpolation(0.25f)));

        boolean threw = false;
        try {
            Path bad = new Path();
            bad.moveTo(0f, 0f);
            bad.lineTo(0.8f, 0.2f);
            bad.lineTo(0.2f, 0.5f);
            bad.lineTo(1f, 1f);
            new PathInterpolator(bad);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("reject", threw, Boolean.valueOf(threw));

        slide(R.id.linear, new LinearInterpolator());
        slide(R.id.over, over);
        slide(R.id.ant, ant);
        slide(R.id.bounce, bounce);
        slide(R.id.cycle, cycle);
        slide(R.id.path, pathXml);

        ImageView morph = (ImageView) findViewById(R.id.morph);
        Drawable drawable = morph.getDrawable();
        Animatable avd = drawable instanceof Animatable ? (Animatable) drawable : null;
        if (avd != null) avd.start();
        check("avd", avd != null && avd.isRunning(), avd == null ? "null" : Boolean.valueOf(avd.isRunning()));
    }
}
