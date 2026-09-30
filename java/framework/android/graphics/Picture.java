package android.graphics;

/** A picture recorded into an offscreen bitmap. */
public class Picture {
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private int mWidth, mHeight;

    public Picture() {}
    public Picture(Picture src) {
        if (src.mBitmap != null) mBitmap = src.mBitmap.copy(Bitmap.Config.ARGB_8888, true);
        mWidth = src.mWidth;
        mHeight = src.mHeight;
    }

    public Canvas beginRecording(int width, int height) {
        mWidth = width;
        mHeight = height;
        mBitmap = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        mBitmap.setDensity(Bitmap.DENSITY_NONE);
        mCanvas = new Canvas(mBitmap);
        mCanvas.setDensity(Bitmap.DENSITY_NONE);
        return mCanvas;
    }

    public void endRecording() { mCanvas = null; }
    public int getWidth() { return mWidth; }
    public int getHeight() { return mHeight; }
    public boolean requiresHardwareAcceleration() { return false; }

    public void draw(Canvas canvas) {
        if (mBitmap != null) canvas.drawBitmap(mBitmap, null, new RectF(0, 0, mWidth, mHeight), null);
    }

    Bitmap toBitmap() {
        return mBitmap != null ? mBitmap.copy(Bitmap.Config.ARGB_8888, false) : Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    }
}
