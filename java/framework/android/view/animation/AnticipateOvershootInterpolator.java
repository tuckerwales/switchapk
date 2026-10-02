package android.view.animation;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

/**
 * Pulls back, then overshoots. The stored tension is {@code tension * extraTension}.
 * The defaults are 2 and 1.5, so the no-arg constructor uses 3.
 */
public class AnticipateOvershootInterpolator extends BaseInterpolator {
    private final float mTension;

    public AnticipateOvershootInterpolator() {
        mTension = 2.0f * 1.5f;
    }

    public AnticipateOvershootInterpolator(float tension) {
        mTension = tension * 1.5f;
    }

    public AnticipateOvershootInterpolator(float tension, float extraTension) {
        mTension = tension * extraTension;
    }

    public AnticipateOvershootInterpolator(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs,
                new int[] {android.R.attr.tension, android.R.attr.extraTension});
        mTension = a.getFloat(0, 2.0f) * a.getFloat(1, 1.5f);
        a.recycle();
    }

    private static float a(float t, float s) {
        return t * t * ((s + 1) * t - s);
    }

    private static float o(float t, float s) {
        return t * t * ((s + 1) * t + s);
    }

    public float getInterpolation(float t) {
        if (t < 0.5f) return 0.5f * a(t * 2.0f, mTension);
        return 0.5f * (o(t * 2.0f - 2.0f, mTension) + 2.0f);
    }
}
