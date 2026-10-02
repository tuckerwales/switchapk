package com.example.motion;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * State-list press, a staggered layout animation, an animated vector trim,
 * and a layout-transition fade. The script shoots the middle of each.
 */
public class MainActivity extends Activity {
    private static final String TAG = "Motion";

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "MOTION ok " + name);
        else Log.e(TAG, "MOTION FAIL " + name + " " + detail);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        View state = findViewById(R.id.state);
        check("sla", state.getStateListAnimator() != null, state.getStateListAnimator());

        ViewGroup column = (ViewGroup) findViewById(R.id.column);
        LayoutAnimationController lac = column.getLayoutAnimation();
        check("lac", lac != null && Math.abs(lac.getDelay() - 0.5f) < 0.01f,
                lac == null ? "null" : Float.valueOf(lac.getDelay()));

        Animator loaded = AnimatorInflater.loadAnimator(this, R.animator.trim);
        ValueAnimator trim = loaded instanceof ValueAnimator ? (ValueAnimator) loaded : null;
        if (trim != null) {
            trim.setInterpolator(new LinearInterpolator());
            trim.setCurrentFraction(0.5f);
        }
        Object animated = trim != null ? trim.getAnimatedValue() : null;
        float half = animated instanceof Float ? ((Float) animated).floatValue() : -1f;
        check("half", Math.abs(half - 0.5f) < 0.02f, Float.valueOf(half));

        ValueAnimator first = ValueAnimator.ofFloat(0f, 1f);
        ValueAnimator second = ValueAnimator.ofFloat(0f, 1f);
        first.setDuration(400);
        second.setDuration(400);
        AnimatorSet seq = new AnimatorSet();
        seq.playSequentially(first, second);
        check("seq", seq.getChildAnimations().size() == 2 && seq.getTotalDuration() == 800,
                Long.valueOf(seq.getTotalDuration()));

        Animation slide = AnimationUtils.loadAnimation(this, R.anim.slide_in);
        LayoutAnimationController probe = new LayoutAnimationController(slide, 0.5f);
        View row = findViewById(R.id.row1);
        LayoutAnimationController.AnimationParameters params = new LayoutAnimationController.AnimationParameters();
        params.index = 1;
        params.count = 3;
        row.getLayoutParams().layoutAnimationParameters = params;
        Animation staggered = probe.getAnimationForView(row);
        long offset = staggered != null ? staggered.getStartOffset() : -1;
        check("stagger", staggered != null && Math.abs(offset - slide.getDuration() / 2) < 2, Long.valueOf(offset));

        FrameLayout box = (FrameLayout) findViewById(R.id.fade);
        LayoutTransition transition = new LayoutTransition();
        transition.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        transition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        transition.disableTransitionType(LayoutTransition.CHANGING);
        transition.setAnimator(LayoutTransition.APPEARING, ObjectAnimator.ofFloat(null, "alpha", 0f, 1f));
        transition.setDuration(LayoutTransition.APPEARING, 3000);
        transition.setInterpolator(LayoutTransition.APPEARING, new LinearInterpolator());
        box.setLayoutTransition(transition);
        View orange = new View(this);
        orange.setBackgroundColor(0xFFFF6F00);
        box.addView(orange, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        check("fade", transition.getDuration(LayoutTransition.APPEARING) == 3000,
                Long.valueOf(transition.getDuration(LayoutTransition.APPEARING)));

        ImageView bar = (ImageView) findViewById(R.id.bar);
        Drawable drawable = bar.getDrawable();
        Animatable avd = drawable instanceof Animatable ? (Animatable) drawable : null;
        check("avdIdle", avd != null && !avd.isRunning(), avd);
        if (avd != null) avd.start();
        check("avdRun", avd != null && avd.isRunning(), avd == null ? "null" : Boolean.valueOf(avd.isRunning()));
    }
}
