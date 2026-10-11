package libcore.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An X.500 distinguished name (RFC 5280 Name): parsed from DER or from an
 * RFC 1779/2253 string, and formatted in the styles of OpenJDK's
 * X500Principal (keywords, escaping and quoting follow sun.security.x509.AVA
 * so that names print the same on both). Framework-internal: used by
 * javax.security.auth.x500.X500Principal and the X.509 certificate code.
 */
public final class X500Name {
    /** Output styles. DEFAULT is X500Principal.toString(). */
    public static final int DEFAULT = 0, RFC1779 = 1, RFC2253 = 2;

    private static final String EMAIL_OID = "1.2.840.113549.1.9.1", DC_OID = "0.9.2342.19200300.100.1.25";

    /** keyword, OID, RFC 1779 compliant, RFC 2253 compliant; in OpenJDK's AVAKeyword order (later entries win the OID). */
    private static final Object[][] KEYWORDS = {
        {"CN", "2.5.4.3", true, true},
        {"C", "2.5.4.6", true, true},
        {"L", "2.5.4.7", true, true},
        {"S", "2.5.4.8", false, false},
        {"ST", "2.5.4.8", true, true},
        {"O", "2.5.4.10", true, true},
        {"OU", "2.5.4.11", true, true},
        {"T", "2.5.4.12", false, false},
        {"IP", "1.3.6.1.4.1.42.2.11.2.1", false, false},
        {"STREET", "2.5.4.9", true, true},
        {"DC", DC_OID, false, true},
        {"DNQUALIFIER", "2.5.4.46", false, false},
        {"DNQ", "2.5.4.46", false, false},
        {"SURNAME", "2.5.4.4", false, false},
        {"GIVENNAME", "2.5.4.42", false, false},
        {"INITIALS", "2.5.4.43", false, false},
        {"GENERATION", "2.5.4.44", false, false},
        {"EMAIL", EMAIL_OID, false, false},
        {"EMAILADDRESS", EMAIL_OID, false, false},
        {"UID", "0.9.2342.19200300.100.1.1", false, true},
        {"SERIALNUMBER", "2.5.4.5", false, false},
    };
    private static final HashMap<String, Object[]> BY_KEYWORD = new HashMap<String, Object[]>();
    private static final HashMap<String, Object[]> BY_OID = new HashMap<String, Object[]>();

    static {
        for (Object[] k : KEYWORDS) {
            BY_KEYWORD.put((String) k[0], k);
            BY_OID.put((String) k[1], k);
        }
    }

    /** One attribute type and value. */
    static final class Ava {
        final String oid;
        final int tag;
        final byte[] content;
        final byte[] value;

        Ava(String oid, byte[] value) throws IOException {
            this.oid = oid;
            this.value = value;
            Der.Reader r = new Der.Reader(value);
            this.tag = r.peekTag();
            this.content = r.content();
            r.end();
        }

        /** The value as text for the default and RFC 1779 styles (OpenJDK's DerValue.getAsString), or null. */
        String asString() {
            switch (tag) {
                case Der.UTF8_STRING:
                case Der.PRINTABLE_STRING:
                case Der.T61_STRING:
                case Der.IA5_STRING:
                case Der.UNIVERSAL_STRING:
                case Der.BMP_STRING:
                case Der.GENERAL_STRING:
                    return Der.string(tag, content);
                default:
                    return null;
            }
        }

        boolean isDerString(boolean canonical) {
            if (canonical) return tag == Der.PRINTABLE_STRING || tag == Der.UTF8_STRING;
            switch (tag) {
                case Der.PRINTABLE_STRING:
                case Der.T61_STRING:
                case Der.IA5_STRING:
                case Der.GENERAL_STRING:
                case Der.UTF8_STRING:
                case Der.BMP_STRING:
                    return true;
                default:
                    return false;
            }
        }

        byte[] encode() {
            return Der.seq(Der.oid(oid), value);
        }
    }

    private final byte[] encoded;
    /** RDNs in DER order (most significant first). */
    private final Ava[][] rdns;
    private String canonical;

    private X500Name(byte[] encoded, Ava[][] rdns) {
        this.encoded = encoded;
        this.rdns = rdns;
    }

