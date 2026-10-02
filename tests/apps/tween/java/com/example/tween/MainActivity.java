package com.example.tween;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.view.animation.Transformation;
import android.view.animation.TranslateAnimation;
import java.util.List;

/**
 * A slide, a shrink and a fade, plus a rotate that is only evaluated.
 * The slide and the fade run for 6s; the shrink finishes in 400ms and stays.
 */
public class MainActivity extends Activity {
    private static final String TAG = "Tween";

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "TWEEN ok " + name);
        else Log.e(TAG, "TWEEN FAIL " + name + " " + detail);
    }

    private static Animation.AnimationListener ended(final String name) {
        return new Animation.AnimationListener() {
            public void onAnimationStart(Animation animation) {}

            public void onAnimationEnd(Animation animation) { Log.i(TAG, "TWEEN ok " + name); }

            public void onAnimationRepeat(Animation animation) {}
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Animation slide = AnimationUtils.loadAnimation(this, R.anim.slide);
        check("set", slide instanceof AnimationSet, slide.getClass().getName());
        if (slide instanceof AnimationSet) {
            List<Animation> kids = ((AnimationSet) slide).getAnimations();
            boolean oneTranslate = kids.size() == 1 && kids.get(0) instanceof TranslateAnimation;
            check("child", oneTranslate, Integer.valueOf(kids.size()));
        } else {
            check("child", false, slide.getClass().getName());
        }
        check("fillAfter", slide.getFillAfter(), Boolean.valueOf(slide.getFillAfter()));
        check("duration", slide.getDuration() == 6000, Long.valueOf(slide.getDuration()));
        slide.setAnimationListener(ended("slideEnd"));

        Animation grow = AnimationUtils.loadAnimation(this, R.anim.grow);
        check("scale", grow instanceof ScaleAnimation, grow.getClass().getName());
        grow.setAnimationListener(ended("scaleEnd"));

        Animation fade = AnimationUtils.loadAnimation(this, R.anim.fade);
        fade.setAnimationListener(ended("fadeEnd"));

        Animation spin = AnimationUtils.loadAnimation(this, R.anim.spin);
        spin.initialize(100, 100, 100, 100);
        spin.setStartTime(0);
        Transformation tr = new Transformation();
        spin.getTransformation(spin.getDuration(), tr);
        check("spin", !tr.getMatrix().isIdentity(), tr.getMatrix().toShortString());

        View slideView = findViewById(R.id.slide);
        View growView = findViewById(R.id.grow);
        View fadeView = findViewById(R.id.fade);
        slideView.startAnimation(slide);
        growView.startAnimation(grow);
        fadeView.startAnimation(fade);
    }
}
