package android.opengl;

import android.graphics.Bitmap;

/**
 * Utility methods for uploading bitmaps to GL textures. Bitmap pixels are
 * kept unpremultiplied (DECISIONS D10); the upload premultiplies them like
 * Android's premultiplied bitmaps arrive in GL.
 */
public final class GLUtils {
    private GLUtils() {}

    public static int getInternalFormat(Bitmap bitmap) {
        check(bitmap);
        switch (bitmap.getConfig()) {
            case ALPHA_8:
                return GLES20.GL_ALPHA;
            case RGB_565:
                return GLES20.GL_RGB;
            case ARGB_4444:
            case ARGB_8888:
                return GLES20.GL_RGBA;
            default:
                return -1;
        }
    }

    public static int getType(Bitmap bitmap) {
        check(bitmap);
        switch (bitmap.getConfig()) {
            case ALPHA_8:
            case ARGB_8888:
                return GLES20.GL_UNSIGNED_BYTE;
            case RGB_565:
                return GLES20.GL_UNSIGNED_SHORT_5_6_5;
            case ARGB_4444:
                return GLES20.GL_UNSIGNED_SHORT_4_4_4_4;
            default:
                return -1;
        }
    }

    public static void texImage2D(int target, int level, int internalformat, Bitmap bitmap, int border) {
        check(bitmap);
        int format = internalformat >= 0 ? internalformat : getInternalFormat(bitmap);
        upload(false, target, level, format, 0, 0, bitmap, format, getType(bitmap), border);
    }

    public static void texImage2D(int target, int level, int internalformat, Bitmap bitmap, int type, int border) {
        check(bitmap);
        int format = internalformat >= 0 ? internalformat : getInternalFormat(bitmap);
        upload(false, target, level, format, 0, 0, bitmap, format, type, border);
    }

    public static void texImage2D(int target, int level, Bitmap bitmap, int border) {
        check(bitmap);
        int format = getInternalFormat(bitmap);
        upload(false, target, level, format, 0, 0, bitmap, format, getType(bitmap), border);
    }

    public static void texSubImage2D(int target, int level, int xoffset, int yoffset, Bitmap bitmap) {
        check(bitmap);
        int format = getInternalFormat(bitmap);
        upload(true, target, level, format, xoffset, yoffset, bitmap, format, getType(bitmap), 0);
    }

    public static void texSubImage2D(int target, int level, int xoffset, int yoffset, Bitmap bitmap, int format,
            int type) {
        check(bitmap);
        upload(true, target, level, format, xoffset, yoffset, bitmap, format, type, 0);
    }

    public static String getEGLErrorString(int error) {
        switch (error) {
            case EGL14.EGL_SUCCESS: return "EGL_SUCCESS";
            case EGL14.EGL_NOT_INITIALIZED: return "EGL_NOT_INITIALIZED";
            case EGL14.EGL_BAD_ACCESS: return "EGL_BAD_ACCESS";
            case EGL14.EGL_BAD_ALLOC: return "EGL_BAD_ALLOC";
            case EGL14.EGL_BAD_ATTRIBUTE: return "EGL_BAD_ATTRIBUTE";
            case EGL14.EGL_BAD_CONFIG: return "EGL_BAD_CONFIG";
            case EGL14.EGL_BAD_CONTEXT: return "EGL_BAD_CONTEXT";
            case EGL14.EGL_BAD_CURRENT_SURFACE: return "EGL_BAD_CURRENT_SURFACE";
            case EGL14.EGL_BAD_DISPLAY: return "EGL_BAD_DISPLAY";
            case EGL14.EGL_BAD_MATCH: return "EGL_BAD_MATCH";
            case EGL14.EGL_BAD_NATIVE_PIXMAP: return "EGL_BAD_NATIVE_PIXMAP";
            case EGL14.EGL_BAD_NATIVE_WINDOW: return "EGL_BAD_NATIVE_WINDOW";
            case EGL14.EGL_BAD_PARAMETER: return "EGL_BAD_PARAMETER";
            case EGL14.EGL_BAD_SURFACE: return "EGL_BAD_SURFACE";
            case EGL14.EGL_CONTEXT_LOST: return "EGL_CONTEXT_LOST";
            default: return "0x" + Integer.toHexString(error);
        }
    }

    private static void check(Bitmap bitmap) {
        if (bitmap == null) throw new NullPointerException("texImage2D can't be used with a null Bitmap");
        if (bitmap.isRecycled()) throw new IllegalArgumentException("bitmap is recycled");
    }

    private static void upload(boolean sub, int target, int level, int internalformat, int xoffset, int yoffset,
            Bitmap bitmap, int format, int type, int border) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = bitmap.getPixelArray();
        if (pixels == null) {
            pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        }
        if (nUpload(sub, target, level, internalformat, xoffset, yoffset, pixels, w, h, format, type, border) != 0) {
            throw new IllegalArgumentException("invalid Bitmap format");
        }
    }

    private static native int nUpload(boolean sub, int target, int level, int internalformat, int xoffset,
            int yoffset, int[] argb, int width, int height, int format, int type, int border);
}
