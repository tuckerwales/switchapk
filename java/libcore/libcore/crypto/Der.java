package libcore.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Minimal DER: the encoder builds TLVs from byte arrays; the reader walks a
 * buffer. Enough for SubjectPublicKeyInfo, PKCS#8, PKCS#1, SEC 1 EC keys,
 * DigestInfo, ECDSA signatures, X.509 certificates and X.500 names. Lengths
 * up to 2^24; definite forms and single-byte tags only.
 */
final class Der {
    static final int BOOLEAN = 0x01, INTEGER = 0x02, BIT_STRING = 0x03, OCTET_STRING = 0x04, NULL = 0x05, OID = 0x06,
            UTF8_STRING = 0x0c, NUMERIC_STRING = 0x12, PRINTABLE_STRING = 0x13, T61_STRING = 0x14, IA5_STRING = 0x16,
            UTC_TIME = 0x17, GENERALIZED_TIME = 0x18, VISIBLE_STRING = 0x1a, GENERAL_STRING = 0x1b,
            UNIVERSAL_STRING = 0x1c, BMP_STRING = 0x1e, SEQUENCE = 0x30, SET = 0x31, CONTEXT0 = 0xa0, CONTEXT1 = 0xa1,
            CONTEXT3 = 0xa3;

    private Der() {
    }

    static byte[] tlv(int tag, byte[] content) {
        int n = content.length;
        byte[] len;
        if (n < 0x80) len = new byte[] {(byte) n};
        else if (n < 0x100) len = new byte[] {(byte) 0x81, (byte) n};
        else if (n < 0x10000) len = new byte[] {(byte) 0x82, (byte) (n >> 8), (byte) n};
        else len = new byte[] {(byte) 0x83, (byte) (n >> 16), (byte) (n >> 8), (byte) n};
        byte[] out = new byte[1 + len.length + n];
        out[0] = (byte) tag;
        System.arraycopy(len, 0, out, 1, len.length);
        System.arraycopy(content, 0, out, 1 + len.length, n);
        return out;
    }

    static byte[] seq(byte[]... parts) {
        return tlv(SEQUENCE, concat(parts));
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.write(p, 0, p.length);
        return out.toByteArray();
    }

    /** A DER SET OF: the encodings sorted as unsigned byte strings (shorter first on a common prefix). */
    static byte[] setOf(byte[][] elements) {
        byte[][] sorted = elements.clone();
        Arrays.sort(sorted, new java.util.Comparator<byte[]>() {
            public int compare(byte[] a, byte[] b) {
                for (int i = 0; i < Math.min(a.length, b.length); i++) {
                    int d = (a[i] & 0xff) - (b[i] & 0xff);
                    if (d != 0) return d;
                }
                return a.length - b.length;
            }
        });
        return tlv(SET, concat(sorted));
    }

    static byte[] integer(BigInteger v) {
        return tlv(INTEGER, v.toByteArray());
    }

    static byte[] integer(int v) {
        return integer(BigInteger.valueOf(v));
    }

    static byte[] octets(byte[] v) {
        return tlv(OCTET_STRING, v);
    }

    static byte[] bits(byte[] v) {
        byte[] c = new byte[v.length + 1];
        System.arraycopy(v, 0, c, 1, v.length);
        return tlv(BIT_STRING, c);
    }

    static byte[] nul() {
        return new byte[] {NULL, 0};
    }

