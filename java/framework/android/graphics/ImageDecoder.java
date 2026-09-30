package android.graphics;

import android.content.ContentResolver;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Size;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public final class ImageDecoder implements AutoCloseable {
    public static final int ALLOCATOR_DEFAULT = 0;
    public static final int ALLOCATOR_SOFTWARE = 1;
    public static final int ALLOCATOR_SHARED_MEMORY = 2;
    public static final int ALLOCATOR_HARDWARE = 3;
    public static final int MEMORY_POLICY_DEFAULT = 1;
    public static final int MEMORY_POLICY_LOW_RAM = 0;

    public abstract static class Source {
        abstract byte[] bytes() throws IOException;
        Resources resources() { return null; }
        int density() { return Bitmap.DENSITY_NONE; }
    }

    public static class ImageInfo {
        private final Size mSize;
        private final String mMime;
        ImageInfo(Size size, String mime) { mSize = size; mMime = mime; }
        public Size getSize() { return mSize; }
        public String getMimeType() { return mMime; }
        public boolean isAnimated() { return false; }
        public ColorSpace getColorSpace() { return ColorSpace.get(ColorSpace.Named.SRGB); }
    }

    public interface OnHeaderDecodedListener {
        void onHeaderDecoded(ImageDecoder decoder, ImageInfo info, Source source);
    }

    public interface OnPartialImageListener {
        boolean onPartialImage(DecodeException exception);
    }

    public static final class DecodeException extends IOException {
        public static final int SOURCE_EXCEPTION = 1;
        public static final int SOURCE_INCOMPLETE = 2;
        public static final int SOURCE_MALFORMED_DATA = 3;
        private final int mError;
        DecodeException(int error, String msg) { super(msg); mError = error; }
        public int getError() { return mError; }
        public Source getSource() { return null; }
    }

    private int mTargetWidth, mTargetHeight;
    private Rect mCrop;
    private boolean mMutable;
    private PostProcessor mPostProcessor;

    public void setTargetSize(int width, int height) { mTargetWidth = width; mTargetHeight = height; }
    public void setTargetSampleSize(int sampleSize) {}
    public void setAllocator(int allocator) {}
    public int getAllocator() { return ALLOCATOR_DEFAULT; }
    public void setUnpremultipliedRequired(boolean unpremultipliedRequired) {}
    public void setPostProcessor(PostProcessor postProcessor) { mPostProcessor = postProcessor; }
    public PostProcessor getPostProcessor() { return mPostProcessor; }
    public void setOnPartialImageListener(OnPartialImageListener listener) {}
    public void setCrop(Rect subset) { mCrop = subset; }
    public Rect getCrop() { return mCrop; }
    public void setMutableRequired(boolean mutable) { mMutable = mutable; }
    public boolean isMutableRequired() { return mMutable; }
    public void setMemorySizePolicy(int policy) {}
    public void setDecodeAsAlphaMaskEnabled(boolean enabled) {}
    public void setTargetColorSpace(ColorSpace colorSpace) {}
    public void close() {}

    public static Source createSource(final Resources res, final int resId) {
        return new Source() {
            byte[] bytes() throws IOException { return readAll(res.openRawResource(resId)); }
            Resources resources() { return res; }
        };
    }

    public static Source createSource(final ContentResolver cr, final Uri uri) {
        return new Source() {
            byte[] bytes() throws IOException { return readAll(cr.openInputStream(uri)); }
        };
    }

    public static Source createSource(final AssetManager assets, final String fileName) {
        return new Source() {
            byte[] bytes() throws IOException { return readAll(assets.open(fileName)); }
        };
    }

    public static Source createSource(final byte[] data, final int offset, final int length) {
        return new Source() {
            byte[] bytes() { byte[] b = new byte[length]; System.arraycopy(data, offset, b, 0, length); return b; }
        };
    }

    public static Source createSource(byte[] data) { return createSource(data, 0, data.length); }

    public static Source createSource(final ByteBuffer buffer) {
        return new Source() {
            byte[] bytes() {
                ByteBuffer b = buffer.duplicate();
                byte[] out = new byte[b.remaining()];
                b.get(out);
                return out;
            }
        };
    }

    public static Source createSource(final File file) {
        return new Source() {
            byte[] bytes() throws IOException { return readAll(new java.io.FileInputStream(file)); }
        };
    }

    public static Source createSource(final java.util.concurrent.Callable<AssetFileDescriptorHolder> callable) {
        throw new UnsupportedOperationException();
    }

    /** Placeholder type for the Callable-based factory. */
    public static final class AssetFileDescriptorHolder {}

    static byte[] readAll(InputStream in) throws IOException {
        if (in == null) throw new IOException("null stream");
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }

    public static Bitmap decodeBitmap(Source src, OnHeaderDecodedListener listener) throws IOException {
        byte[] data = src.bytes();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0) throw new DecodeException(DecodeException.SOURCE_MALFORMED_DATA, "Failed to decode image");
        ImageDecoder d = new ImageDecoder();
        if (listener != null) listener.onHeaderDecoded(d, new ImageInfo(new Size(bounds.outWidth, bounds.outHeight), bounds.outMimeType), src);
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (b == null) throw new DecodeException(DecodeException.SOURCE_MALFORMED_DATA, "Failed to decode image");
        if (d.mCrop != null) b = Bitmap.createBitmap(b, d.mCrop.left, d.mCrop.top, d.mCrop.width(), d.mCrop.height());
        if (d.mTargetWidth > 0 && d.mTargetHeight > 0) b = Bitmap.createScaledBitmap(b, d.mTargetWidth, d.mTargetHeight, true);
        if (d.mMutable && !b.isMutable()) b = b.copy(Bitmap.Config.ARGB_8888, true);
        if (d.mPostProcessor != null) {
            if (!b.isMutable()) b = b.copy(Bitmap.Config.ARGB_8888, true);
            d.mPostProcessor.onPostProcess(new Canvas(b));
        }
        return b;
    }

    public static Bitmap decodeBitmap(Source src) throws IOException { return decodeBitmap(src, null); }

    public static Drawable decodeDrawable(Source src, OnHeaderDecodedListener listener) throws IOException {
        Bitmap b = decodeBitmap(src, listener);
        Resources r = src.resources();
        return new BitmapDrawable(r != null ? r : Resources.getSystem(), b);
    }

    public static Drawable decodeDrawable(Source src) throws IOException { return decodeDrawable(src, null); }

    public static boolean isMimeTypeSupported(String mimeType) {
        return mimeType != null && (mimeType.equals("image/png") || mimeType.equals("image/jpeg") || mimeType.equals("image/gif") || mimeType.equals("image/bmp"));
    }
}
