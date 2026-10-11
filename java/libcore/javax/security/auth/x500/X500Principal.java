package javax.security.auth.x500;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import libcore.crypto.X500Name;

/**
 * An X.500 distinguished name, held as its DER encoding. Names given as
 * strings are parsed as RFC 1779/2253; the getName styles and toString
 * format and escape values as OpenJDK does (see libcore.crypto.X500Name).
 */
public final class X500Principal implements Principal, java.io.Serializable {
    private static final long serialVersionUID = -500463348111345721L;

    public static final String RFC1779 = "RFC1779";
    public static final String RFC2253 = "RFC2253";
    public static final String CANONICAL = "CANONICAL";

    private final X500Name name;

    public X500Principal(String name) {
        this(name, Collections.<String, String>emptyMap());
    }

    public X500Principal(String name, Map<String, String> keywordMap) {
        if (name == null) throw new NullPointerException("provided null name");
        if (keywordMap == null) throw new NullPointerException("provided null keyword map");
        try {
            this.name = X500Name.fromString(name, keywordMap);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("improperly specified input name: " + name, e);
        }
    }

    public X500Principal(byte[] name) {
        if (name == null) throw new NullPointerException("provided null name");
        try {
            this.name = X500Name.fromDer(name);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("improperly specified input name", e);
        }
    }

    public X500Principal(InputStream is) {
        if (is == null) throw new NullPointerException("provided null input stream");
        try {
            this.name = X500Name.fromDer(readElement(is));
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("improperly specified input stream", e);
        }
    }

    /** Reads exactly one DER element, leaving the stream after it. */
    private static byte[] readElement(InputStream is) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int tag = is.read();
        if (tag < 0) throw new IOException("end of stream");
        out.write(tag);
        int len = is.read();
        if (len < 0) throw new IOException("end of stream");
        out.write(len);
        if (len >= 0x80) {
            int n = len & 0x7f;
            if (n == 0 || n > 3) throw new IOException("bad length");
            len = 0;
            for (int i = 0; i < n; i++) {
                int b = is.read();
                if (b < 0) throw new IOException("end of stream");
                out.write(b);
                len = (len << 8) | b;
            }
        }
        byte[] content = new byte[len];
        int off = 0;
        while (off < len) {
            int r = is.read(content, off, len - off);
            if (r < 0) throw new IOException("end of stream");
            off += r;
        }
        out.write(content, 0, len);
        return out.toByteArray();
    }

    public String getName() {
        return getName(RFC2253);
    }

    public String getName(String format) {
        if (format != null) {
            if (format.equalsIgnoreCase(RFC1779)) return name.format(X500Name.RFC1779, null);
            if (format.equalsIgnoreCase(RFC2253)) return name.format(X500Name.RFC2253, null);
            if (format.equalsIgnoreCase(CANONICAL)) return name.canonical();
        }
        throw new IllegalArgumentException("invalid format specified");
    }

    public String getName(String format, Map<String, String> oidMap) {
        if (oidMap == null) throw new NullPointerException("provided null OID map");
        if (format != null) {
            if (format.equalsIgnoreCase(RFC1779)) return name.format(X500Name.RFC1779, oidMap);
            if (format.equalsIgnoreCase(RFC2253)) return name.format(X500Name.RFC2253, oidMap);
        }
        throw new IllegalArgumentException("invalid format specified");
    }

    public byte[] getEncoded() {
        return name.getEncoded();
    }

    public String toString() {
        return name.toString();
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof X500Principal && name.equals(((X500Principal) o).name);
    }

    public int hashCode() {
        return name.hashCode();
    }
}