    /** "1.2.840.113549.1.1.1" to its DER TLV. */
    static byte[] oid(String dotted) {
        String[] p = dotted.split("\\.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(Integer.parseInt(p[0]) * 40 + Integer.parseInt(p[1]));
        for (int i = 2; i < p.length; i++) {
            long v = Long.parseLong(p[i]);
            byte[] tmp = new byte[10];
            int n = 0;
            do {
                tmp[n++] = (byte) (v & 0x7f);
                v >>>= 7;
            } while (v != 0);
            for (int j = n - 1; j >= 0; j--) out.write(tmp[j] | (j > 0 ? 0x80 : 0));
        }
        return tlv(OID, out.toByteArray());
    }

    static String oidString(byte[] content) {
        StringBuilder sb = new StringBuilder();
        int first = content[0] & 0xff;
        sb.append(Math.min(first / 40, 2)).append('.').append(first - 40 * Math.min(first / 40, 2));
        long v = 0;
        for (int i = 1; i < content.length; i++) {
            v = (v << 7) | (content[i] & 0x7f);
            if ((content[i] & 0x80) == 0) {
                sb.append('.').append(v);
                v = 0;
            }
        }
        return sb.toString();
    }

    /** Unsigned big-endian bytes of v, left-padded to len. */
    static byte[] unsigned(BigInteger v, int len) {
        byte[] b = v.toByteArray();
        if (b.length == len) return b;
        if (b.length > len) {
            if (b.length == len + 1 && b[0] == 0) return Arrays.copyOfRange(b, 1, b.length);
            throw new IllegalArgumentException("value too large");
        }
        byte[] out = new byte[len];
        System.arraycopy(b, 0, out, len - b.length, b.length);
        return out;
    }

    /** The text of a string-typed value, or null if the tag is not a string type we decode. */
    static String string(int tag, byte[] c) {
        switch (tag) {
            case UTF8_STRING:
                return new String(c, java.nio.charset.StandardCharsets.UTF_8);
            case NUMERIC_STRING:
            case PRINTABLE_STRING:
            case IA5_STRING:
            case VISIBLE_STRING:
                return new String(c, java.nio.charset.StandardCharsets.US_ASCII);
            case T61_STRING:
            case GENERAL_STRING:
                return new String(c, java.nio.charset.StandardCharsets.ISO_8859_1);
            case BMP_STRING:
                return new String(c, java.nio.charset.StandardCharsets.UTF_16BE);
            case UNIVERSAL_STRING: {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i + 3 < c.length; i += 4) {
                    sb.appendCodePoint(((c[i] & 0xff) << 24) | ((c[i + 1] & 0xff) << 16) | ((c[i + 2] & 0xff) << 8) | (c[i + 3] & 0xff));
                }
                return sb.toString();
            }
            default:
                return null;
        }
    }

