package android.graphics;

/**
 * Display lists are emulated by recording into a Picture. The node's properties follow Android's RenderNode
 * (RenderProperties in hwui): position, a transform about the pivot (2D, or 3D through a Camera when rotated about
 * X or Y), alpha, and clipping to bounds, a clip rect or the outline. Jetpack Compose draws every graphics layer
 * through one of these. Elevation shadows, projection and render effects are recorded but not drawn.
 */
public final class RenderNode {
    private final String mName;
    private Picture mPicture;
    private final Rect mPosition = new Rect();
    private float mAlpha = 1f;
    private float mTranslationX, mTranslationY, mTranslationZ, mElevation;
    private float mScaleX = 1f, mScaleY = 1f;
    private float mRotationX, mRotationY, mRotationZ;
    private float mPivotX, mPivotY;
    private boolean mPivotExplicitlySet;
    private float mCameraDistance = 8f; /* inches, as hwui's default camera at z = -8 */
    private boolean mClipToBounds = true;
    private boolean mClipToOutline;
    private Rect mClipRect;
    private final Outline mOutline = new Outline();
    private boolean mHasOverlappingRendering = true;
    private boolean mUseCompositingLayer;
    private Paint mLayerPaint;
    private boolean mProjectBackwards, mProjectionReceiver;
    private boolean mForceDarkAllowed = true;
    private int mAmbientShadowColor = Color.BLACK, mSpotShadowColor = Color.BLACK;
    private RenderEffect mRenderEffect;

    private final Matrix mMatrix = new Matrix();
    private boolean mMatrixDirty = true;
    private Camera mCamera;

    public RenderNode(String name) { mName = name; }

    public RecordingCanvas beginRecording(int width, int height) {
        mPicture = new Picture();
        return new RecordingCanvas(mPicture.beginRecording(width, height));
    }

    public RecordingCanvas beginRecording() { return beginRecording(mPosition.width(), mPosition.height()); }
    public void endRecording() { if (mPicture != null) mPicture.endRecording(); }
    public boolean hasDisplayList() { return mPicture != null; }
    public void discardDisplayList() { mPicture = null; }
    public long computeApproximateMemoryUsage() { return mPicture != null ? 1024 : 0; }
    public long getUniqueId() { return System.identityHashCode(this); }

    // ---- position

    public boolean setPosition(int left, int top, int right, int bottom) {
        if (mPosition.left == left && mPosition.top == top && mPosition.right == right && mPosition.bottom == bottom) {
            return false;
        }
        mPosition.set(left, top, right, bottom);
        mMatrixDirty = true;
        return true;
    }

    public boolean setPosition(Rect position) {
        return setPosition(position.left, position.top, position.right, position.bottom);
    }

    public boolean offsetLeftAndRight(int offset) {
        if (offset == 0) return false;
        mPosition.offset(offset, 0);
        return true;
    }

    public boolean offsetTopAndBottom(int offset) {
        if (offset == 0) return false;
        mPosition.offset(0, offset);
        return true;
    }

    public int getLeft() { return mPosition.left; }
    public int getTop() { return mPosition.top; }
    public int getRight() { return mPosition.right; }
    public int getBottom() { return mPosition.bottom; }
    public int getWidth() { return mPosition.width(); }
    public int getHeight() { return mPosition.height(); }

    // ---- transform

    private boolean setTransform(float old, float value) {
        if (old == value) return false;
        mMatrixDirty = true;
        return true;
    }

    public boolean setTranslationX(float v) { boolean c = setTransform(mTranslationX, v); mTranslationX = v; return c; }
    public boolean setTranslationY(float v) { boolean c = setTransform(mTranslationY, v); mTranslationY = v; return c; }
    public boolean setTranslationZ(float v) { boolean c = mTranslationZ != v; mTranslationZ = v; return c; }
    public boolean setScaleX(float v) { boolean c = setTransform(mScaleX, v); mScaleX = v; return c; }
    public boolean setScaleY(float v) { boolean c = setTransform(mScaleY, v); mScaleY = v; return c; }
    public boolean setRotationX(float v) { boolean c = setTransform(mRotationX, v); mRotationX = v; return c; }
    public boolean setRotationY(float v) { boolean c = setTransform(mRotationY, v); mRotationY = v; return c; }
    public boolean setRotationZ(float v) { boolean c = setTransform(mRotationZ, v); mRotationZ = v; return c; }

