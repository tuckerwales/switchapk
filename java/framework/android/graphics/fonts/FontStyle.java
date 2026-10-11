package android.graphics.fonts;

public final class FontStyle {
    public static final int FONT_WEIGHT_UNSPECIFIED = -1;
    public static final int FONT_WEIGHT_MIN = 1;
    public static final int FONT_WEIGHT_THIN = 100;
    public static final int FONT_WEIGHT_EXTRA_LIGHT = 200;
    public static final int FONT_WEIGHT_LIGHT = 300;
    public static final int FONT_WEIGHT_NORMAL = 400;
    public static final int FONT_WEIGHT_MEDIUM = 500;
    public static final int FONT_WEIGHT_SEMI_BOLD = 600;
    public static final int FONT_WEIGHT_BOLD = 700;
    public static final int FONT_WEIGHT_EXTRA_BOLD = 800;
    public static final int FONT_WEIGHT_BLACK = 900;
    public static final int FONT_WEIGHT_MAX = 1000;
    public static final int FONT_SLANT_UPRIGHT = 0;
    public static final int FONT_SLANT_ITALIC = 1;

    private final int mWeight, mSlant;

    public FontStyle() {
        this(FONT_WEIGHT_NORMAL, FONT_SLANT_UPRIGHT);
    }

    public FontStyle(int weight, int slant) {
        if (weight < FONT_WEIGHT_MIN || weight > FONT_WEIGHT_MAX) {
            throw new IllegalArgumentException("weight value must be [" + FONT_WEIGHT_MIN + ", " + FONT_WEIGHT_MAX + "]");
        }
        if (slant != FONT_SLANT_UPRIGHT && slant != FONT_SLANT_ITALIC) throw new IllegalArgumentException("Unknown slant value: " + slant);
        mWeight = weight;
        mSlant = slant;
    }

    public int getWeight() {
        return mWeight;
    }

    public int getSlant() {
        return mSlant;
    }

    /** framework-internal: how far a font with this style is from the wanted one (smaller is closer). */
    public int getMatchScore(FontStyle o) {
        return Math.abs(getWeight() - o.getWeight()) / 100 + (getSlant() == o.getSlant() ? 0 : 2);
    }

    public boolean equals(Object o) {
        return o instanceof FontStyle && ((FontStyle) o).mWeight == mWeight && ((FontStyle) o).mSlant == mSlant;
    }

    public int hashCode() {
        return mWeight * 31 + mSlant;
    }

    public String toString() {
        return "FontStyle { weight=" + mWeight + ", slant=" + mSlant + "}";
    }
}