    /** Parses a DER Name; the array must hold exactly one Name. */
    public static X500Name fromDer(byte[] der) throws IOException {
        Der.Reader top = new Der.Reader(der);
        Der.Reader seq = top.sequence();
        top.end();
        ArrayList<Ava[]> rdns = new ArrayList<Ava[]>();
        while (seq.more()) {
            Der.Reader set = seq.set();
            ArrayList<Ava> avas = new ArrayList<Ava>();
            while (set.more()) {
                Der.Reader a = set.sequence();
                String oid = a.oid();
                byte[] value = a.element();
                a.end();
                avas.add(new Ava(oid, value));
            }
            if (avas.isEmpty()) throw new IOException("empty RDN");
            rdns.add(avas.toArray(new Ava[0]));
        }
        return new X500Name(der.clone(), rdns.toArray(new Ava[0][]));
    }

    /** Parses an RFC 1779 or RFC 2253 name as OpenJDK's X500Principal(String, Map) does. */
    public static X500Name fromString(String name, Map<String, String> keywordMap) throws IOException {
        if (keywordMap == null) keywordMap = Collections.emptyMap();
        ArrayList<Ava[]> rdns = new ArrayList<Ava[]>();
        if (!name.isEmpty()) {
            for (String rdn : split(name, ",;")) {
                List<String> parts = split(rdn, "+");
                Ava[] avas = new Ava[parts.size()];
                for (int i = 0; i < avas.length; i++) avas[i] = parseAva(parts.get(i), keywordMap);
                rdns.add(avas);
            }
            // Strings list the most specific RDN first; DER puts it last.
            Collections.reverse(rdns);
        }
        Ava[][] arr = rdns.toArray(new Ava[0][]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Ava[] rdn : arr) {
            byte[][] enc = new byte[rdn.length][];
            for (int i = 0; i < rdn.length; i++) enc[i] = rdn[i].encode();
            byte[] set = Der.setOf(enc);
            out.write(set, 0, set.length);
        }
        // The RDN members stay in the order given (they print that way); the encoding sorts them.
        return new X500Name(Der.tlv(Der.SEQUENCE, out.toByteArray()), arr);
    }

    /** Splits at unescaped separators outside double quotes. */
    private static List<String> split(String s, String seps) {
        ArrayList<String> out = new ArrayList<String>();
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && seps.indexOf(c) >= 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }

    private static final String SPECIAL_CHARS = ",=\n+<>#;\\\" ";

    private static Ava parseAva(String s, Map<String, String> keywordMap) throws IOException {
        int eq = s.indexOf('=');
        if (eq < 0) throw new IOException("Incorrect AVA format");
        String oid = oidOf(s.substring(0, eq), keywordMap);
        int i = eq + 1, n = s.length();
        while (i < n && (s.charAt(i) == ' ' || s.charAt(i) == '\n')) i++;
        if (i == n) return new Ava(oid, Der.tlv(Der.PRINTABLE_STRING, new byte[0]));
        char c = s.charAt(i);
        if (c == '#') {
            String hex = s.substring(i + 1).trim();
            if (hex.isEmpty() || hex.length() % 2 != 0) throw new IOException("AVA parse, odd number of hex digits");
            byte[] b = new byte[hex.length() / 2];
            for (int j = 0; j < b.length; j++) {
                int hi = Character.digit(hex.charAt(2 * j), 16), lo = Character.digit(hex.charAt(2 * j + 1), 16);
                if (hi < 0 || lo < 0) throw new IOException("AVA parse, invalid hex digit");
                b[j] = (byte) (hi << 4 | lo);
            }
            return new Ava(oid, b);
        }
        StringBuilder value = new StringBuilder();
        if (c == '"') {
            i++;
            for (;; i++) {
                if (i >= n) throw new IOException("Quoted string did not end with quote");
                c = s.charAt(i);
                if (c == '"') break;
                if (c == '\\') {
                    if (++i >= n) throw new IOException("Quoted string did not end with quote");
                    c = s.charAt(i);
                }
                value.append(c);
            }
            for (i++; i < n; i++) {
                if (s.charAt(i) != ' ' && s.charAt(i) != '\n') throw new IOException("AVA parse, trailing garbage");
            }
        } else {
            int spaces = 0;
            for (; i < n; i++) {
                c = s.charAt(i);
                boolean escaped = false;
                if (c == '\\') {
                    if (++i >= n) throw new IOException("Invalid trailing backslash");
                    c = s.charAt(i);
                    if (SPECIAL_CHARS.indexOf(c) < 0) throw new IOException("Invalid escaped character in AVA: '" + c + "'");
                    escaped = true;
                }
                if (c == ' ' && !escaped) {
                    spaces++;
                } else {
                    for (; spaces > 0; spaces--) value.append(' ');
                    value.append(c);
                }
            }
        }
        String v = value.toString();
        int tag;
        if (oid.equals(EMAIL_OID) || oid.equals(DC_OID)) tag = Der.IA5_STRING;
        else if (isPrintable(v)) tag = Der.PRINTABLE_STRING;
        else tag = Der.UTF8_STRING;
        return new Ava(oid, Der.tlv(tag, v.getBytes(StandardCharsets.UTF_8)));
    }

