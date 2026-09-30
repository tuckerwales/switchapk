package java.text;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class SimpleDateFormat extends DateFormat {
    private String pattern;
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August",
        "September", "October", "November", "December"};
    private static final String[] DAYS = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    public SimpleDateFormat() {
        this("M/d/yy h:mm a");
    }

    public SimpleDateFormat(String pattern) {
        this.pattern = pattern;
    }

    public SimpleDateFormat(String pattern, Locale locale) {
        this(pattern);
    }

    public SimpleDateFormat(String pattern, DateFormatSymbols formatSymbols) {
        this(pattern);
    }

    public void applyPattern(String pattern) {
        this.pattern = pattern;
    }

    public void applyLocalizedPattern(String pattern) {
        this.pattern = pattern;
    }

    public String toPattern() {
        return pattern;
    }

    public String toLocalizedPattern() {
        return pattern;
    }

    private static String pad(int v, int width) {
        String s = Integer.toString(v);
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < width; i++) {
            sb.append('0');
        }
        return sb.append(s).toString();
    }

    public StringBuffer format(Date date, StringBuffer out, FieldPosition pos) {
        Calendar c = (Calendar) calendar.clone();
        c.setTime(date);
        int i = 0;
        int n = pattern.length();
        while (i < n) {
            char ch = pattern.charAt(i);
            if (ch == '\'') {
                int end = pattern.indexOf('\'', i + 1);
                if (end == i + 1) {
                    out.append('\'');
                    i += 2;
                    continue;
                }
                if (end < 0) {
                    end = n;
                }
                out.append(pattern, i + 1, end);
                i = end + 1;
                continue;
            }
            if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) {
                out.append(ch);
                i++;
                continue;
            }
            int count = 1;
            while (i + count < n && pattern.charAt(i + count) == ch) {
                count++;
            }
            i += count;
            switch (ch) {
                case 'G': out.append(c.get(Calendar.ERA) == 1 ? "AD" : "BC"); break;
                case 'y':
                case 'Y':
                    if (count == 2) {
                        out.append(pad(c.get(Calendar.YEAR) % 100, 2));
                    } else {
                        out.append(pad(c.get(Calendar.YEAR), count));
                    }
                    break;
                case 'M':
                case 'L':
                    if (count >= 4) {
                        out.append(MONTHS[c.get(Calendar.MONTH)]);
                    } else if (count == 3) {
                        out.append(MONTHS[c.get(Calendar.MONTH)], 0, 3);
                    } else {
                        out.append(pad(c.get(Calendar.MONTH) + 1, count));
                    }
                    break;
                case 'd': out.append(pad(c.get(Calendar.DAY_OF_MONTH), count)); break;
                case 'D': out.append(pad(c.get(Calendar.DAY_OF_YEAR), count)); break;
                case 'E':
                    if (count >= 4) {
                        out.append(DAYS[c.get(Calendar.DAY_OF_WEEK) - 1]);
                    } else {
                        out.append(DAYS[c.get(Calendar.DAY_OF_WEEK) - 1], 0, 3);
                    }
                    break;
                case 'u': out.append(((c.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1); break;
                case 'a': out.append(c.get(Calendar.AM_PM) == 0 ? "AM" : "PM"); break;
                case 'H': out.append(pad(c.get(Calendar.HOUR_OF_DAY), count)); break;
                case 'k': {
                    int h = c.get(Calendar.HOUR_OF_DAY);
                    out.append(pad(h == 0 ? 24 : h, count));
                    break;
                }
                case 'K': out.append(pad(c.get(Calendar.HOUR), count)); break;
                case 'h': {
                    int h = c.get(Calendar.HOUR);
                    out.append(pad(h == 0 ? 12 : h, count));
                    break;
                }
                case 'm': out.append(pad(c.get(Calendar.MINUTE), count)); break;
                case 's': out.append(pad(c.get(Calendar.SECOND), count)); break;
                case 'S': out.append(pad(c.get(Calendar.MILLISECOND), count).substring(0, Math.min(count, 3))); break;
                case 'z': out.append(count >= 4 ? "Coordinated Universal Time" : "UTC"); break;
                case 'Z': out.append("+0000"); break;
                case 'X': out.append(count == 1 ? "Z" : "Z"); break;
                case 'w': out.append(pad(c.get(Calendar.WEEK_OF_YEAR), count)); break;
                case 'W': out.append(pad(c.get(Calendar.WEEK_OF_MONTH), count)); break;
                case 'F': out.append(pad(c.get(Calendar.DAY_OF_WEEK_IN_MONTH), count)); break;
                default:
                    throw new IllegalArgumentException("Illegal pattern character '" + ch + "'");
            }
        }
        return out;
    }

    public Date parse(String text, ParsePosition pos) {
        Calendar c = (Calendar) calendar.clone();
        c.clear();
        int ti = pos.index;
        int i = 0;
        int n = pattern.length();
        boolean pm = false;
        boolean hasAmPm = false;
        int hour12 = -1;
        try {
            while (i < n) {
                char ch = pattern.charAt(i);
                if (ch == '\'') {
                    int end = pattern.indexOf('\'', i + 1);
                    if (end < 0) {
                        end = n;
                    }
                    String lit = pattern.substring(i + 1, end);
                    if (!text.startsWith(lit, ti)) {
                        pos.errorIndex = ti;
                        return null;
                    }
                    ti += lit.length();
                    i = end + 1;
                    continue;
                }
                if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) {
                    if (ti >= text.length() || text.charAt(ti) != ch) {
                        pos.errorIndex = ti;
                        return null;
                    }
                    ti++;
                    i++;
                    continue;
                }
                int count = 1;
                while (i + count < n && pattern.charAt(i + count) == ch) {
                    count++;
                }
                i += count;
                boolean nextIsDigitField = i < n && Character.isLetter(pattern.charAt(i));
                if ((ch == 'M' || ch == 'L') && count >= 3 || ch == 'E' || ch == 'a' || ch == 'G' || ch == 'z') {
                    int end = ti;
                    while (end < text.length() && Character.isLetter(text.charAt(end))) {
                        end++;
                    }
                    String word = text.substring(ti, end).toLowerCase();
                    ti = end;
                    if (ch == 'M' || ch == 'L') {
                        for (int m = 0; m < 12; m++) {
                            if (MONTHS[m].toLowerCase().startsWith(word) && word.length() >= 3) {
                                c.set(Calendar.MONTH, m);
                            }
                        }
                    } else if (ch == 'a') {
                        hasAmPm = true;
                        pm = word.startsWith("p");
                    }
                    continue;
                }
                if (ch == 'Z' || ch == 'X') {
                    int end = ti;
                    while (end < text.length() && "+-0123456789:Z".indexOf(text.charAt(end)) >= 0) {
                        end++;
                    }
                    ti = end;
                    continue;
                }
                int end = ti;
                int maxLen = nextIsDigitField ? count : Integer.MAX_VALUE;
                if (end < text.length() && (text.charAt(end) == '-' || text.charAt(end) == '+')) {
                    end++;
                }
                while (end < text.length() && Character.isDigit(text.charAt(end)) && end - ti < maxLen) {
                    end++;
                }
                if (end == ti) {
                    pos.errorIndex = ti;
                    return null;
                }
                int v = Integer.parseInt(text.substring(ti, end));
                ti = end;
                switch (ch) {
                    case 'y':
                    case 'Y':
                        if (count <= 2 && v < 100) {
                            v += v < 70 ? 2000 : 1900;
                        }
                        c.set(Calendar.YEAR, v);
                        break;
                    case 'M':
                    case 'L': c.set(Calendar.MONTH, v - 1); break;
                    case 'd': c.set(Calendar.DAY_OF_MONTH, v); break;
                    case 'H':
                    case 'k': c.set(Calendar.HOUR_OF_DAY, v == 24 ? 0 : v); break;
                    case 'h':
                    case 'K': hour12 = v % 12; break;
                    case 'm': c.set(Calendar.MINUTE, v); break;
                    case 's': c.set(Calendar.SECOND, v); break;
                    case 'S': c.set(Calendar.MILLISECOND, v); break;
                    case 'D': c.set(Calendar.DAY_OF_YEAR, v); break;
                    default: break;
                }
            }
        } catch (NumberFormatException e) {
            pos.errorIndex = ti;
            return null;
        }
        if (hour12 >= 0) {
            c.set(Calendar.HOUR_OF_DAY, hour12 + (hasAmPm && pm ? 12 : 0));
        }
        pos.index = ti;
        return c.getTime();
    }

    public DateFormatSymbols getDateFormatSymbols() {
        return new DateFormatSymbols();
    }

    public void setDateFormatSymbols(DateFormatSymbols newFormatSymbols) {
    }

    public void set2DigitYearStart(Date startDate) {
    }
}
