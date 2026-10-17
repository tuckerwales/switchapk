package android.webkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Framework-internal: the in-memory cookie store behind CookieManager.getInstance(). Follows the RFC 6265
 * storage model: host-only and domain cookies, default-path and path matching, Expires and Max-Age, Secure
 * (only sent to https/wss and only set from them), HttpOnly (stored; WebView has no script to hide it from),
 * the __Secure- and __Host- name prefixes, and session cookies. Cookies do not survive the process. Thread-safe.
 */
final class CookieManagerImpl extends CookieManager {
    private static boolean sAcceptFileSchemeCookies;

    private final ArrayList<Cookie> mCookies = new ArrayList<Cookie>();
    private final WeakHashMap<WebView, Boolean> mThirdParty = new WeakHashMap<WebView, Boolean>();
    private boolean mAcceptCookie = true;
    private long mCreationCounter;

    private static final class Cookie {
        String name;
        String value;
        String domain;
        boolean hostOnly;
        String path;
        long expiry = Long.MAX_VALUE;
        boolean persistent;
        boolean secure;
        boolean httpOnly;
        long creation;
    }

    /** A parsed URL: lower-case scheme and host, path without query or fragment. */
    private static final class Url {
        String scheme;
        String host;
        String path;

        boolean isSecure() {
            return "https".equals(scheme) || "wss".equals(scheme);
        }
    }

    CookieManagerImpl() {
    }

    static synchronized boolean allowFileSchemeCookiesImpl() {
        return sAcceptFileSchemeCookies;
    }

    static synchronized void setAcceptFileSchemeCookiesImpl(boolean accept) {
        sAcceptFileSchemeCookies = accept;
    }

    // Framework-internal API for WebView's HTTP stack.

    /** The Cookie request header value for url, or null when there is nothing to send or cookies are off. */
    String cookieHeaderFor(String url) {
        if (!acceptCookie()) return null;
        return getCookie(url);
    }

