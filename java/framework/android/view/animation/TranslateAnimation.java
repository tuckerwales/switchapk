package android.view.animation;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

/** Moves a view from one offset to another. Offsets are pixels, or fractions of the view or its parent. */
public class TranslateAnimation extends Animation {
    private int mFromXType = ABSOLUTE;
    private int mToXType = ABSOLUTE;
    private int mFromYType = ABSOLUTE;
    private int mToYType = ABSOLUTE;
    private float mFromXValue;
    private float mToXValue;
    private float mFromYValue;
    private float mToYValue;
    private float mFromXDelta;
    private float mToXDelta;
    private float mFromYDelta;
    private float mToYDelta;

    public TranslateAnimation(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.fromXDelta, android.R.attr.toXDelta,
                android.R.attr.fromYDelta, android.R.attr.toYDelta});
        Description fromX = Description.parseValue(a.peekValue(0), context);
        Description toX = Description.parseValue(a.peekValue(1), context);
        Description fromY = Description.parseValue(a.peekValue(2), context);
        Description toY = Description.parseValue(a.peekValue(3), context);
        a.recycle();
        mFromXType = fromX.type;
        mFromXValue = fromX.value;
        mToXType = toX.type;
        mToXValue = toX.value;
        mFromYType = fromY.type;
        mFromYValue = fromY.value;
        mToYType = toY.type;
        mToYValue = toY.value;
    }

    public TranslateAnimation(float fromXDelta, float toXDelta, float fromYDelta, float toYDelta) {
        mFromXValue = fromXDelta;
        mToXValue = toXDelta;
        mFromYValue = fromYDelta;
        mToYValue = toYDelta;
        mFromXType = mToXType = mFromYType = mToYType = ABSOLUTE;
    }

    public TranslateAnimation(int fromXType, float fromXValue, int toXType, float toXValue, int fromYType,
            float fromYValue, int toYType, float toYValue) {
        mFromXType = fromXType;
        mFromXValue = fromXValue;
        mToXType = toXType;
        mToXValue = toXValue;
        mFromYType = fromYType;
        mFromYValue = fromYValue;
        mToYType = toYType;
        mToYValue = toYValue;
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        float dx = mFromXDelta;
        float dy = mFromYDelta;
        if (mFromXDelta != mToXDelta) dx = mFromXDelta + ((mToXDelta - mFromXDelta) * interpolatedTime);
        if (mFromYDelta != mToYDelta) dy = mFromYDelta + ((mToYDelta - mFromYDelta) * interpolatedTime);
        t.getMatrix().setTranslate(dx, dy);
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        mFromXDelta = resolveSize(mFromXType, mFromXValue, width, parentWidth);
        mToXDelta = resolveSize(mToXType, mToXValue, width, parentWidth);
        mFromYDelta = resolveSize(mFromYType, mFromYValue, height, parentHeight);
        mToYDelta = resolveSize(mToYType, mToYValue, height, parentHeight);
    }
}
