package android.graphics;

import java.io.IOException;
import java.io.InputStream;

public final class BitmapRegionDecoder {
    private Bitmap mFull;

    private BitmapRegionDecoder(Bitmap full) { mFull = full; }

    public static BitmapRegionDecoder newInstance(byte[] data, int offset, int length, boolean isShareable) throws IOException {
        Bitmap b = BitmapFactory.decodeByteArray(data, offset, length);
        if (b == null) throw new IOException("Image format not supported");
        return new BitmapRegionDecoder(b);
    }

    public static BitmapRegionDecoder newInstance(byte[] data, int offset, int length) throws IOException { return newInstance(data, offset, length, false); }

    public static BitmapRegionDecoder newInstance(InputStream is, boolean isShareable) throws IOException {
        Bitmap b = BitmapFactory.decodeStream(is);
        if (b == null) throw new IOException("Image format not supported");
        return new BitmapRegionDecoder(b);
    }

    public static BitmapRegionDecoder newInstance(InputStream is) throws IOException { return newInstance(is, false); }

    public static BitmapRegionDecoder newInstance(String pathName, boolean isShareable) throws IOException {
        Bitmap b = BitmapFactory.decodeFile(pathName);
        if (b == null) throw new IOException("Image format not supported");
        return new BitmapRegionDecoder(b);
    }

    public static BitmapRegionDecoder newInstance(String pathName) throws IOException { return newInstance(pathName, false); }

    public Bitmap decodeRegion(Rect rect, BitmapFactory.Options options) {
        Rect r = new Rect(rect);
        if (!r.intersect(0, 0, mFull.getWidth(), mFull.getHeight())) return null;
        Bitmap b = Bitmap.createBitmap(mFull, r.left, r.top, r.width(), r.height());
        int s = options != null && options.inSampleSize > 1 ? options.inSampleSize : 1;
        if (s > 1) b = Bitmap.createScaledBitmap(b, Math.max(1, b.getWidth() / s), Math.max(1, b.getHeight() / s), true);
        return b;
    }

    public int getWidth() { return mFull.getWidth(); }
    public int getHeight() { return mFull.getHeight(); }
    public void recycle() { mFull = null; }
    public final boolean isRecycled() { return mFull == null; }
}
