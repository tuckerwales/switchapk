package android.graphics;

public class ImageFormat {
    public static final int UNKNOWN = 0;
    public static final int RGB_565 = 4;
    public static final int YV12 = 0x32315659;
    public static final int Y8 = 0x20203859;
    public static final int NV16 = 0x10;
    public static final int NV21 = 0x11;
    public static final int YUY2 = 0x14;
    public static final int JPEG = 0x100;
    public static final int YUV_420_888 = 0x23;
    public static final int FLEX_RGB_888 = 0x29;
    public static final int FLEX_RGBA_8888 = 0x2A;
    public static final int RAW_SENSOR = 0x20;
    public static final int PRIVATE = 0x22;

    public static int getBitsPerPixel(int format) {
        switch (format) {
            case RGB_565: case NV16: case YUY2: return 16;
            case YV12: case NV21: case YUV_420_888: return 12;
            case Y8: return 8;
            case FLEX_RGB_888: return 24;
            case FLEX_RGBA_8888: return 32;
        }
        return -1;
    }
}
