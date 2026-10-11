package android.graphics.fonts;

import java.util.ArrayList;

/** An axis setting such as "wght" 700. switchapk draws the default instance of variable fonts. */
public final class FontVariationAxis {
    private final String mTag;
    private final float mStyleValue;

    public FontVariationAxis(String tagString, float styleValue) {
        if (tagString == null || tagString.length() != 4) throw new IllegalArgumentException("Illegal tag pattern: " + tagString);
        for (int i = 0; i < 4; i++) {
            char c = tagString.charAt(i);
            if (c < 0x20 || c > 0x7e) throw new IllegalArgumentException("Illegal tag pattern: " + tagString);
        }
        mTag = tagString;
        mStyleValue = styleValue;
    }

    public String getTag() {
        return mTag;
    }

    public float getStyleValue() {
        return mStyleValue;
    }

    public String toString() {
        return "'" + mTag + "' " + mStyleValue;
    }

    public static FontVariationAxis[] fromFontVariationSettings(String settings) {
        if (settings == null || settings.isEmpty()) return null;
        ArrayList<FontVariationAxis> axes = new ArrayList<FontVariationAxis>();
        for (String part : settings.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            if (p.length() < 7 || (p.charAt(0) != '\'' && p.charAt(0) != '"') || p.charAt(5) != p.charAt(0)) {
                throw new IllegalArgumentException("Invalid format of font variation settings: " + settings);
            }
            try {
                axes.add(new FontVariationAxis(p.substring(1, 5), Float.parseFloat(p.substring(6).trim())));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Failed to parse float string: " + e.getMessage());
            }
        }
        return axes.toArray(new FontVariationAxis[0]);
    }

    public static String toFontVariationSettings(FontVariationAxis[] axes) {
        if (axes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (FontVariationAxis a : axes) {
            if (sb.length() > 0) sb.append(',');
            sb.append(a.toString());
        }
        return sb.toString();
    }

    public boolean equals(Object o) {
        return o instanceof FontVariationAxis && ((FontVariationAxis) o).mTag.equals(mTag)
                && Float.compare(((FontVariationAxis) o).mStyleValue, mStyleValue) == 0;
    }

    public int hashCode() {
        return mTag.hashCode() * 31 + Float.floatToIntBits(mStyleValue);
    }
}
