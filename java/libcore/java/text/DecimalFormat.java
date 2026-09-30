package java.text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public class DecimalFormat extends NumberFormat {
    private String positivePrefix = "";
    private String positiveSuffix = "";
    private String negativePrefix = "-";
    private String negativeSuffix = "";
    private int multiplier = 1;
    private int groupingSize = 3;
    private boolean decimalSeparatorAlwaysShown = false;
    private boolean parseBigDecimal = false;
    private DecimalFormatSymbols symbols = new DecimalFormatSymbols();
    private String pattern;

    public DecimalFormat() {
        this("#,##0.###");
    }

    public DecimalFormat(String pattern) {
        applyPattern(pattern);
    }

    public DecimalFormat(String pattern, DecimalFormatSymbols symbols) {
        this.symbols = (DecimalFormatSymbols) symbols.clone();
        applyPattern(pattern);
    }

    public void applyPattern(String pattern) {
        this.pattern = pattern;
        String pos = pattern;
        String neg = null;
        int semi = pattern.indexOf(';');
        if (semi >= 0) {
            pos = pattern.substring(0, semi);
            neg = pattern.substring(semi + 1);
        }
        int start = 0;
        while (start < pos.length() && "#0,.".indexOf(pos.charAt(start)) < 0) {
            start++;
        }
        int end = start;
        while (end < pos.length() && "#0,.E".indexOf(pos.charAt(end)) >= 0) {
            end++;
        }
        positivePrefix = unquote(pos.substring(0, start));
        positiveSuffix = unquote(pos.substring(end));
        String num = pos.substring(start, end);
        int e = num.indexOf('E');
        if (e >= 0) {
            num = num.substring(0, e);
        }
        multiplier = 1;
        if (positiveSuffix.indexOf('%') >= 0 || positivePrefix.indexOf('%') >= 0) {
            multiplier = 100;
        } else if (positiveSuffix.indexOf('‰') >= 0) {
            multiplier = 1000;
        }
        positivePrefix = positivePrefix.replace("¤", symbols.getCurrencySymbol());
        positiveSuffix = positiveSuffix.replace("¤", symbols.getCurrencySymbol());
        int dot = num.indexOf('.');
        String intPart = dot >= 0 ? num.substring(0, dot) : num;
        String fracPart = dot >= 0 ? num.substring(dot + 1) : "";
        int comma = intPart.lastIndexOf(',');
        setGroupingUsed(comma >= 0);
        if (comma >= 0) {
            groupingSize = intPart.length() - comma - 1;
        }
        int minInt = 0;
        for (char c : intPart.toCharArray()) {
            if (c == '0') {
                minInt++;
            }
        }
        int minFrac = 0, maxFrac = 0;
        for (char c : fracPart.toCharArray()) {
            if (c == '0') {
                minFrac++;
                maxFrac++;
            } else if (c == '#') {
                maxFrac++;
            }
        }
        setMaximumIntegerDigits(Integer.MAX_VALUE);
        setMinimumIntegerDigits(minInt);
        setMaximumFractionDigits(maxFrac);
        setMinimumFractionDigits(minFrac);
        decimalSeparatorAlwaysShown = dot >= 0 && fracPart.isEmpty();
        if (neg != null) {
            int ns = 0;
            while (ns < neg.length() && "#0,.".indexOf(neg.charAt(ns)) < 0) {
                ns++;
            }
            int ne = ns;
            while (ne < neg.length() && "#0,.E".indexOf(neg.charAt(ne)) >= 0) {
                ne++;
            }
            negativePrefix = unquote(neg.substring(0, ns));
            negativeSuffix = unquote(neg.substring(ne));
        } else {
            negativePrefix = "-" + positivePrefix;
            negativeSuffix = positiveSuffix;
        }
    }

    private static String unquote(String s) {
        StringBuilder sb = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                if (i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                    sb.append('\'');
                    i++;
                } else {
                    inQuote = !inQuote;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public void applyLocalizedPattern(String pattern) {
        applyPattern(pattern);
    }

    public String toPattern() {
        return pattern;
    }

    public String toLocalizedPattern() {
        return pattern;
    }

    public StringBuffer format(double number, StringBuffer result, FieldPosition fieldPosition) {
        if (Double.isNaN(number)) {
            return result.append(symbols.getNaN());
        }
        boolean neg = number < 0 || (number == 0.0 && 1 / number < 0);
        if (Double.isInfinite(number)) {
            return result.append(neg ? negativePrefix : positivePrefix).append(symbols.getInfinity())
                    .append(neg ? negativeSuffix : positiveSuffix);
        }
        BigDecimal bd = new BigDecimal(Double.toString(Math.abs(number)));
        return formatBig(bd.multiply(BigDecimal.valueOf(multiplier)), neg, result);
    }

    public StringBuffer format(long number, StringBuffer result, FieldPosition fieldPosition) {
        boolean neg = number < 0;
        BigDecimal bd = BigDecimal.valueOf(number).abs();
        return formatBig(bd.multiply(BigDecimal.valueOf(multiplier)), neg, result);
    }

    public StringBuffer format(Object number, StringBuffer toAppendTo, FieldPosition pos) {
        if (number instanceof BigDecimal) {
            BigDecimal b = (BigDecimal) number;
            return formatBig(b.abs().multiply(BigDecimal.valueOf(multiplier)), b.signum() < 0, toAppendTo);
        }
        return super.format(number, toAppendTo, pos);
    }

    private StringBuffer formatBig(BigDecimal value, boolean neg, StringBuffer result) {
        value = value.setScale(getMaximumFractionDigits(), getRoundingMode());
        String plain = value.toPlainString();
        int dot = plain.indexOf('.');
        String ip = dot >= 0 ? plain.substring(0, dot) : plain;
        String fp = dot >= 0 ? plain.substring(dot + 1) : "";
        while (fp.length() > getMinimumFractionDigits() && fp.endsWith("0")) {
            fp = fp.substring(0, fp.length() - 1);
        }
        while (ip.length() > 1 && ip.startsWith("0")) {
            ip = ip.substring(1);
        }
        if (ip.equals("0") && getMinimumIntegerDigits() == 0 && !fp.isEmpty()) {
            ip = "";
        }
        while (ip.length() < getMinimumIntegerDigits()) {
            ip = "0" + ip;
        }
        if (isGroupingUsed() && groupingSize > 0) {
            StringBuilder g = new StringBuilder();
            int len = ip.length();
            for (int i = 0; i < len; i++) {
                if (i > 0 && (len - i) % groupingSize == 0) {
                    g.append(symbols.getGroupingSeparator());
                }
                g.append(ip.charAt(i));
            }
            ip = g.toString();
        }
        boolean isZero = value.signum() == 0;
        result.append(neg && !isZero ? negativePrefix : positivePrefix);
        result.append(ip);
        if (!fp.isEmpty() || decimalSeparatorAlwaysShown) {
            result.append(symbols.getDecimalSeparator()).append(fp);
        }
        result.append(neg && !isZero ? negativeSuffix : positiveSuffix);
        return result;
    }

    public Number parse(String text, ParsePosition pos) {
        int i = pos.index;
        boolean neg = false;
        if (text.startsWith(negativePrefix, i) && !negativePrefix.isEmpty()) {
            neg = true;
            i += negativePrefix.length();
        } else if (text.startsWith(positivePrefix, i)) {
            i += positivePrefix.length();
        }
        StringBuilder digits = new StringBuilder();
        boolean seenDot = false;
        int start = i;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (c == symbols.getDecimalSeparator() && !seenDot && !isParseIntegerOnly()) {
                digits.append('.');
                seenDot = true;
            } else if (c == symbols.getGroupingSeparator() && isGroupingUsed()) {
                // skip
            } else if ((c == 'E' || c == 'e') && digits.length() > 0) {
                digits.append('E');
                if (i + 1 < text.length() && (text.charAt(i + 1) == '-' || text.charAt(i + 1) == '+')) {
                    digits.append(text.charAt(++i));
                }
            } else {
                break;
            }
            i++;
        }
        if (digits.length() == 0 || digits.toString().equals(".")) {
            pos.errorIndex = start;
            return null;
        }
        String suffix = neg ? negativeSuffix : positiveSuffix;
        if (!suffix.isEmpty() && text.startsWith(suffix, i)) {
            i += suffix.length();
        }
        pos.index = i;
        BigDecimal bd = new BigDecimal(digits.toString());
        if (multiplier != 1) {
            bd = bd.divide(BigDecimal.valueOf(multiplier));
        }
        if (neg) {
            bd = bd.negate();
        }
        if (parseBigDecimal) {
            return bd;
        }
        if (bd.scale() <= 0 || bd.stripTrailingZeros().scale() <= 0) {
            try {
                long l = bd.longValueExact();
                if (!(l == 0 && neg)) {
                    return Long.valueOf(l);
                }
            } catch (ArithmeticException e) {
                // fall through
            }
        }
        return Double.valueOf(bd.doubleValue());
    }

    public DecimalFormatSymbols getDecimalFormatSymbols() {
        return (DecimalFormatSymbols) symbols.clone();
    }

    public void setDecimalFormatSymbols(DecimalFormatSymbols newSymbols) {
        symbols = (DecimalFormatSymbols) newSymbols.clone();
    }

    public String getPositivePrefix() {
        return positivePrefix;
    }

    public void setPositivePrefix(String s) {
        positivePrefix = s;
    }

    public String getNegativePrefix() {
        return negativePrefix;
    }

    public void setNegativePrefix(String s) {
        negativePrefix = s;
    }

    public String getPositiveSuffix() {
        return positiveSuffix;
    }

    public void setPositiveSuffix(String s) {
        positiveSuffix = s;
    }

    public String getNegativeSuffix() {
        return negativeSuffix;
    }

    public void setNegativeSuffix(String s) {
        negativeSuffix = s;
    }

    public int getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(int newValue) {
        multiplier = newValue;
    }

    public int getGroupingSize() {
        return groupingSize;
    }

    public void setGroupingSize(int newValue) {
        groupingSize = newValue;
    }

    public boolean isDecimalSeparatorAlwaysShown() {
        return decimalSeparatorAlwaysShown;
    }

    public void setDecimalSeparatorAlwaysShown(boolean newValue) {
        decimalSeparatorAlwaysShown = newValue;
    }

    public boolean isParseBigDecimal() {
        return parseBigDecimal;
    }

    public void setParseBigDecimal(boolean newValue) {
        parseBigDecimal = newValue;
    }
}
