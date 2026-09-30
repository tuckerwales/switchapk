package android.graphics;

public class PixelFormat {
    public static final int UNKNOWN = 0;
    public static final int TRANSLUCENT = -3;
    public static final int TRANSPARENT = -2;
    public static final int OPAQUE = -1;
    public static final int RGBA_8888 = 1;
    public static final int RGBX_8888 = 2;
    public static final int RGB_888 = 3;
    public static final int RGB_565 = 4;
    public static final int RGBA_5551 = 6;
    public static final int RGBA_4444 = 7;
    public static final int A_8 = 8;
    public static final int L_8 = 9;
    public static final int LA_88 = 0xA;
    public static final int RGB_332 = 0xB;
    public static final int RGBA_F16 = 0x16;
    public static final int RGBA_1010102 = 0x2B;
    public int bytesPerPixel;
    public int bitsPerPixel;

    public static void getPixelFormatInfo(int format, PixelFormat info) {
        switch (format) {
            case RGBA_8888: case RGBX_8888: info.bitsPerPixel = 32; info.bytesPerPixel = 4; break;
            case RGB_888: info.bitsPerPixel = 24; info.bytesPerPixel = 3; break;
            case RGB_565: case RGBA_5551: case RGBA_4444: case LA_88: info.bitsPerPixel = 16; info.bytesPerPixel = 2; break;
            case A_8: case L_8: case RGB_332: info.bitsPerPixel = 8; info.bytesPerPixel = 1; break;
            case RGBA_F16: info.bitsPerPixel = 64; info.bytesPerPixel = 8; break;
            default: throw new IllegalArgumentException("unknown pixel format " + format);
        }
    }

    public static boolean formatHasAlpha(int format) {
        switch (format) {
            case A_8: case LA_88: case RGBA_4444: case RGBA_5551: case RGBA_8888: case RGBA_F16: case RGBA_1010102: case TRANSLUCENT: case TRANSPARENT:
                return true;
        }
        return false;
    }
}