    public boolean setPivotX(float v) {
        boolean c = !mPivotExplicitlySet || mPivotX != v;
        mPivotExplicitlySet = true;
        mPivotX = v;
        mMatrixDirty |= c;
        return c;
    }

    public boolean setPivotY(float v) {
        boolean c = !mPivotExplicitlySet || mPivotY != v;
        mPivotExplicitlySet = true;
        mPivotY = v;
        mMatrixDirty |= c;
        return c;
    }

    public boolean resetPivot() {
        boolean c = mPivotExplicitlySet;
        mPivotExplicitlySet = false;
        mMatrixDirty = true;
        return c;
    }

    public boolean isPivotExplicitlySet() { return mPivotExplicitlySet; }

    public boolean setCameraDistance(float distance) {
        if (!Float.isFinite(distance) || distance < 0.0f) {
            throw new IllegalArgumentException("distance must be finite & positive, given=" + distance);
        }
        boolean c = setTransform(mCameraDistance, distance);
        mCameraDistance = distance;
        return c;
    }

    public float getTranslationX() { return mTranslationX; }
    public float getTranslationY() { return mTranslationY; }
    public float getTranslationZ() { return mTranslationZ; }
    public float getScaleX() { return mScaleX; }
    public float getScaleY() { return mScaleY; }
    public float getRotationX() { return mRotationX; }
    public float getRotationY() { return mRotationY; }
    public float getRotationZ() { return mRotationZ; }
    public float getCameraDistance() { return mCameraDistance; }

    public float getPivotX() { return mPivotExplicitlySet ? mPivotX : mPosition.width() / 2f; }
    public float getPivotY() { return mPivotExplicitlySet ? mPivotY : mPosition.height() / 2f; }

    /** The transform from this node's coordinates to its parent's, after its left/top offset (as hwui). */
    private Matrix transform() {
        if (!mMatrixDirty) return mMatrix;
        final float px = getPivotX(), py = getPivotY();
        final Matrix m = mMatrix;
        if (mRotationX == 0 && mRotationY == 0) {
            m.setTranslate(mTranslationX, mTranslationY);
            m.preRotate(mRotationZ, px, py);
            m.preScale(mScaleX, mScaleY, px, py);
        } else {
            if (mCamera == null) mCamera = new Camera();
            Matrix tmp = new Matrix();
            mCamera.save();
            mCamera.setLocation(0, 0, -mCameraDistance);
            mCamera.rotateX(mRotationX);
            mCamera.rotateY(mRotationY);
            mCamera.rotateZ(-mRotationZ);
            mCamera.getMatrix(tmp);
            mCamera.restore();
            tmp.preTranslate(-px, -py);
            tmp.postTranslate(px + mTranslationX, py + mTranslationY);
            m.set(tmp);
            m.preScale(mScaleX, mScaleY, px, py);
        }
        mMatrixDirty = false;
        return m;
    }

    public boolean hasIdentityMatrix() { return transform().isIdentity(); }

    public void getMatrix(Matrix outMatrix) { outMatrix.set(transform()); }

    public void getInverseMatrix(Matrix outMatrix) {
        if (!transform().invert(outMatrix)) outMatrix.reset();
    }

    // ---- appearance

    public boolean setAlpha(float alpha) { boolean c = mAlpha != alpha; mAlpha = alpha; return c; }
    public float getAlpha() { return mAlpha; }
    public boolean setElevation(float v) { boolean c = mElevation != v; mElevation = v; return c; }
    public float getElevation() { return mElevation; }

    public boolean setHasOverlappingRendering(boolean b) {
        boolean c = mHasOverlappingRendering != b;
        mHasOverlappingRendering = b;
        return c;
    }

    public boolean hasOverlappingRendering() { return mHasOverlappingRendering; }

    public boolean setUseCompositingLayer(boolean forceToLayer, Paint paint) {
        mUseCompositingLayer = forceToLayer;
        mLayerPaint = paint;
        return true;
    }

