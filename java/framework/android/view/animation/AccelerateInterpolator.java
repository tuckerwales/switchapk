package android.view.animation;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

public class AccelerateInterpolator extends BaseInterpolator {
    private final float mFactor;
    private final double mDoubleFactor;

    public AccelerateInterpolator() {
        mFactor = 1.0f;
        mDoubleFactor = 2.0;
    }

    public AccelerateInterpolator(float factor) {
        mFactor = factor;
        mDoubleFactor = 2 * mFactor;
    }

    public AccelerateInterpolator(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, new int[] {android.R.attr.factor});
        mFactor = a.getFloat(0, 1.0f);
        mDoubleFactor = 2 * mFactor;
        a.recycle();
    }

    public float getInterpolation(float input) {
        if (mFactor == 1.0f) return input * input;
        return (float) Math.pow(input, mDoubleFactor);
    }
}
