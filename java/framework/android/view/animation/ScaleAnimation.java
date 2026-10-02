package android.view.animation;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

/** Scales a view between two factors, around an optional pivot. */
public class ScaleAnimation extends Animation {
    private float mFromX;
    private float mToX;
    private float mFromY;
    private float mToY;
    private int mPivotXType = ABSOLUTE;
    private int mPivotYType = ABSOLUTE;
    private float mPivotXValue;
    private float mPivotYValue;
    private float mPivotX;
    private float mPivotY;

    public ScaleAnimation(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.fromXScale, android.R.attr.toXScale, android.R.attr.fromYScale, android.R.attr.toYScale,
                android.R.attr.pivotX, android.R.attr.pivotY});
        mFromX = a.getFloat(0, 0f);
        mToX = a.getFloat(1, 0f);
        mFromY = a.getFloat(2, 0f);
        mToY = a.getFloat(3, 0f);
        Description px = Description.parseValue(a.peekValue(4), context);
        Description py = Description.parseValue(a.peekValue(5), context);
        a.recycle();
        mPivotXType = px.type;
        mPivotXValue = px.value;
        mPivotYType = py.type;
        mPivotYValue = py.value;
        initializePivotPoint();
    }

    public ScaleAnimation(float fromX, float toX, float fromY, float toY) {
        mFromX = fromX;
        mToX = toX;
        mFromY = fromY;
        mToY = toY;
        mPivotXType = mPivotYType = ABSOLUTE;
    }

    public ScaleAnimation(float fromX, float toX, float fromY, float toY, float pivotX, float pivotY) {
        this(fromX, toX, fromY, toY, ABSOLUTE, pivotX, ABSOLUTE, pivotY);
    }

    public ScaleAnimation(float fromX, float toX, float fromY, float toY, int pivotXType, float pivotXValue,
            int pivotYType, float pivotYValue) {
        mFromX = fromX;
        mToX = toX;
        mFromY = fromY;
        mToY = toY;
        mPivotXType = pivotXType;
        mPivotXValue = pivotXValue;
        mPivotYType = pivotYType;
        mPivotYValue = pivotYValue;
        initializePivotPoint();
    }

    private void initializePivotPoint() {
        if (mPivotXType == ABSOLUTE) mPivotX = mPivotXValue;
        if (mPivotYType == ABSOLUTE) mPivotY = mPivotYValue;
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        float sx = 1f;
        float sy = 1f;
        if (mFromX != 1f || mToX != 1f) sx = mFromX + ((mToX - mFromX) * interpolatedTime);
        if (mFromY != 1f || mToY != 1f) sy = mFromY + ((mToY - mFromY) * interpolatedTime);
        if (mPivotX == 0 && mPivotY == 0) t.getMatrix().setScale(sx, sy);
        else t.getMatrix().setScale(sx, sy, getScaleFactor() * mPivotX, getScaleFactor() * mPivotY);
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        mPivotX = resolveSize(mPivotXType, mPivotXValue, width, parentWidth);
        mPivotY = resolveSize(mPivotYType, mPivotYValue, height, parentHeight);
    }
}