    public boolean getUseCompositingLayer() { return mUseCompositingLayer; }

    public boolean setClipToBounds(boolean clip) {
        boolean c = mClipToBounds != clip;
        mClipToBounds = clip;
        return c;
    }

    public boolean getClipToBounds() { return mClipToBounds; }

    public boolean setClipRect(Rect rect) {
        if (rect == null) {
            boolean c = mClipRect != null;
            mClipRect = null;
            return c;
        }
        mClipRect = new Rect(rect);
        return true;
    }

    public boolean setOutline(Outline outline) {
        if (outline == null) mOutline.setEmpty();
        else mOutline.set(outline);
        return true;
    }

    public boolean setClipToOutline(boolean clipToOutline) {
        boolean c = mClipToOutline != clipToOutline;
        mClipToOutline = clipToOutline;
        return c;
    }

    public boolean getClipToOutline() { return mClipToOutline; }

    public boolean hasShadow() {
        return mElevation + mTranslationZ > 0 && !mOutline.isEmpty() && mOutline.getAlpha() > 0;
    }

    public boolean setSpotShadowColor(int color) { boolean c = mSpotShadowColor != color; mSpotShadowColor = color; return c; }
    public int getSpotShadowColor() { return mSpotShadowColor; }
    public boolean setAmbientShadowColor(int color) { boolean c = mAmbientShadowColor != color; mAmbientShadowColor = color; return c; }
    public int getAmbientShadowColor() { return mAmbientShadowColor; }
    public boolean setProjectBackwards(boolean b) { boolean c = mProjectBackwards != b; mProjectBackwards = b; return c; }
    public boolean setProjectionReceiver(boolean b) { boolean c = mProjectionReceiver != b; mProjectionReceiver = b; return c; }
    public boolean setForceDarkAllowed(boolean allow) { boolean c = mForceDarkAllowed != allow; mForceDarkAllowed = allow; return c; }
    public boolean isForceDarkAllowed() { return mForceDarkAllowed; }
    public boolean setRenderEffect(RenderEffect effect) { boolean c = mRenderEffect != effect; mRenderEffect = effect; return c; }

    // ---- drawing

    void drawInto(Canvas canvas) {
        if (mPicture == null || mAlpha <= 0f) return;
        int save = canvas.save();
        canvas.translate(mPosition.left, mPosition.top);
        canvas.concat(transform());
        final int w = mPosition.width(), h = mPosition.height();
        if (mClipToBounds) canvas.clipRect(0, 0, w, h);
        if (mClipRect != null) canvas.clipRect(mClipRect);
        if (mClipToOutline && !mOutline.isEmpty()) {
            if (mOutline.mMode == Outline.MODE_PATH && mOutline.mPath != null) {
                canvas.clipPath(mOutline.mPath);
            } else if (mOutline.mRadius > 0) {
                Path p = new Path();
                p.addRoundRect(new RectF(mOutline.mRect), mOutline.mRadius, mOutline.mRadius, Path.Direction.CW);
                canvas.clipPath(p);
            } else {
                canvas.clipRect(mOutline.mRect);
            }
        }
        if (mUseCompositingLayer && mLayerPaint != null) {
            Paint p = new Paint(mLayerPaint);
            p.setAlpha(Math.round(p.getAlpha() * mAlpha));
            canvas.saveLayer(0, 0, w, h, p);
        } else if (mAlpha < 1f) {
            canvas.saveLayerAlpha(0, 0, w, h, Math.round(mAlpha * 255));
        }
        if (mRenderEffect != null) {
            float[] offset = new float[2];
            java.util.ArrayList<ColorFilter> filters = new java.util.ArrayList<ColorFilter>();
            mRenderEffect.collect(offset, filters);
            canvas.translate(offset[0], offset[1]);
            for (ColorFilter f : filters) {
                Paint p = new Paint();
                p.setColorFilter(f);
                canvas.saveLayer(-offset[0], -offset[1], w - offset[0], h - offset[1], p);
            }
        }
        mPicture.draw(canvas);
        canvas.restoreToCount(save);
    }
}
