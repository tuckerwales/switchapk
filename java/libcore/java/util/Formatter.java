package java.util;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

public final class Formatter implements Closeable, Flushable {
    private final Appendable a;
    private final Locale l;
    private IOException lastException;

    public Formatter() {
        this(new StringBuilder(), Locale.getDefault());
    }

    public Formatter(Appendable a) {
        this(a == null ? new StringBuilder() : a, Locale.getDefault());
    }

    public Formatter(Locale l) {
        this(new StringBuilder(), l);
    }

    public Formatter(Appendable a, Locale l) {
        this.a = a == null ? new StringBuilder() : a;
        this.l = l;
    }

    public Formatter(java.io.PrintStream ps) {
        this((Appendable) ps, Locale.getDefault());
    }

    public Locale locale() {
        return l;
    }

    public Appendable out() {
        return a;
    }

    public String toString() {
        return a.toString();
    }

    public void flush() {
        if (a instanceof Flushable) {
            try {
                ((Flushable) a).flush();
            } catch (IOException e) {
                lastException = e;
            }
        }
    }

    public void close() {
        if (a instanceof Closeable) {
            try {
                ((Closeable) a).close();
            } catch (IOException e) {
                lastException = e;
            }
        }
    }

    public IOException ioException() {
        return lastException;
    }

    static native String formatDouble(String spec, double value);

    public Formatter format(String format, Object... args) {
        return format(l, format, args);
    }

    public Formatter format(Locale l, String format, Object... args) {
        try {
            a.append(doFormat(format, args));
        } catch (IOException e) {
            lastException = e;
        }
        return this;
    }

    private static String doFormat(String format, Object[] args) {
        if (args == null) {
            args = new Object[] {null};
        }
        StringBuilder sb = new StringBuilder(format.length() + 16);
        int argIndex = 0;
        int lastArg = -1;
        int i = 0;
        int n = format.length();
        while (i < n) {
            char c = format.charAt(i);
            if (c != '%') {
                sb.append(c);
                i++;
                continue;
            }
            int start = i;
            i++;
            if (i >= n) {
                throw new UnknownFormatConversionException("%");
            }
            // explicit index
            int explicit = -1;
            int j = i;
            while (j < n && Character.isDigit(format.charAt(j))) {
                j++;
            }
            if (j < n && j > i && format.charAt(j) == '$') {
                explicit = Integer.parseInt(format.substring(i, j)) - 1;
                i = j + 1;
            }
            boolean relative = false;
            if (i < n && format.charAt(i) == '<') {
                relative = true;
                i++;
            }
            String flags = "";
            while (i < n && "-#+ 0,(".indexOf(format.charAt(i)) >= 0) {
                flags += format.charAt(i);
                i++;
            }
            int width = -1;
            j = i;
            while (j < n && Character.isDigit(format.charAt(j))) {
                j++;
            }
            if (j > i) {
                width = Integer.parseInt(format.substring(i, j));
                i = j;
            }
            int precision = -1;
            if (i < n && format.charAt(i) == '.') {
                i++;
                j = i;
                while (j < n && Character.isDigit(format.charAt(j))) {
                    j++;
                }
                precision = j > i ? Integer.parseInt(format.substring(i, j)) : 0;
                i = j;
            }
            if (i >= n) {
                throw new UnknownFormatConversionException(format.substring(start));
            }
            char conv = format.charAt(i++);
            boolean dateTime = false;
            char dtConv = 0;
            if (conv == 't' || conv == 'T') {
                dateTime = true;
                if (i >= n) {
                    throw new UnknownFormatConversionException(format.substring(start));
                }
                dtConv = format.charAt(i++);
            }
            if (conv == '%') {
                sb.append(pad("%", flags, width));
                continue;
            }
            if (conv == 'n') {
                sb.append('\n');
                continue;
            }
            Object arg;
            int use;
            if (relative) {
                use = lastArg;
            } else if (explicit >= 0) {
                use = explicit;
            } else {
                use = argIndex++;
            }
            if (use < 0 || use >= args.length) {
                throw new MissingFormatArgumentException("Format specifier '" + format.substring(start, i) + "'");
            }
            lastArg = use;
            arg = args[use];
            String s;
            if (dateTime) {
                s = formatDate(arg, dtConv);
                if (conv == 'T') {
                    s = s.toUpperCase();
                }
                sb.append(pad(s, flags, width));
                continue;
            }
            switch (conv) {
                case 'b':
                case 'B':
                    s = arg == null ? "false" : (arg instanceof Boolean ? arg.toString() : "true");
                    s = precision(s, precision);
                    break;
                case 'h':
                case 'H':
                    s = arg == null ? "null" : Integer.toHexString(arg.hashCode());
                    break;
                case 's':
                case 'S':
                    s = precision(String.valueOf(arg), precision);
                    break;
                case 'c':
                case 'C':
                    if (arg == null) {
                        s = "null";
                    } else if (arg instanceof Character) {
                        s = arg.toString();
                    } else if (arg instanceof Number) {
                        s = new String(Character.toChars(((Number) arg).intValue()));
                    } else {
                        throw new IllegalFormatConversionException("c != " + arg.getClass().getName());
                    }
                    break;
                case 'd':
                    s = arg == null ? "null" : formatInteger(arg, flags);
                    break;
                case 'o':
                case 'x':
                case 'X':
                    s = arg == null ? "null" : formatRadix(arg, conv == 'o' ? 8 : 16, flags);
                    break;
                case 'e':
                case 'E':
                case 'f':
                case 'g':
                case 'G':
                case 'a':
                case 'A':
                    s = arg == null ? "null" : formatFloat(arg, conv, flags, precision);
                    break;
                default:
                    throw new UnknownFormatConversionException(String.valueOf(conv));
            }
            if (Character.isUpperCase(conv)) {
                s = s.toUpperCase();
            }
            if (flags.indexOf('0') >= 0 && width > 0 && flags.indexOf('-') < 0 && "doxXeEfgGaA".indexOf(conv) >= 0) {
                s = zeroPad(s, width);
            }
            sb.append(pad(s, flags, width));
        }
        return sb.toString();
    }

