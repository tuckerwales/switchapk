package libcore.crypto;

import java.io.IOException;
import java.security.AlgorithmParametersSpi;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

/**
 * AlgorithmParameters for "AES" (an IV, DER OCTET STRING) and "GCM" (DER
 * SEQUENCE of the nonce and the tag length in bytes, RFC 5084).
 */
final class AesParameters extends AlgorithmParametersSpi {
    private final boolean gcm;
    private byte[] iv;
    private int tagBits = 128;

    AesParameters(boolean gcm) {
        this.gcm = gcm;
    }

    protected void engineInit(AlgorithmParameterSpec paramSpec) throws InvalidParameterSpecException {
        if (gcm && paramSpec instanceof GCMParameterSpec) {
            iv = ((GCMParameterSpec) paramSpec).getIV();
            tagBits = ((GCMParameterSpec) paramSpec).getTLen();
        } else if (!gcm && paramSpec instanceof IvParameterSpec) {
            iv = ((IvParameterSpec) paramSpec).getIV();
        } else {
            throw new InvalidParameterSpecException("Inappropriate parameter specification");
        }
    }

    protected void engineInit(byte[] params) throws IOException {
        int[] pos = {0};
        if (gcm) {
            expect(params, pos, 0x30);
            int end = length(params, pos) + pos[0];
            expect(params, pos, 0x04);
            iv = octets(params, pos, length(params, pos));
            int tagBytes = 12;
            if (pos[0] < end) {
                expect(params, pos, 0x02);
                int n = length(params, pos);
                tagBytes = 0;
                for (int i = 0; i < n; i++) tagBytes = (tagBytes << 8) | (params[pos[0]++] & 0xff);
            }
            tagBits = tagBytes * 8;
            if (pos[0] != params.length) throw new IOException("Extra data in GCM parameters");
        } else {
            expect(params, pos, 0x04);
            iv = octets(params, pos, length(params, pos));
            if (pos[0] != params.length) throw new IOException("Extra data in IV");
        }
    }

    protected void engineInit(byte[] params, String format) throws IOException {
        if (format != null && !format.equalsIgnoreCase("ASN.1")) throw new IOException("Only ASN.1 format supported");
        engineInit(params);
    }

    private static void expect(byte[] b, int[] pos, int tag) throws IOException {
        if (pos[0] >= b.length || (b[pos[0]] & 0xff) != tag) throw new IOException("Bad DER encoding");
        pos[0]++;
    }

    private static int length(byte[] b, int[] pos) throws IOException {
        if (pos[0] >= b.length) throw new IOException("Bad DER length");
        int first = b[pos[0]++] & 0xff;
        if (first < 0x80) return first;
        int n = first & 0x7f;
        if (n == 0 || n > 3) throw new IOException("Bad DER length");
        int len = 0;
        for (int i = 0; i < n; i++) {
            if (pos[0] >= b.length) throw new IOException("Bad DER length");
            len = (len << 8) | (b[pos[0]++] & 0xff);
        }
        return len;
    }

    private static byte[] octets(byte[] b, int[] pos, int len) throws IOException {
        if (len < 0 || pos[0] + len > b.length) throw new IOException("Bad DER length");
        byte[] out = new byte[len];
        System.arraycopy(b, pos[0], out, 0, len);
        pos[0] += len;
        return out;
    }

    @SuppressWarnings("unchecked")
    protected <T extends AlgorithmParameterSpec> T engineGetParameterSpec(Class<T> paramSpec)
            throws InvalidParameterSpecException {
        if (gcm && paramSpec.isAssignableFrom(GCMParameterSpec.class)) return (T) new GCMParameterSpec(tagBits, iv);
        if (!gcm && paramSpec.isAssignableFrom(IvParameterSpec.class)) return (T) new IvParameterSpec(iv);
        throw new InvalidParameterSpecException("Inappropriate parameter specification");
    }

    private static byte[] tlv(int tag, byte[] content) {
        int n = content.length;
        byte[] len = n < 0x80 ? new byte[] {(byte) n}
                : n < 0x100 ? new byte[] {(byte) 0x81, (byte) n} : new byte[] {(byte) 0x82, (byte) (n >> 8), (byte) n};
        byte[] out = new byte[1 + len.length + n];
        out[0] = (byte) tag;
        System.arraycopy(len, 0, out, 1, len.length);
        System.arraycopy(content, 0, out, 1 + len.length, n);
        return out;
    }

    protected byte[] engineGetEncoded() throws IOException {
        byte[] octets = tlv(0x04, iv);
        if (!gcm) return octets;
        byte[] tag = tlv(0x02, new byte[] {(byte) (tagBits / 8)});
        byte[] seq = new byte[octets.length + tag.length];
        System.arraycopy(octets, 0, seq, 0, octets.length);
        System.arraycopy(tag, 0, seq, octets.length, tag.length);
        return tlv(0x30, seq);
    }

    protected byte[] engineGetEncoded(String format) throws IOException {
        return engineGetEncoded();
    }

    protected String engineToString() {
        StringBuilder sb = new StringBuilder();
        if (gcm) sb.append("tLen(bits)=").append(tagBits).append(", ");
        sb.append(gcm ? "iv=" : "\n    iv:\n[");
        for (byte b : iv) sb.append(String.format("%02x", b & 0xff));
        if (!gcm) sb.append("]\n");
        return sb.toString();
    }
}
