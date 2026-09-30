package java.nio.charset;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public abstract class Charset implements Comparable<Charset> {
    private final String canonicalName;
    private final String[] aliases;

    protected Charset(String canonicalName, String[] aliases) {
        this.canonicalName = canonicalName;
        this.aliases = aliases == null ? new String[0] : aliases;
    }

    static final Charset UTF_8 = new Impl("UTF-8", new String[] {"UTF8", "utf8", "utf-8"});
    static final Charset ISO_8859_1 = new Impl("ISO-8859-1", new String[] {"ISO8859_1", "latin1", "iso-8859-1", "8859_1"});
    static final Charset US_ASCII = new Impl("US-ASCII", new String[] {"ASCII", "ascii", "us-ascii"});
    static final Charset UTF_16 = new Impl("UTF-16", new String[] {"UTF16", "utf-16"});
    static final Charset UTF_16BE = new Impl("UTF-16BE", new String[] {"UnicodeBigUnmarked"});
    static final Charset UTF_16LE = new Impl("UTF-16LE", new String[] {"UnicodeLittleUnmarked"});
    private static final Charset[] ALL = {UTF_8, ISO_8859_1, US_ASCII, UTF_16, UTF_16BE, UTF_16LE};

    public static boolean isSupported(String charsetName) {
        return lookup(charsetName) != null;
    }

    private static Charset lookup(String name) {
        if (name == null) {
            throw new IllegalArgumentException("charsetName == null");
        }
        for (Charset c : ALL) {
            if (c.canonicalName.equalsIgnoreCase(name)) {
                return c;
            }
            for (String a : c.aliases) {
                if (a.equalsIgnoreCase(name)) {
                    return c;
                }
            }
        }
        if (name.equalsIgnoreCase("windows-1252") || name.equalsIgnoreCase("cp1252")) {
            return ISO_8859_1;
        }
        return null;
    }

    public static Charset forName(String charsetName) {
        Charset c = lookup(charsetName);
        if (c == null) {
            throw new UnsupportedCharsetException(charsetName);
        }
        return c;
    }

    public static Charset defaultCharset() {
        return UTF_8;
    }

    public static SortedMap<String, Charset> availableCharsets() {
        TreeMap<String, Charset> m = new TreeMap<String, Charset>(String.CASE_INSENSITIVE_ORDER);
        for (Charset c : ALL) {
            m.put(c.canonicalName, c);
        }
        return Collections.unmodifiableSortedMap(m);
    }

    public final String name() {
        return canonicalName;
    }

    public final Set<String> aliases() {
        HashSet<String> s = new HashSet<String>();
        Collections.addAll(s, aliases);
        return Collections.unmodifiableSet(s);
    }

    public String displayName() {
        return canonicalName;
    }

    public final boolean isRegistered() {
        return true;
    }

    public boolean canEncode() {
        return true;
    }

    public boolean contains(Charset cs) {
        return cs == this || this == UTF_8 || this == UTF_16;
    }

    public CharsetDecoder newDecoder() {
        return new CharsetDecoder(this, 1.0f, 1.0f) {
        };
    }

    public CharsetEncoder newEncoder() {
        return new CharsetEncoder(this, 1.1f, 4.0f) {
        };
    }

    public final CharBuffer decode(ByteBuffer bb) {
        byte[] b = new byte[bb.remaining()];
        bb.get(b);
        return CharBuffer.wrap(decodeChars(b, 0, b.length));
    }

    public final ByteBuffer encode(CharBuffer cb) {
        char[] c = new char[cb.remaining()];
        cb.get(c);
        return ByteBuffer.wrap(encodeChars(c, 0, c.length));
    }

    public final ByteBuffer encode(String str) {
        char[] c = str.toCharArray();
        return ByteBuffer.wrap(encodeChars(c, 0, c.length));
    }

    public final int compareTo(Charset that) {
        return name().compareToIgnoreCase(that.name());
    }

    public final int hashCode() {
        return name().hashCode();
    }

    public final boolean equals(Object ob) {
        return this == ob || (ob instanceof Charset && name().equals(((Charset) ob).name()));
    }

    public final String toString() {
        return name();
    }

    /* ---- conversion helpers used by the class library ---- */

    public char[] decodeChars(byte[] b, int off, int len) {
        if (this == UTF_8) {
            return decodeUtf8(b, off, len);
        }
        if (this == ISO_8859_1) {
            char[] r = new char[len];
            for (int i = 0; i < len; i++) {
                r[i] = (char) (b[off + i] & 0xff);
            }
            return r;
        }
        if (this == US_ASCII) {
            char[] r = new char[len];
            for (int i = 0; i < len; i++) {
                int c = b[off + i] & 0xff;
                r[i] = c < 0x80 ? (char) c : '�';
            }
            return r;
        }
        boolean le = this == UTF_16LE;
        int start = off;
        int end = off + len;
        if (this == UTF_16 && len >= 2) {
            int b0 = b[off] & 0xff, b1 = b[off + 1] & 0xff;
            if (b0 == 0xff && b1 == 0xfe) {
                le = true;
                start += 2;
            } else if (b0 == 0xfe && b1 == 0xff) {
                start += 2;
            }
        }
        char[] r = new char[(end - start) / 2];
        for (int i = 0; i < r.length; i++) {
            int x = b[start + 2 * i] & 0xff, y = b[start + 2 * i + 1] & 0xff;
            r[i] = (char) (le ? (y << 8) | x : (x << 8) | y);
        }
        return r;
    }

    public byte[] encodeChars(char[] c, int off, int len) {
        if (this == UTF_8) {
            return encodeUtf8(c, off, len);
        }
        if (this == ISO_8859_1 || this == US_ASCII) {
            int max = this == ISO_8859_1 ? 0xff : 0x7f;
            byte[] r = new byte[len];
            for (int i = 0; i < len; i++) {
                char ch = c[off + i];
                r[i] = ch <= max ? (byte) ch : (byte) '?';
            }
            return r;
        }
        boolean le = this == UTF_16LE;
        boolean bom = this == UTF_16;
        byte[] r = new byte[len * 2 + (bom ? 2 : 0)];
        int p = 0;
        if (bom) {
            r[p++] = (byte) 0xfe;
            r[p++] = (byte) 0xff;
        }
        for (int i = 0; i < len; i++) {
            char ch = c[off + i];
            if (le) {
                r[p++] = (byte) ch;
                r[p++] = (byte) (ch >> 8);
            } else {
                r[p++] = (byte) (ch >> 8);
                r[p++] = (byte) ch;
            }
        }
        return r;
    }

    public static char[] decodeUtf8(byte[] b, int off, int len) {
        char[] out = new char[len];
        int n = 0;
        int end = off + len;
        int i = off;
        while (i < end) {
            int c = b[i++] & 0xff;
            if (c < 0x80) {
                out[n++] = (char) c;
                continue;
            }
            int need;
            int cp;
            if (c >= 0xf0 && c < 0xf8) {
                need = 3;
                cp = c & 0x07;
            } else if (c >= 0xe0) {
                need = 2;
                cp = c & 0x0f;
            } else if (c >= 0xc0) {
                need = 1;
                cp = c & 0x1f;
            } else {
                out[n++] = '�';
                continue;
            }
            if (i + need > end) {
                out[n++] = '�';
                break;
            }
            boolean bad = false;
            for (int k = 0; k < need; k++) {
                int cc = b[i + k] & 0xff;
                if ((cc & 0xc0) != 0x80) {
                    bad = true;
                    break;
                }
                cp = (cp << 6) | (cc & 0x3f);
            }
            if (bad) {
                out[n++] = '�';
                continue;
            }
            i += need;
            if (cp >= 0x10000) {
                if (n + 2 > out.length) {
                    out = java.util.Arrays.copyOf(out, out.length + 16);
                }
                out[n++] = Character.highSurrogate(cp);
                out[n++] = Character.lowSurrogate(cp);
            } else {
                out[n++] = (char) cp;
            }
        }
        return n == out.length ? out : java.util.Arrays.copyOf(out, n);
    }

    public static byte[] encodeUtf8(char[] c, int off, int len) {
        byte[] out = new byte[len * 3];
        int n = 0;
        int end = off + len;
        for (int i = off; i < end; i++) {
            int ch = c[i];
            if (ch < 0x80) {
                out[n++] = (byte) ch;
            } else if (ch < 0x800) {
                out[n++] = (byte) (0xc0 | (ch >> 6));
                out[n++] = (byte) (0x80 | (ch & 0x3f));
            } else if (Character.isHighSurrogate((char) ch) && i + 1 < end && Character.isLowSurrogate(c[i + 1])) {
                int cp = Character.toCodePoint((char) ch, c[++i]);
                if (n + 4 > out.length) {
                    out = java.util.Arrays.copyOf(out, out.length + 16);
                }
                out[n++] = (byte) (0xf0 | (cp >> 18));
                out[n++] = (byte) (0x80 | ((cp >> 12) & 0x3f));
                out[n++] = (byte) (0x80 | ((cp >> 6) & 0x3f));
                out[n++] = (byte) (0x80 | (cp & 0x3f));
            } else if (Character.isSurrogate((char) ch)) {
                out[n++] = (byte) '?';
            } else {
                out[n++] = (byte) (0xe0 | (ch >> 12));
                out[n++] = (byte) (0x80 | ((ch >> 6) & 0x3f));
                out[n++] = (byte) (0x80 | (ch & 0x3f));
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    static final class Impl extends Charset {
        Impl(String name, String[] aliases) {
            super(name, aliases);
        }
    }
}
