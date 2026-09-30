package android.graphics.fonts;

public final class FontStyle {
    public static final int FONT_WEIGHT_NORMAL = 400;
    public static final int FONT_WEIGHT_BOLD = 700;
    public static final int FONT_SLANT_UPRIGHT = 0;
    public static final int FONT_SLANT_ITALIC = 1;
    private final int mWeight, mSlant;
    public FontStyle() { this(FONT_WEIGHT_NORMAL, FONT_SLANT_UPRIGHT); }
    public FontStyle(int weight, int slant) { mWeight = weight; mSlant = slant; }
    public int getWeight() { return mWeight; }
    public int getSlant() { return mSlant; }
}
