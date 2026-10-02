package com.example.prop;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Path;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.LinearInterpolator;

/**
 * A slide and a shrink through ViewPropertyAnimator, and a fade through ObjectAnimator.
 * The slide and the fade run for 6s; the shrink finishes in 400ms and stays.
 */
public class MainActivity extends Activity {
    private static final String TAG = "Prop";

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "PROP ok " + name);
        else Log.e(TAG, "PROP FAIL " + name + " " + detail);
    }

    private static Animator.AnimatorListener ended(final String name) {
        return new Animator.AnimatorListener() {
            public void onAnimationStart(Animator animation) {}

            public void onAnimationEnd(Animator animation) { Log.i(TAG, "PROP ok " + name); }

            public void onAnimationCancel(Animator animation) {}

            public void onAnimationRepeat(Animator animation) {}
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        LinearInterpolator linear = new LinearInterpolator();
        View slideView = findViewById(R.id.slide);
        View growView = findViewById(R.id.grow);
        View fadeView = findViewById(R.id.fade);

        ViewPropertyAnimator slide = slideView.animate().translationX(300f).setDuration(6000).setInterpolator(linear)
                .setListener(ended("slideEnd"));
        check("duration", slide.getDuration() == 6000, Long.valueOf(slide.getDuration()));

        growView.animate().scaleX(0.5f).scaleY(0.5f).setDuration(400).setInterpolator(linear)
                .setListener(ended("scaleEnd"));

        ObjectAnimator fade = ObjectAnimator.ofFloat(fadeView, "alpha", 1f, 0f).setDuration(6000);
        fade.setInterpolator(linear);
        check("name", "alpha".equals(fade.getPropertyName()), fade.getPropertyName());
        check("idle", !fade.isRunning(), Boolean.valueOf(fade.isRunning()));
        fade.addListener(ended("fadeEnd"));
        fade.start();

        ValueAnimator half = ValueAnimator.ofFloat(0f, 100f);
        half.setInterpolator(linear);
        half.setCurrentFraction(0.5f);
        float hv = half.getAnimatedValue() instanceof Float ? ((Float) half.getAnimatedValue()).floatValue() : -1f;
        check("half", Math.abs(hv - 50f) < 0.01f, Float.valueOf(hv));

        Object color = new ArgbEvaluator().evaluate(0.5f, Integer.valueOf(0xFF000000), Integer.valueOf(0xFFFFFFFF));
        int r = color instanceof Integer ? (((Integer) color).intValue() >> 16) & 0xff : -1;
        check("argb", r >= 110 && r <= 145, Integer.valueOf(r));

        Path path = new Path();
        path.moveTo(0f, 0f);
        path.lineTo(100f, 0f);
        Pos pos = new Pos();
        ObjectAnimator along = ObjectAnimator.ofFloat(pos, "x", "y", path);
        along.setInterpolator(linear);
        along.setCurrentFraction(1f);
        check("path", Math.abs(pos.getX() - 100f) < 2f, Float.valueOf(pos.getX()));
    }

    static final class Pos {
        private float x;
        private float y;

        public float getX() { return x; }

        public void setX(float value) { x = value; }

        public float getY() { return y; }

        public void setY(float value) { y = value; }
    }
}
