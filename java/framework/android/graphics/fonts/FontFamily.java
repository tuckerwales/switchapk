package android.graphics.fonts;

import java.util.ArrayList;

/** Fonts of one family, at most one per style. */
public final class FontFamily {
    private final ArrayList<Font> mFonts;

    private FontFamily(ArrayList<Font> fonts) {
        mFonts = fonts;
    }

    public Font getFont(int index) {
        return mFonts.get(index);
    }

    public int getSize() {
        return mFonts.size();
    }

    /** framework-internal: the font closest to the wanted style. */
    public Font getClosestMatch(FontStyle style) {
        Font best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Font f : mFonts) {
            int score = f.getStyle().getMatchScore(style);
            if (score < bestScore) {
                best = f;
                bestScore = score;
            }
        }
        return best;
    }

    public static final class Builder {
        private final ArrayList<Font> mFonts = new ArrayList<Font>();

        public Builder(Font font) {
            if (font == null) throw new NullPointerException("font can not be null");
            mFonts.add(font);
        }

        public Builder addFont(Font font) {
            if (font == null) throw new NullPointerException("font can not be null");
            for (Font f : mFonts) {
                if (f.getStyle().equals(font.getStyle())) {
                    throw new IllegalArgumentException("A font with the same style already exists: " + font.getStyle());
                }
            }
            mFonts.add(font);
            return this;
        }

        public FontFamily buildVariableFamily() {
            return mFonts.size() == 1 ? build() : null;
        }

        public FontFamily build() {
            return new FontFamily(new ArrayList<Font>(mFonts));
        }
    }
}