    private static String oidOf(String keyword, Map<String, String> keywordMap) throws IOException {
        keyword = keyword.toUpperCase(Locale.ENGLISH).trim();
        String mapped = keywordMap.get(keyword);
        if (mapped != null) return checkOid(mapped);
        Object[] k = BY_KEYWORD.get(keyword);
        if (k != null) return (String) k[1];
        if (keyword.startsWith("OID.")) keyword = keyword.substring(4);
        return checkOid(keyword);
    }

    private static String checkOid(String s) throws IOException {
        String[] parts = s.split("\\.", -1);
        if (parts.length < 2) throw new IOException("Invalid keyword \"" + s + "\"");
        for (String p : parts) {
            if (p.isEmpty()) throw new IOException("Invalid keyword \"" + s + "\"");
            for (int i = 0; i < p.length(); i++) {
                if (p.charAt(i) < '0' || p.charAt(i) > '9') throw new IOException("Invalid keyword \"" + s + "\"");
            }
        }
        // Normalise, for example leading zeros.
        return Der.oidString(new Der.Reader(Der.oid(s)).read(Der.OID));
    }

    static boolean isPrintableChar(char c) {
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return true;
        switch (c) {
            case ' ':
            case '\'':
            case '(':
            case ')':
            case '+':
            case ',':
            case '-':
            case '.':
            case '/':
            case ':':
            case '=':
            case '?':
                return true;
            default:
                return false;
        }
    }

    private static boolean isPrintable(String s) {
        for (int i = 0; i < s.length(); i++) if (!isPrintableChar(s.charAt(i))) return false;
        return true;
    }

    public byte[] getEncoded() {
        return encoded.clone();
    }

    /** The encoding without a copy; callers must not modify it. */
    byte[] encodedNoCopy() {
        return encoded;
    }

    public boolean isEmpty() {
        return rdns.length == 0;
    }

    // ------------------------------------------------------------------ formatting

    private static String keyword(String oid, int style, Map<String, String> oidMap) {
        if (oidMap != null) {
            String k = oidMap.get(oid);
            if (k != null) return k;
        }
        Object[] k = BY_OID.get(oid);
        if (k != null && (style == DEFAULT || (style == RFC1779 && (Boolean) k[2]) || (style == RFC2253 && (Boolean) k[3]))) {
            return (String) k[0];
        }
        return style == RFC2253 ? oid : "OID." + oid;
    }

    private static boolean hasRfc2253Keyword(String oid) {
        Object[] k = BY_OID.get(oid);
        return k != null && (Boolean) k[3];
    }