    /** Stores every Set-Cookie header of a response for url (header names compared ignoring case). */
    void storeFromHeaders(String url, Map<String, List<String>> headers) {
        if (headers == null || !acceptCookie()) return;
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() == null || !e.getKey().equalsIgnoreCase("Set-Cookie") || e.getValue() == null) continue;
            for (String v : e.getValue()) {
                if (v != null) setCookieInternal(url, v);
            }
        }
    }

    // Public API.

    @Override
    public synchronized void setAcceptCookie(boolean accept) {
        mAcceptCookie = accept;
    }

    @Override
    public synchronized boolean acceptCookie() {
        return mAcceptCookie;
    }

    @Override
    public void setAcceptThirdPartyCookies(WebView webview, boolean accept) {
        if (webview == null) return;
        synchronized (mThirdParty) {
            mThirdParty.put(webview, accept);
        }
    }

    @Override
    public boolean acceptThirdPartyCookies(WebView webview) {
        if (webview == null) return false;
        synchronized (mThirdParty) {
            Boolean b = mThirdParty.get(webview);
            // Off by default for apps targeting Lollipop and later.
            return b != null && b;
        }
    }

    @Override
    public void setCookie(String url, String value) {
        setCookieInternal(url, value);
    }

    @Override
    public void setCookie(String url, String value, ValueCallback<Boolean> callback) {
        CallbackUtil.post(callback, setCookieInternal(url, value));
    }

    @Override
    public String getCookie(String url) {
        Url u = parseUrl(url);
        if (u == null) return null;
        long now = System.currentTimeMillis();
        ArrayList<Cookie> matches = new ArrayList<Cookie>();
        synchronized (this) {
            for (Iterator<Cookie> it = mCookies.iterator(); it.hasNext();) {
                Cookie c = it.next();
                if (c.expiry <= now) {
                    it.remove();
                    continue;
                }
                if (c.hostOnly ? !u.host.equals(c.domain) : !domainMatch(u.host, c.domain)) continue;
                if (!pathMatch(u.path, c.path)) continue;
                if (c.secure && !u.isSecure()) continue;
                matches.add(c);
            }
        }
        if (matches.isEmpty()) return null;
        // RFC 6265 5.4: longer paths first, then earlier creation.
        Collections.sort(matches, new Comparator<Cookie>() {
            @Override
            public int compare(Cookie a, Cookie b) {
                if (a.path.length() != b.path.length()) return b.path.length() - a.path.length();
                return a.creation < b.creation ? -1 : (a.creation == b.creation ? 0 : 1);
            }
        });
        StringBuilder sb = new StringBuilder();
        for (Cookie c : matches) {
            if (sb.length() > 0) sb.append("; ");
            if (c.name.length() > 0) sb.append(c.name).append('=');
            sb.append(c.value);
        }
        return sb.toString();
    }

    @Override
    public void removeSessionCookie() {
        removeSessionCookiesInternal();
    }

    @Override
    public void removeSessionCookies(ValueCallback<Boolean> callback) {
        CallbackUtil.post(callback, removeSessionCookiesInternal());
    }

    @Override
    public void removeAllCookie() {
        removeAllCookiesInternal();
    }

    @Override
    public void removeAllCookies(ValueCallback<Boolean> callback) {
        CallbackUtil.post(callback, removeAllCookiesInternal());
    }

    @Override
    public synchronized boolean hasCookies() {
        removeExpiredCookie();
        return !mCookies.isEmpty();
    }

    @Override
    public synchronized void removeExpiredCookie() {
        long now = System.currentTimeMillis();
        for (Iterator<Cookie> it = mCookies.iterator(); it.hasNext();) {
            if (it.next().expiry <= now) it.remove();
        }
    }

    @Override
    public void flush() {
        // In memory only: nothing to write.
    }

    private synchronized boolean removeSessionCookiesInternal() {
        boolean removed = false;
        for (Iterator<Cookie> it = mCookies.iterator(); it.hasNext();) {
            if (!it.next().persistent) {
                it.remove();
                removed = true;
            }
        }
        return removed;
    }

    private synchronized boolean removeAllCookiesInternal() {
        boolean removed = !mCookies.isEmpty();
        mCookies.clear();
        return removed;
    }

    // Storage model (RFC 6265 section 5.3).

    private boolean setCookieInternal(String url, String header) {
        Url u = parseUrl(url);
        if (u == null || header == null) return false;
        Cookie c = parseSetCookie(header, u);
        if (c == null) return false;
        long now = System.currentTimeMillis();
        synchronized (this) {
            c.creation = ++mCreationCounter;
            for (Iterator<Cookie> it = mCookies.iterator(); it.hasNext();) {
                Cookie old = it.next();
                if (old.name.equals(c.name) && old.domain.equals(c.domain) && old.path.equals(c.path)) {
                    c.creation = old.creation;
                    it.remove();
                    break;
                }
            }
            // An expired cookie only deletes the old one, as with "Max-Age=0".
            if (c.expiry > now) mCookies.add(c);
        }
        return true;
    }

    private static Cookie parseSetCookie(String header, Url u) {
        String[] parts = header.split(";", -1);
        String pair = parts[0];
        int eq = pair.indexOf('=');
        Cookie c = new Cookie();
        // As Chromium: a pair without '=' is a cookie with an empty name.
        c.name = eq < 0 ? "" : pair.substring(0, eq).trim();
        c.value = (eq < 0 ? pair : pair.substring(eq + 1)).trim();
        if (c.name.length() == 0 && c.value.length() == 0) return null;
        // Quotes around a value are part of it (RFC 6265), so they are kept.

        String domainAttr = null;
        String pathAttr = null;
        long maxAgeExpiry = Long.MIN_VALUE;
        long expiresExpiry = Long.MIN_VALUE;
        long now = System.currentTimeMillis();
        for (int i = 1; i < parts.length; i++) {
            String av = parts[i];
            int e = av.indexOf('=');
            String name = (e < 0 ? av : av.substring(0, e)).trim().toLowerCase(Locale.ROOT);
            String value = e < 0 ? "" : av.substring(e + 1).trim();
            if (name.equals("expires")) {
                long t = parseCookieDate(value);
                if (t != Long.MIN_VALUE) expiresExpiry = t;
            } else if (name.equals("max-age")) {
                if (value.length() == 0) continue;
                char first = value.charAt(0);
                if (first != '-' && (first < '0' || first > '9')) continue;
                long secs;
                try {
                    secs = Long.parseLong(value);
                } catch (NumberFormatException ex) {
                    // All digits but too large: effectively never expires.
                    secs = first == '-' ? -1 : Long.MAX_VALUE / 1000;
                }
                if (secs <= 0) maxAgeExpiry = Long.MIN_VALUE + 1;
                else maxAgeExpiry = secs >= (Long.MAX_VALUE - now) / 1000 ? Long.MAX_VALUE - 1 : now + secs * 1000;
            } else if (name.equals("domain")) {
                if (value.length() == 0) continue;
                if (value.charAt(0) == '.') value = value.substring(1);
                domainAttr = value.toLowerCase(Locale.ROOT);
            } else if (name.equals("path")) {
                if (value.length() == 0 || value.charAt(0) != '/') pathAttr = null;
                else pathAttr = value;
            } else if (name.equals("secure")) {
                c.secure = true;
            } else if (name.equals("httponly")) {
                c.httpOnly = true;
            }
        }

        if (maxAgeExpiry != Long.MIN_VALUE) {
            c.expiry = maxAgeExpiry;
            c.persistent = true;
        } else if (expiresExpiry != Long.MIN_VALUE) {
            c.expiry = expiresExpiry;
            c.persistent = true;
        }

        if (domainAttr != null && domainAttr.length() > 0 && !domainAttr.equals(u.host)) {
            if (!domainMatch(u.host, domainAttr)) return null;
            // A bare top-level label (for example "com") is treated like a public suffix and refused.
            if (domainAttr.indexOf('.') < 0) return null;
            c.domain = domainAttr;
            c.hostOnly = false;
        } else {
            c.domain = u.host;
            c.hostOnly = true;
        }
        c.path = pathAttr != null ? pathAttr : defaultPath(u.path);

        // Strict secure cookies: only secure origins may set or overwrite them.
        if (c.secure && !u.isSecure()) return null;
        if (c.name.startsWith("__Secure-") && !c.secure) return null;
        if (c.name.startsWith("__Host-") && (!c.secure || !c.hostOnly || !c.path.equals("/"))) return null;
        return c;
    }

    // URL handling.

    private static Url parseUrl(String url) {
        if (url == null) return null;
        url = url.trim();
        if (url.length() == 0) return null;
        int colon = url.indexOf(':');
        boolean hasScheme = colon > 0 && isScheme(url.substring(0, colon));
        // As Chromium's WebAddress fixup: a URL without a scheme is http.
        if (!hasScheme || !url.startsWith("//", colon + 1)) {
            if (hasScheme && url.substring(0, colon).equalsIgnoreCase("file")) {
                if (!allowFileSchemeCookiesImpl()) return null;
                Url u = new Url();
                u.scheme = "file";
                u.host = "";
                u.path = pathOf(url.substring(colon + 1));
                return u;
            }
            if (hasScheme && !isHostPort(url)) return null;
            url = "http://" + url;
            colon = 4;
        }
        Url u = new Url();
        u.scheme = url.substring(0, colon).toLowerCase(Locale.ROOT);
        if (u.scheme.equals("file")) {
            if (!allowFileSchemeCookiesImpl()) return null;
            u.host = "";
            u.path = pathOf(url.substring(colon + 3));
            int slash = u.path.indexOf('/');
            u.path = slash >= 0 ? u.path.substring(slash) : "/";
            return u;
        }
        int start = colon + 3;
        int end = start;
        while (end < url.length() && "/?#\\".indexOf(url.charAt(end)) < 0) end++;
        String authority = url.substring(start, end);
        int at = authority.lastIndexOf('@');
        if (at >= 0) authority = authority.substring(at + 1);
        String host;
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            host = close > 0 ? authority.substring(0, close + 1) : authority;
        } else {
            int pc = authority.indexOf(':');
            host = pc >= 0 ? authority.substring(0, pc) : authority;
        }
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.length() == 0) return null;
        u.host = host.toLowerCase(Locale.ROOT);
        u.path = end < url.length() && url.charAt(end) != '?' && url.charAt(end) != '#'
                ? pathOf(url.substring(end).replace('\\', '/')) : "/";
        return u;
    }

    // "example.com:8080/x" has a "scheme" that is really a host; treat it as one.
    private static boolean isHostPort(String url) {
        int colon = url.indexOf(':');
        int i = colon + 1;
        int digits = 0;
        while (i < url.length() && Character.isDigit(url.charAt(i))) {
            i++;
            digits++;
        }
        return digits > 0 && (i == url.length() || url.charAt(i) == '/');
    }

    private static boolean isScheme(String s) {
        if (s.length() == 0 || !Character.isLetter(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '+' && ch != '-' && ch != '.') return false;
        }
        return true;
    }

    private static String pathOf(String s) {
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        int h = s.indexOf('#');
        if (h >= 0) s = s.substring(0, h);
        return s.length() == 0 ? "/" : s;
    }

    // RFC 6265 5.1.4.
    private static String defaultPath(String uriPath) {
        if (uriPath == null || uriPath.length() == 0 || uriPath.charAt(0) != '/') return "/";
        int last = uriPath.lastIndexOf('/');
        if (last == 0) return "/";
        return uriPath.substring(0, last);
    }

    private static boolean pathMatch(String requestPath, String cookiePath) {
        if (requestPath.equals(cookiePath)) return true;
        if (!requestPath.startsWith(cookiePath)) return false;
        return cookiePath.endsWith("/") || requestPath.charAt(cookiePath.length()) == '/';
    }

    // RFC 6265 5.1.3.
    private static boolean domainMatch(String host, String domain) {
        if (host.equals(domain)) return true;
        return host.endsWith(domain) && host.length() > domain.length()
                && host.charAt(host.length() - domain.length() - 1) == '.' && !isIpAddress(host);
    }

    private static boolean isIpAddress(String host) {
        if (host.startsWith("[")) return true;
        for (int i = 0; i < host.length(); i++) {
            char ch = host.charAt(i);
            if (ch != '.' && (ch < '0' || ch > '9')) return false;
        }
        return true;
    }

    // RFC 6265 5.1.1 cookie-date parsing. Returns ms since the epoch, or Long.MIN_VALUE when invalid.

    private static final String[] MONTHS = {
        "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
    };

    private static boolean isDelimiter(char ch) {
        return ch == 0x09 || (ch >= 0x20 && ch <= 0x2F) || (ch >= 0x3B && ch <= 0x40) || (ch >= 0x5B && ch <= 0x60)
                || (ch >= 0x7B && ch <= 0x7E);
    }

    static long parseCookieDate(String s) {
        int hour = -1, minute = -1, second = -1, day = -1, month = -1, year = -1;
        int i = 0;
        int n = s.length();
        while (i < n) {
            while (i < n && isDelimiter(s.charAt(i))) i++;
            int start = i;
            while (i < n && !isDelimiter(s.charAt(i))) i++;
            if (start == i) continue;
            String tok = s.substring(start, i);
            if (hour < 0) {
                int[] t = parseTime(tok);
                if (t != null) {
                    hour = t[0];
                    minute = t[1];
                    second = t[2];
                    continue;
                }
            }
            if (day < 0) {
                int d = leadingDigits(tok, 1, 2);
                if (d >= 0) {
                    day = d;
                    continue;
                }
            }
            if (month < 0 && tok.length() >= 3) {
                String m = tok.substring(0, 3).toLowerCase(Locale.ROOT);
                int found = -1;
                for (int k = 0; k < 12; k++) {
                    if (MONTHS[k].equals(m)) found = k;
                }
                if (found >= 0) {
                    month = found;
                    continue;
                }
            }
            if (year < 0) {
                int y = leadingDigits(tok, 2, 4);
                if (y >= 0) year = y;
            }
        }
        if (year >= 70 && year <= 99) year += 1900;
        else if (year >= 0 && year <= 69) year += 2000;
        if (day < 0 || month < 0 || year < 0 || hour < 0) return Long.MIN_VALUE;
        if (day < 1 || day > 31 || year < 1601 || hour > 23 || minute > 59 || second > 59) return Long.MIN_VALUE;
        long days = daysFromCivil(year, month + 1, day);
        return ((days * 24 + hour) * 60 + minute) * 60000L + second * 1000L;
    }

    // 1*2DIGIT ":" 1*2DIGIT ":" 1*2DIGIT, optionally followed by non-digits.
    private static int[] parseTime(String tok) {
        int[] out = new int[3];
        int pos = 0;
        for (int f = 0; f < 3; f++) {
            int start = pos;
            int v = 0;
            while (pos < tok.length() && pos - start < 2 && Character.isDigit(tok.charAt(pos))) {
                v = v * 10 + (tok.charAt(pos) - '0');
                pos++;
            }
            if (pos == start) return null;
            out[f] = v;
            if (f < 2) {
                if (pos >= tok.length() || tok.charAt(pos) != ':') return null;
                pos++;
            }
        }
        if (pos < tok.length() && Character.isDigit(tok.charAt(pos))) return null;
        return out;
    }

    // min..max digits, optionally followed by non-digits; -1 if not matching.
    private static int leadingDigits(String tok, int min, int max) {
        int pos = 0;
        int v = 0;
        while (pos < tok.length() && Character.isDigit(tok.charAt(pos))) {
            if (pos >= max) return -1;
            v = v * 10 + (tok.charAt(pos) - '0');
            pos++;
        }
        return pos >= min ? v : -1;
    }

    // Days since 1970-01-01 for a proleptic Gregorian date (month 1..12).
    private static long daysFromCivil(long y, int m, int d) {
        y -= m <= 2 ? 1 : 0;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }
}
