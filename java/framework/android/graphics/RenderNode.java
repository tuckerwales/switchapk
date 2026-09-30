package android.graphics;

/** Display lists are emulated by recording into a Picture. */
public final class RenderNode {
    private final String mName;
    private Picture mPicture;
    private final Rect mPosition = new Rect();
    private float mAlpha = 1f, mTranslationX, mTranslationY, mScaleX = 1f, mScaleY = 1f, mRotation, mElevation;

    public RenderNode(String name) { mName = name; }

    public RecordingCanvas beginRecording(int width, int height) {
        mPicture = new Picture();
        return new RecordingCanvas(mPicture.beginRecording(width, height));
    }

    public RecordingCanvas beginRecording() { return beginRecording(mPosition.width(), mPosition.height()); }
    public void endRecording() { if (mPicture != null) mPicture.endRecording(); }
    public boolean hasDisplayList() { return mPicture != null; }
    public void discardDisplayList() { mPicture = null; }
    public boolean setPosition(int left, int top, int right, int bottom) { mPosition.set(left, top, right, bottom); return true; }
    public boolean setPosition(Rect position) { mPosition.set(position); return true; }
    public int getLeft() { return mPosition.left; }
    public int getTop() { return mPosition.top; }
    public int getRight() { return mPosition.right; }
    public int getBottom() { return mPosition.bottom; }
    public int getWidth() { return mPosition.width(); }
    public int getHeight() { return mPosition.height(); }
    public boolean setAlpha(float alpha) { mAlpha = alpha; return true; }
    public float getAlpha() { return mAlpha; }
    public boolean setTranslationX(float v) { mTranslationX = v; return true; }
    public boolean setTranslationY(float v) { mTranslationY = v; return true; }
    public boolean setScaleX(float v) { mScaleX = v; return true; }
    public boolean setScaleY(float v) { mScaleY = v; return true; }
    public boolean setRotationZ(float v) { mRotation = v; return true; }
    public boolean setElevation(float v) { mElevation = v; return true; }
    public float getElevation() { return mElevation; }
    public boolean setClipToBounds(boolean clip) { return true; }
    public boolean setOutline(Outline outline) { return true; }
    public boolean setHasOverlappingRendering(boolean b) { return true; }
    public boolean setUseCompositingLayer(boolean forceToLayer, Paint paint) { return true; }
    public long getUniqueId() { return System.identityHashCode(this); }

    void drawInto(Canvas canvas) {
        if (mPicture == null) return;
        canvas.save();
        canvas.translate(mPosition.left + mTranslationX, mPosition.top + mTranslationY);
        canvas.scale(mScaleX, mScaleY);
        canvas.rotate(mRotation);
        if (mAlpha < 1f) canvas.saveLayerAlpha(null, (int) (mAlpha * 255));
        mPicture.draw(canvas);
        canvas.restore();
    }
}
