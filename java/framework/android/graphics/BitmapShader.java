package android.graphics;

public class BitmapShader extends Shader {
    public BitmapShader(Bitmap bitmap, TileMode tileX, TileMode tileY) {
        if (bitmap == null) throw new IllegalArgumentException("Bitmap must be non-null");
        mType = TYPE_BITMAP;
        mBitmap = bitmap;
        mTileX = tileX.nativeInt;
        mTileY = tileY.nativeInt;
    }

    public int getFilterMode() { return 0; }
    public void setFilterMode(int mode) {}
}
