package android.graphics;

import java.util.HashMap;
import java.util.Locale;

public class Color {
    public static final int BLACK = 0xFF000000;
    public static final int DKGRAY = 0xFF444444;
    public static final int GRAY = 0xFF888888;
    public static final int LTGRAY = 0xFFCCCCCC;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int RED = 0xFFFF0000;
    public static final int GREEN = 0xFF00FF00;
    public static final int BLUE = 0xFF0000FF;
    public static final int YELLOW = 0xFFFFFF00;
    public static final int CYAN = 0xFF00FFFF;
    public static final int MAGENTA = 0xFFFF00FF;
    public static final int TRANSPARENT = 0;

    private final float mR, mG, mB, mA;

    public Color() {
        mR = mG = mB = 0;
        mA = 1;
    }

    private Color(float r, float g, float b, float a) {
        mR = r;
        mG = g;
        mB = b;
        mA = a;
    }

    public ColorSpace getColorSpace() { return ColorSpace.get(ColorSpace.Named.SRGB); }
    public ColorSpace.Model getModel() { return ColorSpace.Model.RGB; }
    public boolean isWideGamut() { return false; }
    public boolean isSrgb() { return true; }
    public int getComponentCount() { return 4; }
    public long pack() { return pack(mR, mG, mB, mA); }
    public Color convert(ColorSpace colorSpace) { return this; }
    public int toArgb() { return argb(mA, mR, mG, mB); }
    public float red() { return mR; }
    public float green() { return mG; }
    public float blue() { return mB; }
    public float alpha() { return mA; }
    public float[] getComponents() { return new float[] {mR, mG, mB, mA}; }
    public float getComponent(int component) { return getComponents()[component]; }
    public float luminance() { return 0.2126f * mR + 0.7152f * mG + 0.0722f * mB; }

    public static Color valueOf(int color) { return new Color(red(color) / 255.0f, green(color) / 255.0f, blue(color) / 255.0f, alpha(color) / 255.0f); }
    public static Color valueOf(long color) { return valueOf(toArgb(color)); }
    public static Color valueOf(float r, float g, float b) { return new Color(r, g, b, 1.0f); }
    public static Color valueOf(float r, float g, float b, float a) { return new Color(r, g, b, a); }
    public static Color valueOf(float[] components, ColorSpace colorSpace) { return new Color(components[0], components[1], components[2], components.length > 3 ? components[3] : 1f); }

    public static long pack(int color) { return (color & 0xffffffffL) << 32; }
    public static long pack(float red, float green, float blue) { return pack(red, green, blue, 1.0f); }
    public static long pack(float red, float green, float blue, float alpha) { return pack(argb(alpha, red, green, blue)); }
    public static long pack(float red, float green, float blue, float alpha, ColorSpace colorSpace) { return pack(red, green, blue, alpha); }
    public static long convert(int color, ColorSpace colorSpace) { return pack(color); }
    public static long convert(long color, ColorSpace colorSpace) { return color; }
    public static long convert(float r, float g, float b, float a, ColorSpace source, ColorSpace destination) { return pack(r, g, b, a); }
    public static long convert(long color, ColorSpace.Connector connector) { return color; }
    public static ColorSpace colorSpace(long color) { return ColorSpace.get(ColorSpace.Named.SRGB); }
    public static float red(long color) { return red(toArgb(color)) / 255.0f; }
    public static float green(long color) { return green(toArgb(color)) / 255.0f; }
    public static float blue(long color) { return blue(toArgb(color)) / 255.0f; }
    public static float alpha(long color) { return alpha(toArgb(color)) / 255.0f; }
    public static boolean isSrgb(long color) { return true; }
    public static boolean isWideGamut(long color) { return false; }
    public static boolean isInColorSpace(long color, ColorSpace colorSpace) { return true; }
    public static int toArgb(long color) { return (int) (color >> 32); }
    public static float luminance(long color) { return luminance(toArgb(color)); }

