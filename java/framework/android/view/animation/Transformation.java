package android.view.animation;

import android.graphics.Matrix;

/** Port of AOSP Transformation: an alpha and a matrix applied at one point of an animation. */
public class Transformation {
    public static final int TYPE_IDENTITY = 0x0;
    public static final int TYPE_ALPHA = 0x1;
    public static final int TYPE_MATRIX = 0x2;
    public static final int TYPE_BOTH = TYPE_ALPHA | TYPE_MATRIX;

    protected Matrix mMatrix;
    protected float mAlpha;
    protected int mTransformationType;

    public Transformation() { clear(); }

    public void clear() {
        if (mMatrix == null) mMatrix = new Matrix();
        else mMatrix.reset();
        mAlpha = 1.0f;
        mTransformationType = TYPE_BOTH;
    }

    public int getTransformationType() { return mTransformationType; }

    public void setTransformationType(int transformationType) { mTransformationType = transformationType; }

    public void set(Transformation t) {
        mAlpha = t.getAlpha();
        mMatrix.set(t.getMatrix());
        mTransformationType = t.getTransformationType();
    }

    public void compose(Transformation t) {
        mAlpha *= t.getAlpha();
        mMatrix.preConcat(t.getMatrix());
    }

    public Matrix getMatrix() { return mMatrix; }

    public void setAlpha(float alpha) { mAlpha = alpha; }

    public float getAlpha() { return mAlpha; }

    @Override
    public String toString() { return "Transformation" + toShortString(); }

    public String toShortString() { return "{alpha=" + mAlpha + " matrix=" + mMatrix.toShortString() + "}"; }
}
