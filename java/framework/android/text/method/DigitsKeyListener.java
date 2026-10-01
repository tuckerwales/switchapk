package android.text.method;

import android.text.InputType;
import android.text.Spanned;
import java.util.Locale;

/** Digits, optional sign and decimal point (AOSP DigitsKeyListener, ASCII digits). */
public class DigitsKeyListener extends NumberKeyListener {
    private static final char[] DIGITS = "0123456789".toCharArray();
    private static final char[] SIGNED = "0123456789+-".toCharArray();
    private static final char[] DECIMAL = "0123456789.".toCharArray();
    private static final char[] SIGNED_DECIMAL = "0123456789+-.".toCharArray();

    private static DigitsKeyListener sPlain;
    private static DigitsKeyListener sSigned;
    private static DigitsKeyListener sDecimal;
    private static DigitsKeyListener sSignedDecimal;

    private final boolean mSign;
    private final boolean mDecimal;
    private final char[] mAccepted;

    public DigitsKeyListener() { this(false, false); }

    public DigitsKeyListener(boolean sign, boolean decimal) { this(null, sign, decimal); }

    public DigitsKeyListener(Locale locale) { this(locale, false, false); }

    public DigitsKeyListener(Locale locale, boolean sign, boolean decimal) {
        mSign = sign;
        mDecimal = decimal;
        if (sign && decimal) mAccepted = SIGNED_DECIMAL;
        else if (sign) mAccepted = SIGNED;
        else if (decimal) mAccepted = DECIMAL;
        else mAccepted = DIGITS;
    }

    private DigitsKeyListener(String accepted) {
        mSign = false;
        mDecimal = false;
        mAccepted = accepted == null ? DIGITS : accepted.toCharArray();
    }

    public static DigitsKeyListener getInstance() { return getInstance(false, false); }

    public static DigitsKeyListener getInstance(boolean sign, boolean decimal) {
        if (sign && decimal) {
            if (sSignedDecimal == null) sSignedDecimal = new DigitsKeyListener(true, true);
            return sSignedDecimal;
        }
        if (sign) {
            if (sSigned == null) sSigned = new DigitsKeyListener(true, false);
            return sSigned;
        }
        if (decimal) {
            if (sDecimal == null) sDecimal = new DigitsKeyListener(false, true);
            return sDecimal;
        }
        if (sPlain == null) sPlain = new DigitsKeyListener(false, false);
        return sPlain;
    }

    public static DigitsKeyListener getInstance(Locale locale) { return getInstance(locale, false, false); }

    public static DigitsKeyListener getInstance(Locale locale, boolean sign, boolean decimal) {
        return getInstance(sign, decimal);
    }

    public static DigitsKeyListener getInstance(String accepted) { return new DigitsKeyListener(accepted); }

    @Override
    protected char[] getAcceptedChars() { return mAccepted; }

    public int getInputType() {
        int type = InputType.TYPE_CLASS_NUMBER;
        if (mSign) type |= InputType.TYPE_NUMBER_FLAG_SIGNED;
        if (mDecimal) type |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
        return type;
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
        CharSequence stripped = super.filter(source, start, end, dest, dstart, dend);
        if (!mSign && !mDecimal) return stripped;
        CharSequence in = stripped != null ? stripped : source.subSequence(start, end);
        int signAt = -1;
        int dotAt = -1;
        int len = dest.length();
        for (int i = 0; i < dstart; i++) {
            char c = dest.charAt(i);
            if (c == '-' || c == '+') signAt = i;
            else if (c == '.') dotAt = i;
        }
        for (int i = dend; i < len; i++) {
            char c = dest.charAt(i);
            if (c == '-' || c == '+') signAt = i;
            else if (c == '.') dotAt = i;
        }
        StringBuilder out = null;
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            boolean drop = false;
            if (c == '-' || c == '+') {
                if (!mSign || dstart + i != 0 || signAt >= 0) drop = true;
                else signAt = dstart + i;
            } else if (c == '.') {
                if (!mDecimal || dotAt >= 0) drop = true;
                else dotAt = dstart + i;
            }
            if (drop) {
                if (out == null) {
                    out = new StringBuilder();
                    out.append(in, 0, i);
                }
            } else if (out != null) {
                out.append(c);
            }
        }
        if (out == null) return stripped;
        if (out.length() == 0) return "";
        return out;
    }
}
