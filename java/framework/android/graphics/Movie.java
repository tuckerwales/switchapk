package android.graphics;

@Deprecated
public class Movie {
    private final Bitmap mFrame;
    private Movie(Bitmap frame) { mFrame = frame; }
    public int width() { return mFrame.getWidth(); }
    public int height() { return mFrame.getHeight(); }
    public boolean isOpaque() { return !mFrame.hasAlpha(); }
    public int duration() { return 0; }
    public boolean setTime(int relativeMilliseconds) { return true; }
    public void draw(Canvas canvas, float x, float y, Paint paint) { canvas.drawBitmap(mFrame, x, y, paint); }
    public void draw(Canvas canvas, float x, float y) { draw(canvas, x, y, null); }
    public static Movie decodeStream(java.io.InputStream is) { Bitmap b = BitmapFactory.decodeStream(is); return b != null ? new Movie(b) : null; }
    public static Movie decodeByteArray(byte[] data, int offset, int length) { Bitmap b = BitmapFactory.decodeByteArray(data, offset, length); return b != null ? new Movie(b) : null; }
    public static Movie decodeFile(String pathName) { Bitmap b = BitmapFactory.decodeFile(pathName); return b != null ? new Movie(b) : null; }
}
