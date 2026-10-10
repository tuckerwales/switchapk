package android.media;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.CancellationSignal;
import android.util.Size;
import java.io.File;
import java.io.IOException;

/**
 * Thumbnails: images are decoded with a sample size near the target and
 * scaled to fit it. switchapk cannot grab video frames or read album art
 * here, so those throw IOException (the documented "no thumbnail" result)
 * and the deprecated forms return null.
 */
public class ThumbnailUtils {
    public static final int OPTIONS_RECYCLE_INPUT = 2;

    private static final int MICRO_KIND_SIZE = 96;
    private static final int MINI_KIND_WIDTH = 512, MINI_KIND_HEIGHT = 384;

    public ThumbnailUtils() {
    }

    public static Bitmap createAudioThumbnail(String filePath, int kind) {
        return null;
    }

    public static Bitmap createAudioThumbnail(File file, Size size, CancellationSignal signal) throws IOException {
        if (signal != null) signal.throwIfCanceled();
        throw new IOException("No album art found in " + file);
    }

    public static Bitmap createImageThumbnail(String filePath, int kind) {
        Size size = kind == 3 /* MICRO_KIND */ ? new Size(MICRO_KIND_SIZE, MICRO_KIND_SIZE) : new Size(MINI_KIND_WIDTH, MINI_KIND_HEIGHT);
        try {
            return createImageThumbnail(new File(filePath), size, null);
        } catch (IOException e) {
            return null;
        }
    }

    public static Bitmap createImageThumbnail(File file, Size size, CancellationSignal signal) throws IOException {
        if (signal != null) signal.throwIfCanceled();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Cannot decode " + file);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= size.getWidth() && bounds.outHeight / (sample * 2) >= size.getHeight()) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        if (signal != null) signal.throwIfCanceled();
        Bitmap bm = BitmapFactory.decodeFile(file.getPath(), opts);
        if (bm == null) throw new IOException("Cannot decode " + file);
        // Fit inside the requested size, keeping the aspect ratio.
        float scale = Math.min((float) size.getWidth() / bm.getWidth(), (float) size.getHeight() / bm.getHeight());
        if (scale < 1f) {
            int w = Math.max(1, Math.round(bm.getWidth() * scale)), h = Math.max(1, Math.round(bm.getHeight() * scale));
            Bitmap scaled = Bitmap.createScaledBitmap(bm, w, h, true);
            if (scaled != bm) bm.recycle();
            bm = scaled;
        }
        return bm;
    }

    public static Bitmap createVideoThumbnail(String filePath, int kind) {
        return null;
    }

    public static Bitmap createVideoThumbnail(File file, Size size, CancellationSignal signal) throws IOException {
        if (signal != null) signal.throwIfCanceled();
        throw new IOException("Video thumbnails are not supported: " + file);
    }

    public static Bitmap extractThumbnail(Bitmap source, int width, int height) {
        return extractThumbnail(source, width, height, 0);
    }

    /** Scales to cover width x height and crops the centre, as Android does. */
    public static Bitmap extractThumbnail(Bitmap source, int width, int height, int options) {
        if (source == null) return null;
        float scale = Math.max((float) width / source.getWidth(), (float) height / source.getHeight());
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        int cropW = Math.min(source.getWidth(), Math.round(width / scale));
        int cropH = Math.min(source.getHeight(), Math.round(height / scale));
        int x = (source.getWidth() - cropW) / 2, y = (source.getHeight() - cropH) / 2;
        Bitmap scaled = Bitmap.createBitmap(source, x, y, cropW, cropH, m, true);
        if (scaled.getWidth() != width || scaled.getHeight() != height) {
            Bitmap exact = Bitmap.createScaledBitmap(scaled, width, height, true);
            if (exact != scaled) scaled.recycle();
            scaled = exact;
        }
        if ((options & OPTIONS_RECYCLE_INPUT) != 0 && scaled != source) source.recycle();
        return scaled;
    }
}
