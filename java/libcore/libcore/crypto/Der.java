package libcore.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Minimal DER: the encoder builds TLVs from byte arrays; the reader walks a
 * buffer. Enough for SubjectPublicKeyInfo, PKCS#8, PKCS#1, SEC 1 EC keys,
 * DigestInfo and ECDSA signatures. Lengths up to 2^24; definite forms only.
 */
final class Der {
    static final int INTEGER = 0x02, BIT_STRING = 0x03, OCTET_STRING = 0x04, NULL = 0x05, OID = 0x06,
            SEQUENCE = 0x30, CONTEXT0 = 0xa0, CONTEXT1 = 0xa1;

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

        byte[] read(int tag) throws IOException {
            int t = peekTag();
            if (t != tag) throw new IOException("DER: expected tag 0x" + Integer.toHexString(tag) + ", got 0x" + Integer.toHexString(t));
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
            byte[] out = Arrays.copyOfRange(buf, pos, pos + len);
            pos += len;
            return out;
        }

        Reader sequence() throws IOException {
            byte[] c = read(SEQUENCE);
            return new Reader(c);
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