    private static void hex(StringBuilder sb, byte[] b) {
        sb.append('#');
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
        }
    }

    /** OpenJDK's AVA.toKeywordValueString: the default and RFC 1779 styles. */
    private static String keywordValue(Ava a, String keyword) {
        StringBuilder out = new StringBuilder(keyword).append('=');
        String v = a.asString();
        if (v == null) {
            hex(out, a.value);
            return out.toString();
        }
        boolean quote = false, prevWhite = false;
        StringBuilder sb = new StringBuilder();
        String escapees = ",+=\n<>#;\\\"";
        int len = v.length();
        boolean alreadyQuoted = len > 1 && v.charAt(0) == '"' && v.charAt(len - 1) == '"';
        for (int i = 0; i < len; i++) {
            char c = v.charAt(i);
            if (alreadyQuoted && (i == 0 || i == len - 1)) {
                sb.append(c);
                continue;
            }
            if (isPrintableChar(c) || escapees.indexOf(c) >= 0) {
                if (!quote && ((i == 0 && (c == ' ' || c == '\n')) || escapees.indexOf(c) >= 0)) quote = true;
                if (!(c == ' ' || c == '\n')) {
                    if (c == '"' || c == '\\') sb.append('\\');
                    prevWhite = false;
                } else {
                    if (!quote && prevWhite) quote = true;
                    prevWhite = true;
                }
                sb.append(c);
            } else {
                prevWhite = false;
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            char t = sb.charAt(sb.length() - 1);
            if (t == ' ' || t == '\n') quote = true;
        }
        if (!alreadyQuoted && quote) out.append('"').append(sb).append('"');
        else out.append(sb);
        return out.toString();
    }

    /** OpenJDK's AVA.toRFC2253String. */
    private static String rfc2253(Ava a, Map<String, String> oidMap) {
        StringBuilder out = new StringBuilder(keyword(a.oid, RFC2253, oidMap)).append('=');
        char first = out.charAt(0);
        if ((first >= '0' && first <= '9') || !a.isDerString(false)) {
            hex(out, a.value);
            return out.toString();
        }
        String v = Der.string(a.tag, a.content);
        String escapees = ",=+<>#;\"\\";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (escapees.indexOf(c) >= 0) sb.append('\\').append(c);
            else if (c == '\u0000') sb.append("\\00");
            else sb.append(c);
        }
        int lead = 0, trail = sb.length() - 1;
        while (lead < sb.length() && (sb.charAt(lead) == ' ' || sb.charAt(lead) == '\r')) lead++;
        while (trail >= 0 && (sb.charAt(trail) == ' ' || sb.charAt(trail) == '\r')) trail--;
        for (int i = 0; i < sb.length(); i++) {
            if (i < lead || i > trail) out.append('\\');
            out.append(sb.charAt(i));
        }
        return out.toString();
    }

    /** OpenJDK's AVA.toRFC2253CanonicalString. */
    private static String canonical(Ava a) {
        StringBuilder out = new StringBuilder(keyword(a.oid, RFC2253, null)).append('=');
        char first = out.charAt(0);
        if ((first >= '0' && first <= '9') || (!a.isDerString(true) && a.tag != Der.T61_STRING)) {
            hex(out, a.value);
        } else {
            String v = Der.string(a.tag, a.content);
            String escapees = ",+<>;\"\\";
            StringBuilder sb = new StringBuilder();
            boolean prevWhite = false;
            for (int i = 0; i < v.length(); i++) {
                char c = v.charAt(i);
                if (isPrintableChar(c) || escapees.indexOf(c) >= 0 || (i == 0 && c == '#')) {
                    if ((i == 0 && c == '#') || escapees.indexOf(c) >= 0) sb.append('\\');
                    if (!Character.isWhitespace(c)) {
                        prevWhite = false;
                        sb.append(c);
                    } else if (!prevWhite) {
                        prevWhite = true;
                        sb.append(c);
                    }
                } else {
                    prevWhite = false;
                    sb.append(c);
                }
            }
            out.append(sb.toString().trim());
        }
        String s = out.toString().toUpperCase(Locale.US).toLowerCase(Locale.US);
        return nfkd(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKD));
    }

    /**
     * Compatibility decompositions (NFKD) of U+00A0 to U+024F, which java.text.Normalizer does
     * not provide here: each entry is the character followed by its decomposition, '|' separated.
     */
    private static final String DECOMPOSITIONS =
            "\u00a0\u0020|\u00a8\u0020\u0308|\u00aa\u0061|\u00af\u0020\u0304|\u00b2\u0032|\u00b3\u0033|"
            + "\u00b4\u0020\u0301|\u00b5\u03bc|\u00b8\u0020\u0327|\u00b9\u0031|\u00ba\u006f|"
            + "\u00bc\u0031\u2044\u0034|\u00bd\u0031\u2044\u0032|\u00be\u0033\u2044\u0034|\u00c0\u0041\u0300|"
            + "\u00c1\u0041\u0301|\u00c2\u0041\u0302|\u00c3\u0041\u0303|\u00c4\u0041\u0308|\u00c5\u0041\u030a|"
            + "\u00c7\u0043\u0327|\u00c8\u0045\u0300|\u00c9\u0045\u0301|\u00ca\u0045\u0302|\u00cb\u0045\u0308|"
            + "\u00cc\u0049\u0300|\u00cd\u0049\u0301|\u00ce\u0049\u0302|\u00cf\u0049\u0308|\u00d1\u004e\u0303|"
            + "\u00d2\u004f\u0300|\u00d3\u004f\u0301|\u00d4\u004f\u0302|\u00d5\u004f\u0303|\u00d6\u004f\u0308|"
            + "\u00d9\u0055\u0300|\u00da\u0055\u0301|\u00db\u0055\u0302|\u00dc\u0055\u0308|\u00dd\u0059\u0301|"
            + "\u00e0\u0061\u0300|\u00e1\u0061\u0301|\u00e2\u0061\u0302|\u00e3\u0061\u0303|\u00e4\u0061\u0308|"
            + "\u00e5\u0061\u030a|\u00e7\u0063\u0327|\u00e8\u0065\u0300|\u00e9\u0065\u0301|\u00ea\u0065\u0302|"
            + "\u00eb\u0065\u0308|\u00ec\u0069\u0300|\u00ed\u0069\u0301|\u00ee\u0069\u0302|\u00ef\u0069\u0308|"
            + "\u00f1\u006e\u0303|\u00f2\u006f\u0300|\u00f3\u006f\u0301|\u00f4\u006f\u0302|\u00f5\u006f\u0303|"
            + "\u00f6\u006f\u0308|\u00f9\u0075\u0300|\u00fa\u0075\u0301|\u00fb\u0075\u0302|\u00fc\u0075\u0308|"
            + "\u00fd\u0079\u0301|\u00ff\u0079\u0308|\u0100\u0041\u0304|\u0101\u0061\u0304|\u0102\u0041\u0306|"
            + "\u0103\u0061\u0306|\u0104\u0041\u0328|\u0105\u0061\u0328|\u0106\u0043\u0301|\u0107\u0063\u0301|"
            + "\u0108\u0043\u0302|\u0109\u0063\u0302|\u010a\u0043\u0307|\u010b\u0063\u0307|\u010c\u0043\u030c|"
            + "\u010d\u0063\u030c|\u010e\u0044\u030c|\u010f\u0064\u030c|\u0112\u0045\u0304|\u0113\u0065\u0304|"
            + "\u0114\u0045\u0306|\u0115\u0065\u0306|\u0116\u0045\u0307|\u0117\u0065\u0307|\u0118\u0045\u0328|"
            + "\u0119\u0065\u0328|\u011a\u0045\u030c|\u011b\u0065\u030c|\u011c\u0047\u0302|\u011d\u0067\u0302|"
            + "\u011e\u0047\u0306|\u011f\u0067\u0306|\u0120\u0047\u0307|\u0121\u0067\u0307|\u0122\u0047\u0327|"
            + "\u0123\u0067\u0327|\u0124\u0048\u0302|\u0125\u0068\u0302|\u0128\u0049\u0303|\u0129\u0069\u0303|"
            + "\u012a\u0049\u0304|\u012b\u0069\u0304|\u012c\u0049\u0306|\u012d\u0069\u0306|\u012e\u0049\u0328|"
            + "\u012f\u0069\u0328|\u0130\u0049\u0307|\u0132\u0049\u004a|\u0133\u0069\u006a|\u0134\u004a\u0302|"
            + "\u0135\u006a\u0302|\u0136\u004b\u0327|\u0137\u006b\u0327|\u0139\u004c\u0301|\u013a\u006c\u0301|"
            + "\u013b\u004c\u0327|\u013c\u006c\u0327|\u013d\u004c\u030c|\u013e\u006c\u030c|\u013f\u004c\u00b7|"
            + "\u0140\u006c\u00b7|\u0143\u004e\u0301|\u0144\u006e\u0301|\u0145\u004e\u0327|\u0146\u006e\u0327|"
            + "\u0147\u004e\u030c|\u0148\u006e\u030c|\u0149\u02bc\u006e|\u014c\u004f\u0304|\u014d\u006f\u0304|"
            + "\u014e\u004f\u0306|\u014f\u006f\u0306|\u0150\u004f\u030b|\u0151\u006f\u030b|\u0154\u0052\u0301|"
            + "\u0155\u0072\u0301|\u0156\u0052\u0327|\u0157\u0072\u0327|\u0158\u0052\u030c|\u0159\u0072\u030c|"
            + "\u015a\u0053\u0301|\u015b\u0073\u0301|\u015c\u0053\u0302|\u015d\u0073\u0302|\u015e\u0053\u0327|"
            + "\u015f\u0073\u0327|\u0160\u0053\u030c|\u0161\u0073\u030c|\u0162\u0054\u0327|\u0163\u0074\u0327|"
            + "\u0164\u0054\u030c|\u0165\u0074\u030c|\u0168\u0055\u0303|\u0169\u0075\u0303|\u016a\u0055\u0304|"
            + "\u016b\u0075\u0304|\u016c\u0055\u0306|\u016d\u0075\u0306|\u016e\u0055\u030a|\u016f\u0075\u030a|"
            + "\u0170\u0055\u030b|\u0171\u0075\u030b|\u0172\u0055\u0328|\u0173\u0075\u0328|\u0174\u0057\u0302|"
            + "\u0175\u0077\u0302|\u0176\u0059\u0302|\u0177\u0079\u0302|\u0178\u0059\u0308|\u0179\u005a\u0301|"
            + "\u017a\u007a\u0301|\u017b\u005a\u0307|\u017c\u007a\u0307|\u017d\u005a\u030c|\u017e\u007a\u030c|"
            + "\u017f\u0073|\u01a0\u004f\u031b|\u01a1\u006f\u031b|\u01af\u0055\u031b|\u01b0\u0075\u031b|"
            + "\u01c4\u0044\u005a\u030c|\u01c5\u0044\u007a\u030c|\u01c6\u0064\u007a\u030c|\u01c7\u004c\u004a|"
            + "\u01c8\u004c\u006a|\u01c9\u006c\u006a|\u01ca\u004e\u004a|\u01cb\u004e\u006a|\u01cc\u006e\u006a|"
            + "\u01cd\u0041\u030c|\u01ce\u0061\u030c|\u01cf\u0049\u030c|\u01d0\u0069\u030c|\u01d1\u004f\u030c|"
            + "\u01d2\u006f\u030c|\u01d3\u0055\u030c|\u01d4\u0075\u030c|\u01d5\u0055\u0308\u0304|"
            + "\u01d6\u0075\u0308\u0304|\u01d7\u0055\u0308\u0301|\u01d8\u0075\u0308\u0301|"
            + "\u01d9\u0055\u0308\u030c|\u01da\u0075\u0308\u030c|\u01db\u0055\u0308\u0300|"
            + "\u01dc\u0075\u0308\u0300|\u01de\u0041\u0308\u0304|\u01df\u0061\u0308\u0304|"
            + "\u01e0\u0041\u0307\u0304|\u01e1\u0061\u0307\u0304|\u01e2\u00c6\u0304|\u01e3\u00e6\u0304|"
            + "\u01e6\u0047\u030c|\u01e7\u0067\u030c|\u01e8\u004b\u030c|\u01e9\u006b\u030c|\u01ea\u004f\u0328|"
            + "\u01eb\u006f\u0328|\u01ec\u004f\u0328\u0304|\u01ed\u006f\u0328\u0304|\u01ee\u01b7\u030c|"
            + "\u01ef\u0292\u030c|\u01f0\u006a\u030c|\u01f1\u0044\u005a|\u01f2\u0044\u007a|\u01f3\u0064\u007a|"
            + "\u01f4\u0047\u0301|\u01f5\u0067\u0301|\u01f8\u004e\u0300|\u01f9\u006e\u0300|"
            + "\u01fa\u0041\u030a\u0301|\u01fb\u0061\u030a\u0301|\u01fc\u00c6\u0301|\u01fd\u00e6\u0301|"
            + "\u01fe\u00d8\u0301|\u01ff\u00f8\u0301|\u0200\u0041\u030f|\u0201\u0061\u030f|\u0202\u0041\u0311|"
            + "\u0203\u0061\u0311|\u0204\u0045\u030f|\u0205\u0065\u030f|\u0206\u0045\u0311|\u0207\u0065\u0311|"
            + "\u0208\u0049\u030f|\u0209\u0069\u030f|\u020a\u0049\u0311|\u020b\u0069\u0311|\u020c\u004f\u030f|"
            + "\u020d\u006f\u030f|\u020e\u004f\u0311|\u020f\u006f\u0311|\u0210\u0052\u030f|\u0211\u0072\u030f|"
            + "\u0212\u0052\u0311|\u0213\u0072\u0311|\u0214\u0055\u030f|\u0215\u0075\u030f|\u0216\u0055\u0311|"
            + "\u0217\u0075\u0311|\u0218\u0053\u0326|\u0219\u0073\u0326|\u021a\u0054\u0326|\u021b\u0074\u0326|"
            + "\u021e\u0048\u030c|\u021f\u0068\u030c|\u0226\u0041\u0307|\u0227\u0061\u0307|\u0228\u0045\u0327|"
            + "\u0229\u0065\u0327|\u022a\u004f\u0308\u0304|\u022b\u006f\u0308\u0304|\u022c\u004f\u0303\u0304|"
            + "\u022d\u006f\u0303\u0304|\u022e\u004f\u0307|\u022f\u006f\u0307|\u0230\u004f\u0307\u0304|"
            + "\u0231\u006f\u0307\u0304|\u0232\u0059\u0304|\u0233\u0079\u0304|";
    private static HashMap<Character, String> decompositions;

    /** NFKD for the Latin ranges above; other characters are kept as they are. */
    private static String nfkd(String s) {
        boolean ascii = true;
        for (int i = 0; i < s.length() && ascii; i++) ascii = s.charAt(i) < 0xa0;
        if (ascii) return s;
        synchronized (X500Name.class) {
            if (decompositions == null) {
                decompositions = new HashMap<Character, String>();
                for (String e : DECOMPOSITIONS.split("\\|")) decompositions.put(e.charAt(0), e.substring(1));
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String d = decompositions.get(c);
            if (d != null) sb.append(d);
            else sb.append(c);
        }
        return sb.toString();
    }

    private static final Comparator<Ava> CANONICAL_ORDER = new Comparator<Ava>() {
        public int compare(Ava a, Ava b) {
            boolean ak = hasRfc2253Keyword(a.oid), bk = hasRfc2253Keyword(b.oid);
            if (ak && bk) return canonical(a).compareTo(canonical(b));
            if (ak) return -1;
            if (bk) return 1;
            String[] x = a.oid.split("\\."), y = b.oid.split("\\.");
            for (int i = 0; i < Math.min(x.length, y.length); i++) {
                long d = Long.parseLong(x[i]) - Long.parseLong(y[i]);
                if (d != 0) return d < 0 ? -1 : 1;
            }
            return x.length - y.length;
        }
    };

    /** The name in DEFAULT (X500Principal.toString), RFC1779 or RFC2253 style. */
    public String format(int style, Map<String, String> oidMap) {
        StringBuilder sb = new StringBuilder();
        String rdnSep = style == RFC2253 ? "," : ", ", avaSep = style == RFC2253 ? "+" : " + ";
        for (int i = rdns.length - 1; i >= 0; i--) {
            if (i != rdns.length - 1) sb.append(rdnSep);
            Ava[] rdn = rdns[i];
            for (int j = 0; j < rdn.length; j++) {
                if (j > 0) sb.append(avaSep);
                if (style == RFC2253) sb.append(rfc2253(rdn[j], oidMap));
                else sb.append(keywordValue(rdn[j], keyword(rdn[j].oid, style, oidMap)));
            }
        }
        return sb.toString();
    }

    /** The RFC 2253 canonical form (X500Principal.CANONICAL). */
    public String canonical() {
        if (canonical == null) {
            StringBuilder sb = new StringBuilder();
            for (int i = rdns.length - 1; i >= 0; i--) {
                if (i != rdns.length - 1) sb.append(',');
                Ava[] rdn = rdns[i];
                if (rdn.length > 1) {
                    rdn = rdn.clone();
                    Arrays.sort(rdn, CANONICAL_ORDER);
                }
                for (int j = 0; j < rdn.length; j++) {
                    if (j > 0) sb.append('+');
                    sb.append(canonical(rdn[j]));
                }
            }
            canonical = sb.toString();
        }
        return canonical;
    }

    public String toString() {
        return format(DEFAULT, null);
    }

    public boolean equals(Object o) {
        return o instanceof X500Name && canonical().equals(((X500Name) o).canonical());
    }

    public int hashCode() {
        return canonical().hashCode();
    }
}
