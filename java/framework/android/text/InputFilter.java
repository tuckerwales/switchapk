package android.text;

import java.util.Locale;

/** Filters text going into an Editable (AOSP InputFilter). */
public interface InputFilter {
    CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend);

    class AllCaps implements InputFilter {
        private final Locale mLocale;

        public AllCaps() { mLocale = null; }

        public AllCaps(Locale locale) {
            if (locale == null) throw new NullPointerException("locale must not be null");
            mLocale = locale;
        }

        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            boolean lower = false;
            for (int i = start; i < end; i++) {
                if (Character.isLowerCase(source.charAt(i))) {
                    lower = true;
                    break;
                }
            }
            if (!lower) return null;
            String upper = mLocale != null ? source.subSequence(start, end).toString().toUpperCase(mLocale)
                    : source.subSequence(start, end).toString().toUpperCase();
            if (source instanceof Spanned) {
                SpannableString s = new SpannableString(upper);
                if (upper.length() == end - start) {
                    TextUtils.copySpansFrom((Spanned) source, start, end, null, s, 0);
                }
                return s;
            }
            return upper;
        }
    }

    class LengthFilter implements InputFilter {
        private final int mMax;

        public LengthFilter(int max) { mMax = max; }

        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            int keep = mMax - (dest.length() - (dend - dstart));
            if (keep <= 0) return "";
            if (keep >= end - start) return null;
            keep += start;
            if (Character.isHighSurrogate(source.charAt(keep - 1))) {
                --keep;
                if (keep == start) return "";
            }
            return source.subSequence(start, keep);
        }

        public int getMax() { return mMax; }
    }
}
