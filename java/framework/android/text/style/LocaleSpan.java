package android.text.style;

import android.graphics.Paint;
import android.os.LocaleList;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import android.text.TextUtils;
import java.util.Locale;

/** Sets the text locale of the spanned run (AOSP LocaleSpan). */
public class LocaleSpan extends MetricAffectingSpan implements ParcelableSpan {
    private final LocaleList mLocales;

    public LocaleSpan(Locale locale) { mLocales = locale == null ? LocaleList.getEmptyLocaleList() : new LocaleList(locale); }

    public LocaleSpan(LocaleList locales) { mLocales = locales; }

    public LocaleSpan(Parcel source) { mLocales = LocaleList.forLanguageTags(source.readString()); }

    public int getSpanTypeId() { return getSpanTypeIdInternal(); }

    /** Hidden AOSP API. */
    public int getSpanTypeIdInternal() { return TextUtils.LOCALE_SPAN; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

    /** Hidden AOSP API. */
    public void writeToParcelInternal(Parcel dest, int flags) { dest.writeString(mLocales.toLanguageTags()); }

    public Locale getLocale() { return mLocales.get(0); }

    public LocaleList getLocales() { return mLocales; }

    @Override
    public void updateDrawState(TextPaint ds) { apply(ds, mLocales); }

    @Override
    public void updateMeasureState(TextPaint paint) { apply(paint, mLocales); }

    private static void apply(Paint paint, LocaleList locales) { paint.setTextLocales(locales); }

    @Override
    public String toString() { return "LocaleSpan{locales=" + getLocales() + "}"; }
}
