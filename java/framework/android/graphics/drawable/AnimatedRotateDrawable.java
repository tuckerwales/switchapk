package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class AnimatedRotateDrawable extends DrawableWrapper implements Animatable {
    private float mCurrentDegrees;
    private float mIncrement;
    private boolean mRunning;
    private int mFrameDuration = 150;
    private int mFramesCount = 12;
    private float mPivotX = 0.5f, mPivotY = 0.5f;

    public AnimatedRotateDrawable() { super(null); mIncrement = 360.0f / mFramesCount; }

    private static final int[] ATTRS = {android.R.attr.drawable, android.R.attr.pivotX, android.R.attr.pivotY, android.R.attr.visible};

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final TypedArray a = obtainAttributes(r, theme, attrs, ATTRS);
        inflateDrawableAttr(r, a, 0, theme);
        TypedValue tv = a.peekValue(1);
        if (tv != null) mPivotX = tv.type == TypedValue.TYPE_FRACTION ? tv.getFraction(1f, 1f) : 0.5f;
        tv = a.peekValue(2);
        if (tv != null) mPivotY = tv.type == TypedValue.TYPE_FRACTION ? tv.getFraction(1f, 1f) : 0.5f;
        a.recycle();
        if (getDrawable() == null) inflateChildDrawable(r, parser, attrs, theme);
    }

    private final Runnable mNextFrame = new Runnable() {
        public void run() {
            mCurrentDegrees += mIncrement;
            if (mCurrentDegrees > (360.0f - mIncrement)) mCurrentDegrees = 0.0f;
            invalidateSelf();
            nextFrame();
        }
    };

    private void nextFrame() {
        unscheduleSelf(mNextFrame);
        scheduleSelf(mNextFrame, SystemClock.uptimeMillis() + mFrameDuration);
    }

    @Override
    public void draw(Canvas canvas) {
        final Drawable drawable = getDrawable();
        if (drawable == null) return;
        final Rect bounds = drawable.getBounds();
        final int w = bounds.right - bounds.left;
        final int h = bounds.bottom - bounds.top;
        final float px = w * mPivotX;
        final float py = h * mPivotY;
        final int saveCount = canvas.save();
        canvas.rotate(mCurrentDegrees, px + bounds.left, py + bounds.top);
        drawable.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    public void start() {
        if (!mRunning) {
            mRunning = true;
            nextFrame();
        }
    }

    public void stop() {
        mRunning = false;
        unscheduleSelf(mNextFrame);
    }

    public boolean isRunning() { return mRunning; }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        final boolean changed = super.setVisible(visible, restart);
        if (visible) {
            if (changed || restart) {
                mCurrentDegrees = 0.0f;
                nextFrame();
            }
        } else {
            unscheduleSelf(mNextFrame);
        }
        return changed;
    }

    public void setFramesCount(int framesCount) { mFramesCount = framesCount; mIncrement = 360.0f / framesCount; }
    public void setFramesDuration(int framesDuration) { mFrameDuration = framesDuration; }
}
