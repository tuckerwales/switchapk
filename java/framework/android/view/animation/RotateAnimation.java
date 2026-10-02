package android.view.animation;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

/** Rotates a view from one angle to another, around an optional pivot. */
public class RotateAnimation extends Animation {
    private float mFromDegrees;
    private float mToDegrees;
    private int mPivotXType = ABSOLUTE;
    private int mPivotYType = ABSOLUTE;
    private float mPivotXValue;
    private float mPivotYValue;
    private float mPivotX;
    private float mPivotY;

    public RotateAnimation(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.fromDegrees, android.R.attr.toDegrees, android.R.attr.pivotX, android.R.attr.pivotY});
        mFromDegrees = a.getFloat(0, 0f);
        mToDegrees = a.getFloat(1, 0f);
        Description px = Description.parseValue(a.peekValue(2), context);
        Description py = Description.parseValue(a.peekValue(3), context);
        a.recycle();
        mPivotXType = px.type;
        mPivotXValue = px.value;
        mPivotYType = py.type;
        mPivotYValue = py.value;
        initializePivotPoint();
    }

    public RotateAnimation(float fromDegrees, float toDegrees) {
        mFromDegrees = fromDegrees;
        mToDegrees = toDegrees;
        mPivotXType = mPivotYType = ABSOLUTE;
    }

    public RotateAnimation(float fromDegrees, float toDegrees, float pivotX, float pivotY) {
        this(fromDegrees, toDegrees, ABSOLUTE, pivotX, ABSOLUTE, pivotY);
    }

    public RotateAnimation(float fromDegrees, float toDegrees, int pivotXType, float pivotXValue, int pivotYType,
            float pivotYValue) {
        mFromDegrees = fromDegrees;
        mToDegrees = toDegrees;
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
        float degrees = mFromDegrees + ((mToDegrees - mFromDegrees) * interpolatedTime);
        float scale = getScaleFactor();
        if (mPivotX == 0f && mPivotY == 0f) t.getMatrix().setRotate(degrees);
        else t.getMatrix().setRotate(degrees, mPivotX * scale, mPivotY * scale);
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        mPivotX = resolveSize(mPivotXType, mPivotXValue, width, parentWidth);
        mPivotY = resolveSize(mPivotYType, mPivotYValue, height, parentHeight);
    }
}
