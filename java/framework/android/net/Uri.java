package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** RFC 2396 URI reference, API compatible with android.net.Uri. Components are kept encoded. */
public abstract class Uri implements Parcelable, Comparable<Uri> {
    public static final Uri EMPTY = new Impl(null, null, null, null, null, false);
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    Uri() {}

    public abstract boolean isHierarchical();
    public boolean isOpaque() { return !isHierarchical(); }
    public abstract boolean isRelative();
    public boolean isAbsolute() { return !isRelative(); }
    public abstract String getScheme();
    public abstract String getSchemeSpecificPart();
    public abstract String getEncodedSchemeSpecificPart();
    public abstract String getAuthority();
    public abstract String getEncodedAuthority();
    public abstract String getUserInfo();
    public abstract String getEncodedUserInfo();
    public abstract String getHost();
    public abstract int getPort();
    public abstract String getPath();
    public abstract String getEncodedPath();
    public abstract String getQuery();
    public abstract String getEncodedQuery();
    public abstract String getFragment();
    public abstract String getEncodedFragment();
    public abstract List<String> getPathSegments();
    public abstract String getLastPathSegment();
    public abstract Builder buildUpon();

    public boolean equals(Object o) { return o instanceof Uri && toString().equals(o.toString()); }
    public int hashCode() { return toString().hashCode(); }
    public int compareTo(Uri other) { return toString().compareTo(other.toString()); }
    public abstract String toString();

    public String toSafeString() { return toString(); }

    public static Uri parse(String uriString) {
        if (uriString == null) throw new NullPointerException("uriString");
        return Impl.parse(uriString);
    }

    public static Uri fromFile(File file) {
        if (file == null) throw new NullPointerException("file");
        return new Builder().scheme("file").authority("").path(file.getAbsolutePath()).build();
    }

    public static Uri fromParts(String scheme, String ssp, String fragment) {
        if (scheme == null) throw new NullPointerException("scheme");
        if (ssp == null) throw new NullPointerException("ssp");
        return new Builder().scheme(scheme).opaquePart(ssp).fragment(fragment).build();
    }

    public Set<String> getQueryParameterNames() {
        String query = getEncodedQuery();
        if (query == null) return Collections.emptySet();
        Set<String> names = new LinkedHashSet<String>();
        int start = 0;
        do {
            int next = query.indexOf('&', start);
            int end = (next == -1) ? query.length() : next;
            int separator = query.indexOf('=', start);
            if (separator > end || separator == -1) separator = end;
            String name = query.substring(start, separator);
            names.add(decode(name));
            start = end + 1;
        } while (start < query.length());
        return Collections.unmodifiableSet(names);
    }

    public List<String> getQueryParameters(String key) {
        if (key == null) throw new NullPointerException("key");
        String query = getEncodedQuery();
        if (query == null) return Collections.emptyList();
        String encodedKey = encode(key, null);
        ArrayList<String> values = new ArrayList<String>();
        int start = 0;
        do {
            int nextAmpersand = query.indexOf('&', start);
            int end = nextAmpersand != -1 ? nextAmpersand : query.length();
            int separator = query.indexOf('=', start);
            if (separator > end || separator == -1) separator = end;
            if (separator - start == encodedKey.length() && query.regionMatches(start, encodedKey, 0, encodedKey.length())) {
                if (separator == end) values.add("");
                else values.add(decode(query.substring(separator + 1, end)));
            }
            if (nextAmpersand != -1) start = nextAmpersand + 1;
            else break;
        } while (true);
        return Collections.unmodifiableList(values);
    }