    private static String precision(String s, int precision) {
        return (precision >= 0 && precision < s.length()) ? s.substring(0, precision) : s;
    }

    private static String pad(String s, String flags, int width) {
        if (width <= s.length()) {
            return s;
        }
        StringBuilder sb = new StringBuilder(width);
        boolean left = flags.indexOf('-') >= 0;
        if (left) {
            sb.append(s);
        }
        for (int i = s.length(); i < width; i++) {
            sb.append(' ');
        }
        if (!left) {
            sb.append(s);
        }
        return sb.toString();
    }

    private static String zeroPad(String s, int width) {
        if (s.length() >= width) {
            return s;
        }
        int signLen = 0;
        if (s.startsWith("-") || s.startsWith("+") || s.startsWith(" ") || s.startsWith("(")) {
            signLen = 1;
        }
        if (s.startsWith("0x", signLen) || s.startsWith("0X", signLen)) {
            signLen += 2;
        }
        StringBuilder sb = new StringBuilder(width);
        sb.append(s, 0, signLen);
        for (int i = s.length(); i < width; i++) {
            sb.append('0');
        }
        sb.append(s, signLen, s.length());
        return sb.toString();
    }

    private static String group(String digits) {
        StringBuilder sb = new StringBuilder();
        int len = digits.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                sb.append(',');
            }
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    private static String applySign(String digits, boolean neg, String flags) {
        if (neg) {
            return flags.indexOf('(') >= 0 ? "(" + digits + ")" : "-" + digits;
        }
        if (flags.indexOf('+') >= 0) {
            return "+" + digits;
        }
        if (flags.indexOf(' ') >= 0) {
            return " " + digits;
        }
        return digits;
    }

    private static String formatInteger(Object arg, String flags) {
        String digits;
        boolean neg;
        if (arg instanceof Integer || arg instanceof Long || arg instanceof Short || arg instanceof Byte) {
            long v = ((Number) arg).longValue();
            neg = v < 0;
            digits = neg ? (v == Long.MIN_VALUE ? "9223372036854775808" : Long.toString(-v)) : Long.toString(v);
        } else if (arg instanceof java.math.BigInteger) {
            java.math.BigInteger b = (java.math.BigInteger) arg;
            neg = b.signum() < 0;
            digits = b.abs().toString();
        } else {
            throw new IllegalFormatConversionException("d != " + arg.getClass().getName());
        }
        if (flags.indexOf(',') >= 0) {
            digits = group(digits);
        }
        return applySign(digits, neg, flags);
    }

    private static String formatRadix(Object arg, int radix, String flags) {
        String s;
        if (arg instanceof Integer) {
            int v = (Integer) arg;
            s = radix == 16 ? Integer.toHexString(v) : Integer.toOctalString(v);
        } else if (arg instanceof Long) {
            long v = (Long) arg;
            s = radix == 16 ? Long.toHexString(v) : Long.toOctalString(v);
        } else if (arg instanceof Short) {
            int v = ((Short) arg) & 0xffff;
            s = radix == 16 ? Integer.toHexString(v) : Integer.toOctalString(v);
        } else if (arg instanceof Byte) {
            int v = ((Byte) arg) & 0xff;
            s = radix == 16 ? Integer.toHexString(v) : Integer.toOctalString(v);
        } else if (arg instanceof java.math.BigInteger) {
            s = ((java.math.BigInteger) arg).toString(radix);
        } else {
            throw new IllegalFormatConversionException("x != " + arg.getClass().getName());
        }
        if (flags.indexOf('#') >= 0) {
            s = (radix == 16 ? "0x" : "0") + s;
        }
        return s;
    }

