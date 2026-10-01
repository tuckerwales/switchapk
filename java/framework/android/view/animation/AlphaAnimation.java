package android.view.animation;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

public class AlphaAnimation extends Animation {
    private float mFromAlpha;
    private float mToAlpha;

    public AlphaAnimation(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs,
                new int[] {android.R.attr.fromAlpha, android.R.attr.toAlpha});
        mFromAlpha = a.getFloat(0, 1.0f);
        mToAlpha = a.getFloat(1, 1.0f);
        a.recycle();
    }

    public AlphaAnimation(float fromAlpha, float toAlpha) {
        mFromAlpha = fromAlpha;
        mToAlpha = toAlpha;
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        final float alpha = mFromAlpha;
        t.setAlpha(alpha + ((mToAlpha - alpha) * interpolatedTime));
    }

    @Override
    public boolean willChangeTransformationMatrix() { return false; }

    @Override
    public boolean willChangeBounds() { return false; }
}