    public static int alpha(int color) { return color >>> 24; }
    public static int red(int color) { return (color >> 16) & 0xFF; }
    public static int green(int color) { return (color >> 8) & 0xFF; }
    public static int blue(int color) { return color & 0xFF; }
    public static int rgb(int red, int green, int blue) { return 0xff000000 | (red << 16) | (green << 8) | blue; }
    public static int rgb(float red, float green, float blue) { return 0xff000000 | ((int) (red * 255.0f + 0.5f) << 16) | ((int) (green * 255.0f + 0.5f) << 8) | (int) (blue * 255.0f + 0.5f); }
    public static int argb(int alpha, int red, int green, int blue) { return (alpha << 24) | (red << 16) | (green << 8) | blue; }
    public static int argb(float alpha, float red, float green, float blue) {
        return ((int) (alpha * 255.0f + 0.5f) << 24) | ((int) (red * 255.0f + 0.5f) << 16) | ((int) (green * 255.0f + 0.5f) << 8) | (int) (blue * 255.0f + 0.5f);
    }

    public static float luminance(int color) {
        double r = linear(red(color) / 255.0), g = linear(green(color) / 255.0), b = linear(blue(color) / 255.0);
        return (float) ((0.2126 * r) + (0.7152 * g) + (0.0722 * b));
    }

    private static double linear(double c) { return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }

    public static int parseColor(String colorString) {
        if (colorString.charAt(0) == '#') {
            long color = Long.parseLong(colorString.substring(1), 16);
            if (colorString.length() == 7) color |= 0x00000000ff000000L;
            else if (colorString.length() != 9) throw new IllegalArgumentException("Unknown color");
            return (int) color;
        } else {
            Integer color = sColorNameMap.get(colorString.toLowerCase(Locale.ROOT));
            if (color != null) return color;
        }
        throw new IllegalArgumentException("Unknown color");
    }

    public static void RGBToHSV(int red, int green, int blue, float[] hsv) {
        float r = red / 255f, g = green / 255f, b = blue / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0;
        if (d != 0) {
            if (max == r) h = 60 * (((g - b) / d) % 6);
            else if (max == g) h = 60 * (((b - r) / d) + 2);
            else h = 60 * (((r - g) / d) + 4);
        }
        if (h < 0) h += 360;
        hsv[0] = h;
        hsv[1] = max == 0 ? 0 : d / max;
        hsv[2] = max;
    }

    public static void colorToHSV(int color, float[] hsv) { RGBToHSV((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, hsv); }
    public static int HSVToColor(float[] hsv) { return HSVToColor(0xFF, hsv); }

    public static int HSVToColor(int alpha, float[] hsv) {
        float h = hsv[0], s = hsv[1], v = hsv[2];
        h = ((h % 360) + 360) % 360;
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60) % 2 - 1));
        float m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return argb(alpha, Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Color)) return false;
        Color c = (Color) o;
        return c.mR == mR && c.mG == mG && c.mB == mB && c.mA == mA;
    }

    @Override
    public int hashCode() { return toArgb(); }

    @Override
    public String toString() { return "Color(" + mR + ", " + mG + ", " + mB + ", " + mA + ", sRGB IEC61966-2.1)"; }

    private static final HashMap<String, Integer> sColorNameMap = new HashMap<String, Integer>();
    static {
        sColorNameMap.put("black", BLACK);
        sColorNameMap.put("darkgray", DKGRAY);
        sColorNameMap.put("gray", GRAY);
        sColorNameMap.put("lightgray", LTGRAY);
        sColorNameMap.put("white", WHITE);
        sColorNameMap.put("red", RED);
        sColorNameMap.put("green", GREEN);
        sColorNameMap.put("blue", BLUE);
        sColorNameMap.put("yellow", YELLOW);
        sColorNameMap.put("cyan", CYAN);
        sColorNameMap.put("magenta", MAGENTA);
        sColorNameMap.put("aqua", 0xFF00FFFF);
        sColorNameMap.put("fuchsia", 0xFFFF00FF);
        sColorNameMap.put("darkgrey", DKGRAY);
        sColorNameMap.put("grey", GRAY);
        sColorNameMap.put("lightgrey", LTGRAY);
        sColorNameMap.put("lime", 0xFF00FF00);
        sColorNameMap.put("maroon", 0xFF800000);
        sColorNameMap.put("navy", 0xFF000080);
        sColorNameMap.put("olive", 0xFF808000);
        sColorNameMap.put("purple", 0xFF800080);
        sColorNameMap.put("silver", 0xFFC0C0C0);
        sColorNameMap.put("teal", 0xFF008080);
    }
}
