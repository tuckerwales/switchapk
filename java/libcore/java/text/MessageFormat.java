package java.text;

import java.util.Locale;

public class MessageFormat extends Format {
    private String pattern;

    public MessageFormat(String pattern) {
        this.pattern = pattern;
    }

    public MessageFormat(String pattern, Locale locale) {
        this.pattern = pattern;
    }

    public void applyPattern(String pattern) {
        this.pattern = pattern;
    }

    public String toPattern() {
        return pattern;
    }

    public static String format(String pattern, Object... arguments) {
        return new MessageFormat(pattern).format(arguments);
    }

    public final StringBuffer format(Object[] arguments, StringBuffer result, FieldPosition pos) {
        int i = 0;
        int n = pattern.length();
        boolean quoted = false;
        while (i < n) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                if (i + 1 < n && pattern.charAt(i + 1) == '\'') {
                    result.append('\'');
                    i += 2;
                    continue;
                }
                quoted = !quoted;
                i++;
                continue;
            }
            if (c == '{' && !quoted) {
                int depth = 1;
                int j = i + 1;
                while (j < n && depth > 0) {
                    if (pattern.charAt(j) == '{') {
                        depth++;
                    } else if (pattern.charAt(j) == '}') {
                        depth--;
                    }
                    j++;
                }
                String spec = pattern.substring(i + 1, j - 1);
                String[] parts = spec.split(",", 3);
                int idx = Integer.parseInt(parts[0].trim());
                Object arg = arguments != null && idx < arguments.length ? arguments[idx] : null;
                if (arguments == null || idx >= arguments.length) {
                    result.append('{').append(spec).append('}');
                } else if (parts.length >= 2 && parts[1].trim().equals("number") && arg instanceof Number) {
                    NumberFormat nf = parts.length == 3 ? new DecimalFormat(parts[2].trim()) : NumberFormat.getInstance();
                    if (parts.length == 3 && parts[2].trim().equals("integer")) {
                        nf = NumberFormat.getIntegerInstance();
                    } else if (parts.length == 3 && parts[2].trim().equals("percent")) {
                        nf = NumberFormat.getPercentInstance();
                    }
                    result.append(nf.format(arg));
                } else if (parts.length >= 2 && (parts[1].trim().equals("date") || parts[1].trim().equals("time"))
                        && arg instanceof java.util.Date) {
                    result.append(DateFormat.getDateTimeInstance().format((java.util.Date) arg));
                } else if (arg instanceof Number) {
                    result.append(NumberFormat.getInstance().format(arg));
                } else if (arg instanceof java.util.Date) {
                    result.append(DateFormat.getDateTimeInstance().format((java.util.Date) arg));
                } else {
                    result.append(String.valueOf(arg));
                }
                i = j;
                continue;
            }
            result.append(c);
            i++;
        }
        return result;
    }

    public final StringBuffer format(Object arguments, StringBuffer result, FieldPosition pos) {
        return format((Object[]) arguments, result, pos);
    }

    public Object[] parse(String source, ParsePosition pos) {
        return new Object[0];
    }

    public Object parseObject(String source, ParsePosition pos) {
        return parse(source, pos);
    }
}
