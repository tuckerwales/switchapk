package android.text.style;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.LocaleList;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import android.text.TextUtils;

/** Applies a TextAppearance style (or explicit values) to the spanned run (AOSP TextAppearanceSpan). */
public class TextAppearanceSpan extends MetricAffectingSpan implements ParcelableSpan {
    // Order matters: indexes below follow this array.
    private static final int[] ATTRS = {
        android.R.attr.textSize, android.R.attr.typeface, android.R.attr.textStyle, android.R.attr.textColor,
        android.R.attr.textColorLink, android.R.attr.fontFamily, android.R.attr.shadowColor,
        android.R.attr.shadowDx, android.R.attr.shadowDy, android.R.attr.shadowRadius,
        android.R.attr.elegantTextHeight, android.R.attr.letterSpacing, android.R.attr.fontFeatureSettings,
        android.R.attr.fontVariationSettings, android.R.attr.textFontWeight, android.R.attr.textLocale,
    };

    private final String mFamilyName;
    private final int mStyle;
    private final int mTextSize;
    private final ColorStateList mTextColor;
    private final ColorStateList mTextColorLink;
    private final Typeface mTypeface;
    private final int mTextFontWeight;
    private final LocaleList mTextLocales;
    private final float mShadowRadius;
    private final float mShadowDx;
    private final float mShadowDy;
    private final int mShadowColor;
    private final boolean mHasElegantTextHeight;
    private final boolean mElegantTextHeight;
    private final boolean mHasLetterSpacing;
    private final float mLetterSpacing;
    private final String mFontFeatureSettings;
    private final String mFontVariationSettings;

    public TextAppearanceSpan(Context context, int appearance) { this(context, appearance, -1); }

    public TextAppearanceSpan(Context context, int appearance, int colorList) {
        TypedArray a = context.obtainStyledAttributes(appearance, ATTRS);
        ColorStateList textColor = a.getColorStateList(3);
        mTextColorLink = a.getColorStateList(4);
        mTextSize = a.getDimensionPixelSize(0, -1);
        mStyle = a.getInt(2, 0);
        String family = a.getString(5);
        if (family == null) {
            switch (a.getInt(1, 0)) {
                case 1: family = "sans"; break;
                case 2: family = "serif"; break;
                case 3: family = "monospace"; break;
            }
        }
        mFamilyName = family;
        mTypeface = null;
        mTextFontWeight = a.getInt(14, -1);
        String locale = a.getString(15);
        mTextLocales = locale != null ? LocaleList.forLanguageTags(locale) : null;
        mShadowRadius = a.getFloat(9, 0.0f);
        mShadowDx = a.getFloat(7, 0.0f);
        mShadowDy = a.getFloat(8, 0.0f);
        mShadowColor = a.getColor(6, 0);
        mHasElegantTextHeight = a.hasValue(10);
        mElegantTextHeight = a.getBoolean(10, false);
        mHasLetterSpacing = a.hasValue(11);
        mLetterSpacing = a.getFloat(11, 0.0f);
        mFontFeatureSettings = a.getString(12);
        mFontVariationSettings = a.getString(13);
        a.recycle();
        if (colorList >= 0) {
            TypedArray b = context.getResources().obtainTypedArray(colorList);
            textColor = b.getColorStateList(0);
            b.recycle();
        }
        mTextColor = textColor;
    }

    public TextAppearanceSpan(String family, int style, int size, ColorStateList color, ColorStateList linkColor) {
        mFamilyName = family;
        mStyle = style;
        mTextSize = size;
        mTextColor = color;
        mTextColorLink = linkColor;
        mTypeface = null;
        mTextFontWeight = -1;
        mTextLocales = null;
        mShadowRadius = 0.0f;
        mShadowDx = 0.0f;
        mShadowDy = 0.0f;
        mShadowColor = 0;
        mHasElegantTextHeight = false;
        mElegantTextHeight = false;
        mHasLetterSpacing = false;
        mLetterSpacing = 0.0f;
        mFontFeatureSettings = null;
        mFontVariationSettings = null;
    }

    public TextAppearanceSpan(Parcel src) {
        mFamilyName = src.readString();
        mStyle = src.readInt();
        mTextSize = src.readInt();
        mTextColor = (ColorStateList) src.readValue(null);
        mTextColorLink = (ColorStateList) src.readValue(null);
        mTypeface = null;
        mTextFontWeight = src.readInt();
        String tags = src.readString();
        mTextLocales = tags != null ? LocaleList.forLanguageTags(tags) : null;
        mShadowRadius = src.readFloat();
        mShadowDx = src.readFloat();
        mShadowDy = src.readFloat();
        mShadowColor = src.readInt();
        mHasElegantTextHeight = src.readBoolean();
        mElegantTextHeight = src.readBoolean();
        mHasLetterSpacing = src.readBoolean();
        mLetterSpacing = src.readFloat();
        mFontFeatureSettings = src.readString();
        mFontVariationSettings = src.readString();
    }