    public String getQueryParameter(String key) {
        if (key == null) throw new NullPointerException("key");
        final String query = getEncodedQuery();
        if (query == null) return null;
        final String encodedKey = encode(key, null);
        final int length = query.length();
        int start = 0;
        do {
            int nextAmpersand = query.indexOf('&', start);
            int end = nextAmpersand != -1 ? nextAmpersand : length;
            int separator = query.indexOf('=', start);
            if (separator > end || separator == -1) separator = end;
            if (separator - start == encodedKey.length() && query.regionMatches(start, encodedKey, 0, encodedKey.length())) {
                if (separator == end) return "";
                String encodedValue = query.substring(separator + 1, end);
                return decode(encodedValue.replace('+', ' '));
            }
            if (nextAmpersand != -1) start = nextAmpersand + 1;
            else break;
        } while (true);
        return null;
    }

    public boolean getBooleanQueryParameter(String key, boolean defaultValue) {
        String flag = getQueryParameter(key);
        if (flag == null) return defaultValue;
        flag = flag.toLowerCase(Locale.ROOT);
        return (!"false".equals(flag) && !"0".equals(flag));
    }

    public Uri normalizeScheme() {
        String scheme = getScheme();
        if (scheme == null) return this;
        String lowerScheme = scheme.toLowerCase(Locale.ROOT);
        if (scheme.equals(lowerScheme)) return this;
        return buildUpon().scheme(lowerScheme).build();
    }

    public static Uri withAppendedPath(Uri baseUri, String pathSegment) {
        Builder builder = baseUri.buildUpon();
        builder = builder.appendEncodedPath(pathSegment);
        return builder.build();
    }

    public static String encode(String s) { return encode(s, null); }

    public static String encode(String s, String allow) {
        if (s == null) return null;
        StringBuilder encoded = null;
        int oldLength = s.length();
        int current = 0;
        while (current < oldLength) {
            int nextToEncode = current;
            while (nextToEncode < oldLength && isAllowed(s.charAt(nextToEncode), allow)) nextToEncode++;
            if (nextToEncode == oldLength) {
                if (current == 0) return s;
                encoded.append(s, current, oldLength);
                return encoded.toString();
            }
            if (encoded == null) encoded = new StringBuilder();
            if (nextToEncode > current) encoded.append(s, current, nextToEncode);
            current = nextToEncode;
            int nextAllowed = current + 1;
            while (nextAllowed < oldLength && !isAllowed(s.charAt(nextAllowed), allow)) nextAllowed++;
            String toEncode = s.substring(current, nextAllowed);
            try {
                byte[] bytes = toEncode.getBytes("UTF-8");
                for (byte b : bytes) {
                    encoded.append('%');
                    encoded.append(HEX_DIGITS[(b & 0xf0) >> 4]);
                    encoded.append(HEX_DIGITS[b & 0xf]);
                }
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError(e);
            }
            current = nextAllowed;
        }
        return encoded == null ? s : encoded.toString();
    }

