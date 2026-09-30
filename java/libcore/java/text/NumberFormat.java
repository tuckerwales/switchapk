package java.text;

import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

public abstract class NumberFormat extends Format {
    public static final int INTEGER_FIELD = 0;
    public static final int FRACTION_FIELD = 1;

    private boolean groupingUsed = true;
    private int maximumIntegerDigits = 40;
    private int minimumIntegerDigits = 1;
    private int maximumFractionDigits = 3;
    private int minimumFractionDigits = 0;
    private boolean parseIntegerOnly = false;
    RoundingMode roundingMode = RoundingMode.HALF_EVEN;

    protected NumberFormat() {
    }

    public StringBuffer format(Object number, StringBuffer toAppendTo, FieldPosition pos) {
        if (number instanceof Long || number instanceof Integer || number instanceof Short || number instanceof Byte
                || number instanceof java.util.concurrent.atomic.AtomicInteger
                || number instanceof java.util.concurrent.atomic.AtomicLong
                || (number instanceof java.math.BigInteger && ((java.math.BigInteger) number).bitLength() < 64)) {
            return format(((Number) number).longValue(), toAppendTo, pos);
        } else if (number instanceof Number) {
            return format(((Number) number).doubleValue(), toAppendTo, pos);
        }
        throw new IllegalArgumentException("Cannot format given Object as a Number");
    }

    public final Object parseObject(String source, ParsePosition pos) {
        return parse(source, pos);
    }

    public final String format(double number) {
        return format(number, new StringBuffer(), new FieldPosition(0)).toString();
    }

    public final String format(long number) {
        return format(number, new StringBuffer(), new FieldPosition(0)).toString();
    }

    public abstract StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos);

    public abstract StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos);

    public abstract Number parse(String source, ParsePosition parsePosition);

    public Number parse(String source) throws ParseException {
        ParsePosition parsePosition = new ParsePosition(0);
        Number result = parse(source, parsePosition);
        if (parsePosition.index == 0) {
            throw new ParseException("Unparseable number: \"" + source + "\"", parsePosition.errorIndex);
        }
        return result;
    }

    public boolean isParseIntegerOnly() {
        return parseIntegerOnly;
    }

    public void setParseIntegerOnly(boolean value) {
        parseIntegerOnly = value;
    }

    public static final NumberFormat getInstance() {
        return getNumberInstance();
    }

    public static NumberFormat getInstance(Locale inLocale) {
        return getNumberInstance();
    }

    public static final NumberFormat getNumberInstance() {
        return new DecimalFormat("#,##0.###");
    }

    public static NumberFormat getNumberInstance(Locale inLocale) {
        return getNumberInstance();
    }

    public static final NumberFormat getIntegerInstance() {
        DecimalFormat f = new DecimalFormat("#,##0");
        f.setParseIntegerOnly(true);
        f.setRoundingMode(RoundingMode.HALF_EVEN);
        return f;
    }

    public static NumberFormat getIntegerInstance(Locale inLocale) {
        return getIntegerInstance();
    }

    public static final NumberFormat getCurrencyInstance() {
        return new DecimalFormat("¤#,##0.00");
    }

    public static NumberFormat getCurrencyInstance(Locale inLocale) {
        return getCurrencyInstance();
    }

    public static final NumberFormat getPercentInstance() {
        return new DecimalFormat("#,##0%");
    }

    public static NumberFormat getPercentInstance(Locale inLocale) {
        return getPercentInstance();
    }

    public static Locale[] getAvailableLocales() {
        return new Locale[] {Locale.US};
    }

    public boolean isGroupingUsed() {
        return groupingUsed;
    }

    public void setGroupingUsed(boolean newValue) {
        groupingUsed = newValue;
    }

    public int getMaximumIntegerDigits() {
        return maximumIntegerDigits;
    }

    public void setMaximumIntegerDigits(int newValue) {
        maximumIntegerDigits = Math.max(0, newValue);
        if (minimumIntegerDigits > maximumIntegerDigits) {
            minimumIntegerDigits = maximumIntegerDigits;
        }
    }

    public int getMinimumIntegerDigits() {
        return minimumIntegerDigits;
    }

    public void setMinimumIntegerDigits(int newValue) {
        minimumIntegerDigits = Math.max(0, newValue);
        if (minimumIntegerDigits > maximumIntegerDigits) {
            maximumIntegerDigits = minimumIntegerDigits;
        }
    }

    public int getMaximumFractionDigits() {
        return maximumFractionDigits;
    }

    public void setMaximumFractionDigits(int newValue) {
        maximumFractionDigits = Math.max(0, newValue);
        if (maximumFractionDigits < minimumFractionDigits) {
            minimumFractionDigits = maximumFractionDigits;
        }
    }

    public int getMinimumFractionDigits() {
        return minimumFractionDigits;
    }

    public void setMinimumFractionDigits(int newValue) {
        minimumFractionDigits = Math.max(0, newValue);
        if (maximumFractionDigits < minimumFractionDigits) {
            maximumFractionDigits = minimumFractionDigits;
        }
    }

    public Currency getCurrency() {
        return Currency.getInstance("USD");
    }

    public void setCurrency(Currency currency) {
    }

    public RoundingMode getRoundingMode() {
        return roundingMode;
    }

    public void setRoundingMode(RoundingMode roundingMode) {
        this.roundingMode = roundingMode;
    }

    public static class Field extends Format.Field {
        protected Field(String name) {
            super(name);
        }

        public static final Field INTEGER = new Field("integer");
        public static final Field FRACTION = new Field("fraction");
    }
}