    private static String formatFloat(Object arg, char conv, String flags, int precision) {
        double v;
        if (arg instanceof Double || arg instanceof Float) {
            v = ((Number) arg).doubleValue();
        } else if (arg instanceof java.math.BigDecimal) {
            v = ((java.math.BigDecimal) arg).doubleValue();
        } else {
            throw new IllegalFormatConversionException(conv + " != " + arg.getClass().getName());
        }
        if (Double.isNaN(v)) {
            return "NaN";
        }
        if (Double.isInfinite(v)) {
            return applySign("Infinity", v < 0, flags);
        }
        char c = Character.toLowerCase(conv);
        if (precision < 0) {
            precision = 6;
        }
        if (c == 'g' && precision == 0) {
            precision = 1;
        }
        boolean neg = v < 0 || (v == 0 && 1 / v < 0);
        String spec = "%" + (flags.indexOf('#') >= 0 ? "#" : "") + "." + precision + c;
        String digits = formatDouble(spec, Math.abs(v));
        if (flags.indexOf(',') >= 0 && c == 'f') {
            int dot = digits.indexOf('.');
            String ip = dot >= 0 ? digits.substring(0, dot) : digits;
            digits = group(ip) + (dot >= 0 ? digits.substring(dot) : "");
        }
        return applySign(digits, neg, flags);
    }

    private static String twoDigits(int v) {
        return v < 10 ? "0" + v : Integer.toString(v);
    }

    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August",
        "September", "October", "November", "December"};
    private static final String[] DAYS = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    private static String formatDate(Object arg, char c) {
        Calendar cal;
        if (arg instanceof Calendar) {
            cal = (Calendar) arg;
        } else {
            long millis;
            if (arg instanceof Long) {
                millis = (Long) arg;
            } else if (arg instanceof Date) {
                millis = ((Date) arg).getTime();
            } else {
                throw new IllegalFormatConversionException("t != " + (arg == null ? "null" : arg.getClass().getName()));
            }
            cal = Calendar.getInstance();
            cal.setTimeInMillis(millis);
        }
        switch (c) {
            case 'H': return twoDigits(cal.get(Calendar.HOUR_OF_DAY));
            case 'I': {
                int h = cal.get(Calendar.HOUR);
                return twoDigits(h == 0 ? 12 : h);
            }
            case 'k': return Integer.toString(cal.get(Calendar.HOUR_OF_DAY));
            case 'l': {
                int h = cal.get(Calendar.HOUR);
                return Integer.toString(h == 0 ? 12 : h);
            }
            case 'M': return twoDigits(cal.get(Calendar.MINUTE));
            case 'S': return twoDigits(cal.get(Calendar.SECOND));
            case 'L': {
                int ms = cal.get(Calendar.MILLISECOND);
                return (ms < 100 ? (ms < 10 ? "00" : "0") : "") + ms;
            }
            case 'p': return cal.get(Calendar.AM_PM) == 0 ? "am" : "pm";
            case 'B': return MONTHS[cal.get(Calendar.MONTH)];
            case 'b': case 'h': return MONTHS[cal.get(Calendar.MONTH)].substring(0, 3);
            case 'A': return DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1];
            case 'a': return DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1].substring(0, 3);
            case 'Y': return Integer.toString(cal.get(Calendar.YEAR));
            case 'y': return twoDigits(cal.get(Calendar.YEAR) % 100);
            case 'm': return twoDigits(cal.get(Calendar.MONTH) + 1);
            case 'd': return twoDigits(cal.get(Calendar.DAY_OF_MONTH));
            case 'e': return Integer.toString(cal.get(Calendar.DAY_OF_MONTH));
            case 'j': {
                int d = cal.get(Calendar.DAY_OF_YEAR);
                return (d < 100 ? (d < 10 ? "00" : "0") : "") + d;
            }
            case 's': return Long.toString(cal.getTimeInMillis() / 1000);
            case 'Q': return Long.toString(cal.getTimeInMillis());
            case 'Z': return "UTC";
            case 'z': return "+0000";
            case 'R': return formatDate(cal, 'H') + ":" + formatDate(cal, 'M');
            case 'T': return formatDate(cal, 'H') + ":" + formatDate(cal, 'M') + ":" + formatDate(cal, 'S');
            case 'r': return formatDate(cal, 'I') + ":" + formatDate(cal, 'M') + ":" + formatDate(cal, 'S') + " " + formatDate(cal, 'p').toUpperCase();
            case 'D': return formatDate(cal, 'm') + "/" + formatDate(cal, 'd') + "/" + formatDate(cal, 'y');
            case 'F': return formatDate(cal, 'Y') + "-" + formatDate(cal, 'm') + "-" + formatDate(cal, 'd');
            case 'c': return formatDate(cal, 'a') + " " + formatDate(cal, 'b') + " " + formatDate(cal, 'd') + " "
                    + formatDate(cal, 'T') + " UTC " + formatDate(cal, 'Y');
            default:
                throw new UnknownFormatConversionException("t" + c);
        }
    }

    public enum BigDecimalLayoutForm {
        SCIENTIFIC, DECIMAL_FLOAT
    }
}