    public int getSpanTypeId() { return getSpanTypeIdInternal(); }

    /** Hidden AOSP API. */
    public int getSpanTypeIdInternal() { return TextUtils.TEXT_APPEARANCE_SPAN; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

    /** Hidden AOSP API. */
    public void writeToParcelInternal(Parcel dest, int flags) {
        dest.writeString(mFamilyName);
        dest.writeInt(mStyle);
        dest.writeInt(mTextSize);
        dest.writeValue(mTextColor);
        dest.writeValue(mTextColorLink);
        dest.writeInt(mTextFontWeight);
        dest.writeString(mTextLocales != null ? mTextLocales.toLanguageTags() : null);
        dest.writeFloat(mShadowRadius);
        dest.writeFloat(mShadowDx);
        dest.writeFloat(mShadowDy);
        dest.writeInt(mShadowColor);
        dest.writeBoolean(mHasElegantTextHeight);
        dest.writeBoolean(mElegantTextHeight);
        dest.writeBoolean(mHasLetterSpacing);
        dest.writeFloat(mLetterSpacing);
        dest.writeString(mFontFeatureSettings);
        dest.writeString(mFontVariationSettings);
    }

    public String getFamily() { return mFamilyName; }

    public ColorStateList getTextColor() { return mTextColor; }

    public ColorStateList getLinkTextColor() { return mTextColorLink; }

    public int getTextSize() { return mTextSize; }

    public int getTextStyle() { return mStyle; }

    public int getTextFontWeight() { return mTextFontWeight; }

    public LocaleList getTextLocales() { return mTextLocales; }

    public Typeface getTypeface() { return mTypeface; }

    public int getShadowColor() { return mShadowColor; }

    public float getShadowDx() { return mShadowDx; }

    public float getShadowDy() { return mShadowDy; }

    public float getShadowRadius() { return mShadowRadius; }

    public String getFontFeatureSettings() { return mFontFeatureSettings; }

    public String getFontVariationSettings() { return mFontVariationSettings; }

    public boolean isElegantTextHeight() { return mElegantTextHeight; }

    public float getLetterSpacing() { return mLetterSpacing; }

    @Override
    public void updateDrawState(TextPaint ds) {
        updateMeasureState(ds);
        if (mTextColor != null) ds.setColor(mTextColor.getColorForState(ds.drawableState, 0));
        if (mTextColorLink != null) ds.linkColor = mTextColorLink.getColorForState(ds.drawableState, 0);
        if (mShadowColor != 0) ds.setShadowLayer(mShadowRadius, mShadowDx, mShadowDy, mShadowColor);
    }

    @Override
    public void updateMeasureState(TextPaint ds) {
        final Typeface styledTypeface;
        int style = 0;
        if (mTypeface != null) {
            style = mStyle;
            styledTypeface = Typeface.create(mTypeface, style);
        } else if (mFamilyName != null || mStyle != 0) {
            Typeface tf = ds.getTypeface();
            if (tf != null) style = tf.getStyle();
            style |= mStyle;
            if (mFamilyName != null) styledTypeface = Typeface.create(mFamilyName, style);
            else if (tf == null) styledTypeface = Typeface.defaultFromStyle(style);
            else styledTypeface = Typeface.create(tf, style);
        } else {
            styledTypeface = null;
        }
        if (styledTypeface != null) {
            final Typeface readyTypeface = mTextFontWeight >= 0
                    ? Typeface.create(styledTypeface, mTextFontWeight, (style & Typeface.ITALIC) != 0)
                    : styledTypeface;
            int fake = style & ~readyTypeface.getStyle();
            if ((fake & Typeface.BOLD) != 0) ds.setFakeBoldText(true);
            if ((fake & Typeface.ITALIC) != 0) ds.setTextSkewX(-0.25f);
            ds.setTypeface(readyTypeface);
        }
        if (mTextSize > 0) ds.setTextSize(mTextSize);
        if (mTextLocales != null) ds.setTextLocales(mTextLocales);
        if (mHasElegantTextHeight) ds.setElegantTextHeight(mElegantTextHeight);
        if (mHasLetterSpacing) ds.setLetterSpacing(mLetterSpacing);
        if (mFontFeatureSettings != null) ds.setFontFeatureSettings(mFontFeatureSettings);
        if (mFontVariationSettings != null) ds.setFontVariationSettings(mFontVariationSettings);
    }

    @Override
    public String toString() { return "TextAppearanceSpan{familyName='" + getFamily() + "', style=" + getTextStyle() + ", textSize=" + getTextSize() + ", textColor=" + getTextColor() + "}"; }
}