    /** Milliseconds since the epoch of a UTCTime (years 1950 to 2049) or GeneralizedTime, which must be in UTC. */
    static long time(int tag, byte[] c) throws IOException {
        String s = new String(c, java.nio.charset.StandardCharsets.US_ASCII);
        int year, i;
        if (tag == UTC_TIME) {
            year = digits(s, 0, 2);
            year += year >= 50 ? 1900 : 2000;
            i = 2;
        } else if (tag == GENERALIZED_TIME) {
            year = digits(s, 0, 4);
            i = 4;
        } else {
            throw new IOException("DER: expected a time, got tag 0x" + Integer.toHexString(tag));
        }
        int month = digits(s, i, 2), day = digits(s, i + 2, 2), hour = digits(s, i + 4, 2), min = digits(s, i + 6, 2);
        i += 8;
        int sec = 0, millis = 0;
        if (i < s.length() && Character.isDigit(s.charAt(i))) {
            sec = digits(s, i, 2);
            i += 2;
        }
        if (i < s.length() && (s.charAt(i) == '.' || s.charAt(i) == ',')) {
            i++;
            int scale = 100;
            while (i < s.length() && Character.isDigit(s.charAt(i))) {
                millis += (s.charAt(i++) - '0') * scale;
                scale /= 10;
            }
        }
        long offset = 0;
        if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            int sign = s.charAt(i) == '+' ? 1 : -1;
            offset = sign * (digits(s, i + 1, 2) * 60L + digits(s, i + 3, 2)) * 60000L;
            i += 5;
        } else if (i < s.length() && s.charAt(i) == 'Z') {
            i++;
        } else {
            throw new IOException("DER: time is not in UTC: " + s);
        }
        if (i != s.length() || month < 1 || month > 12 || day < 1 || day > 31 || hour > 23 || min > 59 || sec > 59) {
            throw new IOException("DER: bad time " + s);
        }
        return (daysFromCivil(year, month, day) * 86400L + hour * 3600L + min * 60L + sec) * 1000L + millis - offset;
    }

    private static int digits(String s, int off, int n) throws IOException {
        if (off + n > s.length()) throw new IOException("DER: bad time " + s);
        int v = 0;
        for (int i = off; i < off + n; i++) {
            char ch = s.charAt(i);
            if (ch < '0' || ch > '9') throw new IOException("DER: bad time " + s);
            v = v * 10 + (ch - '0');
        }
        return v;
    }

    /** Days since 1970-01-01 of a proleptic Gregorian date (Howard Hinnant's algorithm). */
    private static long daysFromCivil(int y, int m, int d) {
        y -= m <= 2 ? 1 : 0;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    /** Walks DER from a buffer. Each read checks the tag and returns the content. */
    static final class Reader {
        private final byte[] buf;
        private int pos;
        private final int end;

        Reader(byte[] buf) {
            this(buf, 0, buf.length);
        }

        Reader(byte[] buf, int off, int end) {
            this.buf = buf;
            this.pos = off;
            this.end = end;
        }

        boolean more() {
            return pos < end;
        }

        int peekTag() throws IOException {
            if (pos >= end) throw new IOException("DER: unexpected end");
            return buf[pos] & 0xff;
        }

        int position() {
            return pos;
        }

        byte[] read(int tag) throws IOException {
            int t = peekTag();
            if (t != tag) throw new IOException("DER: expected tag 0x" + Integer.toHexString(tag) + ", got 0x" + Integer.toHexString(t));
            int len = header();
            byte[] out = Arrays.copyOfRange(buf, pos, pos + len);
            pos += len;
            return out;
        }

        /** The next element whatever its tag, as its full encoding (tag, length and content). */
        byte[] element() throws IOException {
            int start = pos;
            peekTag();
            if ((buf[pos] & 0x1f) == 0x1f) throw new IOException("DER: multi-byte tags are not supported");
            int len = header();
            pos += len;
            return Arrays.copyOfRange(buf, start, pos);
        }

        /** The content of the next element whatever its tag (see peekTag). */
        byte[] content() throws IOException {
            return read(peekTag());
        }

        /** Skips the tag byte and parses the length; leaves pos at the content and returns its length. */
        private int header() throws IOException {
            pos++;
            if (pos >= end) throw new IOException("DER: missing length");
            int len = buf[pos++] & 0xff;
            if (len >= 0x80) {
                int n = len & 0x7f;
                if (n == 0 || n > 3) throw new IOException("DER: bad length");
                len = 0;
                for (int i = 0; i < n; i++) {
                    if (pos >= end) throw new IOException("DER: bad length");
                    len = (len << 8) | (buf[pos++] & 0xff);
                }
            }
            if (len < 0 || pos + len > end) throw new IOException("DER: length past end");
            return len;
        }

        Reader sequence() throws IOException {
            byte[] c = read(SEQUENCE);
            return new Reader(c);
        }

        Reader set() throws IOException {
            return new Reader(read(SET));
        }

        /** A reader over the content of a constructed element with this tag (for example [0] EXPLICIT). */
        Reader constructed(int tag) throws IOException {
            return new Reader(read(tag));
        }

        boolean bool() throws IOException {
            byte[] c = read(BOOLEAN);
            if (c.length != 1) throw new IOException("DER: bad BOOLEAN");
            return c[0] != 0;
        }

        /** A BIT STRING of the given tag as booleans, one per bit, most significant bit first. */
        boolean[] bitArray(int tag) throws IOException {
            byte[] c = read(tag);
            if (c.length == 0 || (c[0] & 0xff) > 7 || (c.length == 1 && c[0] != 0)) throw new IOException("DER: bad BIT STRING");
            int n = (c.length - 1) * 8 - (c[0] & 0xff);
            boolean[] out = new boolean[n];
            for (int i = 0; i < n; i++) out[i] = (c[1 + i / 8] & (0x80 >> (i % 8))) != 0;
            return out;
        }

        long time() throws IOException {
            int t = peekTag();
            return Der.time(t, read(t));
        }

        BigInteger integer() throws IOException {
            byte[] c = read(INTEGER);
            if (c.length == 0) throw new IOException("DER: empty INTEGER");
            return new BigInteger(c);
        }

        String oid() throws IOException {
            return oidString(read(OID));
        }

        byte[] octets() throws IOException {
            return read(OCTET_STRING);
        }

        byte[] bits() throws IOException {
            byte[] c = read(BIT_STRING);
            if (c.length == 0 || c[0] != 0) throw new IOException("DER: unsupported BIT STRING padding");
            return Arrays.copyOfRange(c, 1, c.length);
        }

        void nul() throws IOException {
            if (read(NULL).length != 0) throw new IOException("DER: bad NULL");
        }

        void end() throws IOException {
            if (pos != end) throw new IOException("DER: trailing data");
        }
    }
}
