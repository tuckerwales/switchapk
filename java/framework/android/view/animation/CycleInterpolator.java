package android.view.animation;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

/** Repeats a sine wave. One cycle returns 0 at both ends and 1 at the quarter. */
public class CycleInterpolator extends BaseInterpolator {
    private final float mCycles;

    public CycleInterpolator(float cycles) {
        mCycles = cycles;
    }

    public CycleInterpolator(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, new int[] {android.R.attr.cycles});
        mCycles = a.getFloat(0, 1.0f);
        a.recycle();
    }

    public float getInterpolation(float input) {
        return (float) Math.sin(2 * mCycles * Math.PI * input);
    }
}
