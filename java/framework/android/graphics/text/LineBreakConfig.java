package android.graphics.text;

import android.os.Parcel;
import android.os.Parcelable;

/** Line break style options. The software line breaker only honours NO_BREAK. */
public final class LineBreakConfig implements Parcelable {
    public static final int HYPHENATION_UNSPECIFIED = -1;
    public static final int HYPHENATION_DISABLED = 0;
    public static final int HYPHENATION_ENABLED = 1;
    public static final int LINE_BREAK_STYLE_UNSPECIFIED = -1;
    public static final int LINE_BREAK_STYLE_NONE = 0;
    public static final int LINE_BREAK_STYLE_LOOSE = 1;
    public static final int LINE_BREAK_STYLE_NORMAL = 2;
    public static final int LINE_BREAK_STYLE_STRICT = 3;
    public static final int LINE_BREAK_STYLE_NO_BREAK = 4;
    public static final int LINE_BREAK_STYLE_AUTO = 5;
    public static final int LINE_BREAK_WORD_STYLE_UNSPECIFIED = -1;
    public static final int LINE_BREAK_WORD_STYLE_NONE = 0;
    public static final int LINE_BREAK_WORD_STYLE_PHRASE = 1;
    public static final int LINE_BREAK_WORD_STYLE_AUTO = 2;

    /** Hidden AOSP constant. */
    public static final LineBreakConfig NONE = new Builder().setLineBreakStyle(LINE_BREAK_STYLE_NONE)
            .setLineBreakWordStyle(LINE_BREAK_WORD_STYLE_NONE).build();

    public static final Parcelable.Creator<LineBreakConfig> CREATOR = new Parcelable.Creator<LineBreakConfig>() {
        public LineBreakConfig createFromParcel(Parcel source) {
            return new LineBreakConfig(source.readInt(), source.readInt(), source.readInt());
        }

        public LineBreakConfig[] newArray(int size) { return new LineBreakConfig[size]; }
    };

    private final int mLineBreakStyle;
    private final int mLineBreakWordStyle;
    private final int mHyphenation;

    LineBreakConfig() { this(LINE_BREAK_STYLE_UNSPECIFIED, LINE_BREAK_WORD_STYLE_UNSPECIFIED, HYPHENATION_UNSPECIFIED); }

    LineBreakConfig(int style, int wordStyle, int hyphenation) {
        mLineBreakStyle = style;
        mLineBreakWordStyle = wordStyle;
        mHyphenation = hyphenation;
    }

    public int getLineBreakStyle() { return mLineBreakStyle; }

    public int getLineBreakWordStyle() { return mLineBreakWordStyle; }

    public int getHyphenation() { return mHyphenation; }

    public LineBreakConfig merge(LineBreakConfig config) {
        if (config == null) return this;
        return new LineBreakConfig(
                config.mLineBreakStyle == LINE_BREAK_STYLE_UNSPECIFIED ? mLineBreakStyle : config.mLineBreakStyle,
                config.mLineBreakWordStyle == LINE_BREAK_WORD_STYLE_UNSPECIFIED ? mLineBreakWordStyle : config.mLineBreakWordStyle,
                config.mHyphenation == HYPHENATION_UNSPECIFIED ? mHyphenation : config.mHyphenation);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LineBreakConfig)) return false;
        LineBreakConfig c = (LineBreakConfig) o;
        return c.mLineBreakStyle == mLineBreakStyle && c.mLineBreakWordStyle == mLineBreakWordStyle && c.mHyphenation == mHyphenation;
    }

    @Override
    public int hashCode() { return (mLineBreakStyle * 31 + mLineBreakWordStyle) * 31 + mHyphenation; }

    @Override
    public String toString() { return "LineBreakConfig{mLineBreakStyle=" + mLineBreakStyle + ", mLineBreakWordStyle=" + mLineBreakWordStyle + ", mHyphenation=" + mHyphenation + "}"; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mLineBreakStyle);
        dest.writeInt(mLineBreakWordStyle);
        dest.writeInt(mHyphenation);
    }

    public static final class Builder {
        private int mLineBreakStyle = LINE_BREAK_STYLE_UNSPECIFIED;
        private int mLineBreakWordStyle = LINE_BREAK_WORD_STYLE_UNSPECIFIED;
        private int mHyphenation = HYPHENATION_UNSPECIFIED;

        public Builder() {}

        public Builder merge(LineBreakConfig config) {
            if (config.mLineBreakStyle != LINE_BREAK_STYLE_UNSPECIFIED) mLineBreakStyle = config.mLineBreakStyle;
            if (config.mLineBreakWordStyle != LINE_BREAK_WORD_STYLE_UNSPECIFIED) mLineBreakWordStyle = config.mLineBreakWordStyle;
            if (config.mHyphenation != HYPHENATION_UNSPECIFIED) mHyphenation = config.mHyphenation;
            return this;
        }

        public Builder setLineBreakStyle(int lineBreakStyle) { mLineBreakStyle = lineBreakStyle; return this; }

        public Builder setLineBreakWordStyle(int lineBreakWordStyle) { mLineBreakWordStyle = lineBreakWordStyle; return this; }

        public Builder setHyphenation(int hyphenation) { mHyphenation = hyphenation; return this; }

        public LineBreakConfig build() { return new LineBreakConfig(mLineBreakStyle, mLineBreakWordStyle, mHyphenation); }
    }
}
