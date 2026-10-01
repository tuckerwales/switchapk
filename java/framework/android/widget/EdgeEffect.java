package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;

/**
 * Edge glow drawn by a scrolling view (AOSP EdgeEffect, the color glow).
 * The caller translates and rotates the canvas so the glow sits on an edge.
 * Stretch-effect types are not pulled in; pull, absorb and a short recede cover the API.
 */
public class EdgeEffect {
    public static final BlendMode DEFAULT_BLEND_MODE = BlendMode.SRC_ATOP;

    private static final int RECEDE_MS = 600;
    private static final int[] COLOR_ATTR = {android.R.attr.colorEdgeEffect};

    private static final int STATE_IDLE = 0;
    private static final int STATE_PULL = 1;
    private static final int STATE_RECEDE = 2;

    private final Paint mPaint = new Paint();
    private int mColor;
    private BlendMode mBlendMode = DEFAULT_BLEND_MODE;
    private int mWidth;
    private int mHeight;
    private float mGlow;
    private float mDistance;
    private float mDisplacement = 0.5f;
    private int mState = STATE_IDLE;
    private boolean mFinished = true;
    private long mLastTime;

    public EdgeEffect(Context context) { this(context, null); }

    public EdgeEffect(Context context, AttributeSet attrs) {
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setBlendMode(DEFAULT_BLEND_MODE);
        int themeColor = 0xff666666;
        if (context != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, COLOR_ATTR);
            themeColor = a.getColor(0, themeColor);
            a.recycle();
        }
        setColor(themeColor);
    }

    public void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public boolean isFinished() { return mFinished; }

    public void finish() {
        mFinished = true;
        mState = STATE_IDLE;
        mGlow = 0;
        mDistance = 0;
    }

    public void onPull(float deltaDistance) { onPull(deltaDistance, 0.5f); }

    public void onPull(float deltaDistance, float displacement) {
        onPullDistance(deltaDistance, displacement);
    }

    public float onPullDistance(float deltaDistance, float displacement) {
        float old = mDistance;
        if (deltaDistance == 0) return 0;
        mFinished = false;
        mState = STATE_PULL;
        mLastTime = SystemClock.uptimeMillis();
        mDistance = Math.max(0f, mDistance + deltaDistance);
        if (deltaDistance > 0) mGlow = Math.min(1f, mGlow + deltaDistance);
        else mGlow = Math.max(0f, mGlow + deltaDistance);
        mDisplacement = displacement;
        if (mDistance == 0 && mGlow == 0) finish();
        return mDistance - old;
    }

    public float getDistance() { return mDistance; }

    public void onRelease() {
        mLastTime = SystemClock.uptimeMillis();
        if (mGlow > 0 || mDistance > 0) mState = STATE_RECEDE;
        else finish();
    }

    public void onAbsorb(int velocity) {
        mFinished = false;
        mState = STATE_RECEDE;
        mLastTime = SystemClock.uptimeMillis();
        float strength = Math.min(1f, Math.abs(velocity) / 4000f);
        if (strength < 0.15f) strength = 0.15f;
        mGlow = Math.max(mGlow, strength);
        mDistance = Math.max(mDistance, strength);
    }

    public void setColor(int color) {
        mColor = color;
        mPaint.setColor(color);
    }

    public void setBlendMode(BlendMode blendMode) {
        mBlendMode = blendMode;
        mPaint.setBlendMode(blendMode);
    }

    public int getColor() { return mColor; }

    public BlendMode getBlendMode() { return mBlendMode; }

    public boolean draw(Canvas canvas) {
        decay();
        if (mFinished || mGlow <= 0 || mWidth <= 0 || mHeight <= 0) return false;
        int alpha = (int) (mGlow * 0x66);
        if (alpha < 1) alpha = 1;
        if (alpha > 255) alpha = 255;
        mPaint.setColor((mColor & 0x00ffffff) | (alpha << 24));
        float radius = Math.max(mWidth * 0.5f, mGlow * mHeight);
        float cx = mWidth * Math.max(0f, Math.min(1f, mDisplacement));
        canvas.drawCircle(cx, 0, radius, mPaint);
        return !mFinished;
    }

    public int getMaxHeight() { return (int) (mHeight * 0.5f + 0.5f); }

    private void decay() {
        if (mState != STATE_RECEDE) return;
        long now = SystemClock.uptimeMillis();
        float dt = (now - mLastTime) / (float) RECEDE_MS;
        mLastTime = now;
        if (dt < 0) dt = 0;
        if (dt > 0.25f) dt = 0.25f;
        mGlow -= dt;
        mDistance = Math.max(0f, mDistance - dt);
        if (mGlow <= 0f) finish();
    }
}