    private static boolean isAllowed(char c, String allow) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || "_-!.~'()*".indexOf(c) != -1
                || (allow != null && allow.indexOf(c) != -1);
    }

    public static String decode(String s) {
        if (s == null) return null;
        if (s.indexOf('%') < 0) return s;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                int hi = Character.digit(s.charAt(i + 1), 16);
                int lo = Character.digit(s.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.write((hi << 4) | lo);
                    i += 2;
                    continue;
                }
            }
            byte[] b;
            try {
                b = String.valueOf(c).getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError(e);
            }
            out.write(b, 0, b.length);
        }
        try {
            return out.toString("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { out.writeString(toString()); }
    public static void writeToParcel(Parcel out, Uri uri) { out.writeString(uri == null ? null : uri.toString()); }

    public static final Parcelable.Creator<Uri> CREATOR = new Parcelable.Creator<Uri>() {
        public Uri createFromParcel(Parcel in) {
            String s = in.readString();
            return s == null ? null : parse(s);
        }
        public Uri[] newArray(int size) { return new Uri[size]; }
    };

    /** Single implementation for both hierarchical and opaque URIs. */
    static final class Impl extends Uri {
        final String scheme;      // decoded scheme (never encoded)
        final String ssp;         // encoded scheme-specific part for opaque URIs
        final String authority;   // encoded
        final String path;        // encoded
        final String query;       // encoded
        final String fragment;    // encoded
        final boolean opaque;
        private String mString;

        Impl(String scheme, String authority, String path, String query, String fragment, boolean opaque) {
            this.scheme = scheme;
            this.authority = authority;
            this.path = path;
            this.query = query;
            this.fragment = fragment;
            this.opaque = opaque;
            this.ssp = null;
        }

        Impl(String scheme, String ssp, String fragment) {
            this.scheme = scheme;
            this.ssp = ssp;
            this.fragment = fragment;
            this.authority = null;
            this.path = null;
            this.query = null;
            this.opaque = true;
        }

        public static Uri parse(String s) {
            int ssi = -1;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == ':') {
                    ssi = i;
                    break;
                }
                if (c == '/' || c == '?' || c == '#') break;
            }
            String scheme = ssi > 0 ? s.substring(0, ssi) : null;
            String rest = ssi > 0 ? s.substring(ssi + 1) : s;
            String fragment = null;
            int hash = rest.indexOf('#');
            if (hash >= 0) {
                fragment = rest.substring(hash + 1);
                rest = rest.substring(0, hash);
            }
            if (scheme != null && !rest.startsWith("/")) {
                Impl u = new Impl(scheme, rest, fragment);
                u.mString = s;
                return u;
            }
            String query = null;
            int q = rest.indexOf('?');
            if (q >= 0) {
                query = rest.substring(q + 1);
                rest = rest.substring(0, q);
            }
            String authority = null;
            if (rest.startsWith("//")) {
                int end = rest.indexOf('/', 2);
                if (end < 0) end = rest.length();
                authority = rest.substring(2, end);
                rest = rest.substring(end);
            }
            Impl u = new Impl(scheme, authority, rest, query, fragment, false);
            u.mString = s;
            return u;
        }

        public boolean isHierarchical() { return !opaque || scheme == null; }
        public boolean isRelative() { return scheme == null; }
        public String getScheme() { return scheme; }

        public String getEncodedSchemeSpecificPart() {
            if (ssp != null) return ssp;
            StringBuilder b = new StringBuilder();
            if (authority != null) b.append("//").append(authority);
            if (path != null) b.append(path);
            if (query != null) b.append('?').append(query);
            return b.toString();
        }

        public String getSchemeSpecificPart() { return decode(getEncodedSchemeSpecificPart()); }
        public String getEncodedAuthority() { return authority; }
        public String getAuthority() { return decode(authority); }

        public String getEncodedUserInfo() {
            if (authority == null) return null;
            int at = authority.lastIndexOf('@');
            return at < 0 ? null : authority.substring(0, at);
        }

        public String getUserInfo() { return decode(getEncodedUserInfo()); }

        public String getHost() {
            if (authority == null) return null;
            int at = authority.lastIndexOf('@');
            String hp = authority.substring(at + 1);
            if (hp.startsWith("[")) {
                int end = hp.indexOf(']');
                return end < 0 ? hp : hp.substring(0, end + 1);
            }
            int colon = hp.lastIndexOf(':');
            return decode(colon < 0 ? hp : hp.substring(0, colon));
        }

        public int getPort() {
            if (authority == null) return -1;
            int at = authority.lastIndexOf('@');
            String hp = authority.substring(at + 1);
            int colon = hp.lastIndexOf(':');
            if (colon < 0 || hp.indexOf(']') > colon) return -1;
            try {
                return Integer.parseInt(decode(hp.substring(colon + 1)));
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        public String getEncodedPath() { return opaque && ssp != null ? null : path; }
        public String getPath() { return decode(getEncodedPath()); }
        public String getEncodedQuery() { return query; }
        public String getQuery() { return decode(query); }
        public String getEncodedFragment() { return fragment; }
        public String getFragment() { return decode(fragment); }

        public List<String> getPathSegments() {
            String p = getEncodedPath();
            if (p == null) return Collections.emptyList();
            ArrayList<String> segs = new ArrayList<String>();
            int previous = 0, current;
            while ((current = p.indexOf('/', previous)) > -1) {
                if (previous < current) segs.add(decode(p.substring(previous, current)));
                previous = current + 1;
            }
            if (previous < p.length()) segs.add(decode(p.substring(previous)));
            return Collections.unmodifiableList(segs);
        }

        public String getLastPathSegment() {
            List<String> segments = getPathSegments();
            int size = segments.size();
            return size == 0 ? null : segments.get(size - 1);
        }

        public String toString() {
            if (mString == null) {
                StringBuilder b = new StringBuilder();
                if (scheme != null) b.append(scheme).append(':');
                b.append(getEncodedSchemeSpecificPart());
                if (fragment != null) b.append('#').append(fragment);
                mString = b.toString();
            }
            return mString;
        }

        public Builder buildUpon() {
            if (ssp != null) return new Builder().scheme(scheme).encodedOpaquePart(ssp).encodedFragment(fragment);
            return new Builder().scheme(scheme).encodedAuthority(authority).encodedPath(path).encodedQuery(query).encodedFragment(fragment);
        }
    }

    public static final class Builder {
        private String scheme;
        private String opaquePart;
        private String authority;
        private String path;
        private String query;
        private String fragment;

        public Builder() {}

        public Builder scheme(String scheme) { this.scheme = scheme; return this; }
        public Builder opaquePart(String opaquePart) { this.opaquePart = encode(opaquePart, ":/@?=&"); return this; }
        public Builder encodedOpaquePart(String opaquePart) { this.opaquePart = opaquePart; return this; }
        public Builder authority(String authority) { this.opaquePart = null; this.authority = encode(authority, "@:[]"); return this; }
        public Builder encodedAuthority(String authority) { this.opaquePart = null; this.authority = authority; return this; }
        public Builder path(String path) { this.opaquePart = null; this.path = encode(path, "/"); return this; }
        public Builder encodedPath(String path) { this.opaquePart = null; this.path = path; return this; }

        public Builder appendPath(String newSegment) { return appendEncodedPath(encode(newSegment, null)); }

        public Builder appendEncodedPath(String newSegment) {
            this.opaquePart = null;
            if (path == null || path.isEmpty()) {
                path = newSegment.startsWith("/") ? newSegment : "/" + newSegment;
            } else if (path.endsWith("/")) {
                path = path + (newSegment.startsWith("/") ? newSegment.substring(1) : newSegment);
            } else {
                path = path + (newSegment.startsWith("/") ? newSegment : "/" + newSegment);
            }
            return this;
        }

        public Builder query(String query) { this.opaquePart = null; this.query = encode(query, "=&"); return this; }
        public Builder encodedQuery(String query) { this.opaquePart = null; this.query = query; return this; }
        public Builder fragment(String fragment) { this.fragment = encode(fragment, null); return this; }
        public Builder encodedFragment(String fragment) { this.fragment = fragment; return this; }

        public Builder appendQueryParameter(String key, String value) {
            this.opaquePart = null;
            String encodedParameter = encode(key, null) + "=" + encode(value, null);
            if (query == null || query.isEmpty()) query = encodedParameter;
            else query = query + "&" + encodedParameter;
            return this;
        }

        public Builder clearQuery() { query = null; return this; }

        public Uri build() {
            if (opaquePart != null) {
                if (scheme == null) throw new UnsupportedOperationException("An opaque URI must have a scheme.");
                return new Impl(scheme, opaquePart, fragment);
            }
            String p = path;
            if (p != null && !p.isEmpty() && authority != null && !p.startsWith("/")) p = "/" + p;
            return new Impl(scheme, authority, p == null ? "" : p, query, fragment, false);
        }

        @Override
        public String toString() { return build().toString(); }
    }
}
