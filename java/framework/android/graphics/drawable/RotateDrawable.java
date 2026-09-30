package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class RotateDrawable extends DrawableWrapper {
    private static final int MAX_LEVEL = 10000;
    private float mFromDegrees = 0, mToDegrees = 360, mCurrentDegrees;
    private float mPivotX = 0.5f, mPivotY = 0.5f;
    private boolean mPivotXRel = true, mPivotYRel = true;

    public RotateDrawable() { super(null); }

    private static final int[] ATTRS = {android.R.attr.drawable, android.R.attr.fromDegrees, android.R.attr.toDegrees,
            android.R.attr.pivotX, android.R.attr.pivotY, android.R.attr.visible};

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final TypedArray a = obtainAttributes(r, theme, attrs, ATTRS);
        inflateDrawableAttr(r, a, 0, theme);
        mFromDegrees = a.getFloat(1, mFromDegrees);
        mToDegrees = a.getFloat(2, mToDegrees);
        mCurrentDegrees = mFromDegrees;
        TypedValue tv = a.peekValue(3);
        if (tv != null) {
            mPivotXRel = tv.type == TypedValue.TYPE_FRACTION;
            mPivotX = mPivotXRel ? tv.getFraction(1.0f, 1.0f) : tv.getFloat();
        }
        tv = a.peekValue(4);
        if (tv != null) {
            mPivotYRel = tv.type == TypedValue.TYPE_FRACTION;
            mPivotY = mPivotYRel ? tv.getFraction(1.0f, 1.0f) : tv.getFloat();
        }
        a.recycle();
        if (getDrawable() == null) inflateChildDrawable(r, parser, attrs, theme);
    }

    @Override
    public void draw(Canvas canvas) {
        final Drawable d = getDrawable();
        if (d == null) return;
        final Rect bounds = d.getBounds();
        final int w = bounds.right - bounds.left;
        final int h = bounds.bottom - bounds.top;
        final float px = mPivotXRel ? (w * mPivotX) : mPivotX;
        final float py = mPivotYRel ? (h * mPivotY) : mPivotY;
        final int saveCount = canvas.save();
        canvas.rotate(mCurrentDegrees, px + bounds.left, py + bounds.top);
        d.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    public void setFromDegrees(float fromDegrees) { mFromDegrees = fromDegrees; invalidateSelf(); }
    public float getFromDegrees() { return mFromDegrees; }
    public void setToDegrees(float toDegrees) { mToDegrees = toDegrees; invalidateSelf(); }
    public float getToDegrees() { return mToDegrees; }
    public void setPivotX(float pivotX) { mPivotX = pivotX; invalidateSelf(); }
    public float getPivotX() { return mPivotX; }
    public void setPivotXRelative(boolean relative) { mPivotXRel = relative; }
    public boolean isPivotXRelative() { return mPivotXRel; }
    public void setPivotY(float pivotY) { mPivotY = pivotY; invalidateSelf(); }
    public float getPivotY() { return mPivotY; }
    public void setPivotYRelative(boolean relative) { mPivotYRel = relative; }
    public boolean isPivotYRelative() { return mPivotYRel; }

    @Override
    protected boolean onLevelChange(int level) {
        super.onLevelChange(level);
        final float value = level / (float) MAX_LEVEL;
        mCurrentDegrees = mFromDegrees + (mToDegrees - mFromDegrees) * value;
        invalidateSelf();
        return true;
    }
}
