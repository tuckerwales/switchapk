package android.util;

public class Base64 {
    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;
    public static final int NO_CLOSE = 16;

    private static final char[] STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final char[] URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    private Base64() {}

    public static byte[] decode(String str, int flags) { return decode(str.getBytes(), flags); }
    public static byte[] decode(byte[] input, int flags) { return decode(input, 0, input.length, flags); }

    public static byte[] decode(byte[] input, int offset, int len, int flags) {
        byte[] out = new byte[len * 3 / 4 + 3];
        int op = 0, acc = 0, bits = 0;
        for (int i = offset; i < offset + len; i++) {
            int c = input[i] & 0xff;
            int v;
            if (c >= 'A' && c <= 'Z') v = c - 'A';
            else if (c >= 'a' && c <= 'z') v = c - 'a' + 26;
            else if (c >= '0' && c <= '9') v = c - '0' + 52;
            else if (c == '+' || c == '-') v = 62;
            else if (c == '/' || c == '_') v = 63;
            else if (c == '=') break;
            else if (c == ' ' || c == '\n' || c == '\r' || c == '\t') continue;
            else throw new IllegalArgumentException("bad base-64");
            acc = (acc << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[op++] = (byte) (acc >> bits);
            }
        }
        byte[] r = new byte[op];
        System.arraycopy(out, 0, r, 0, op);
        return r;
    }

    public static String encodeToString(byte[] input, int flags) { return new String(encode(input, flags)); }
    public static String encodeToString(byte[] input, int offset, int len, int flags) { return new String(encode(input, offset, len, flags)); }
    public static byte[] encode(byte[] input, int flags) { return encode(input, 0, input.length, flags); }

    public static byte[] encode(byte[] input, int offset, int len, int flags) {
        char[] alpha = (flags & URL_SAFE) != 0 ? URL : STD;
        boolean pad = (flags & NO_PADDING) == 0;
        boolean wrap = (flags & NO_WRAP) == 0;
        boolean crlf = (flags & CRLF) != 0;
        StringBuilder sb = new StringBuilder(len * 4 / 3 + 4);
        int lineLen = 0;
        int i = offset, end = offset + len;
        while (i < end) {
            int b0 = input[i++] & 0xff;
            int b1 = i < end ? input[i] & 0xff : -1;
            if (b1 >= 0) i++;
            int b2 = i < end && b1 >= 0 ? input[i] & 0xff : -1;
            if (b2 >= 0) i++;
            sb.append(alpha[b0 >> 2]);
            sb.append(alpha[((b0 & 3) << 4) | (b1 < 0 ? 0 : b1 >> 4)]);
            if (b1 >= 0) sb.append(alpha[((b1 & 15) << 2) | (b2 < 0 ? 0 : b2 >> 6)]);
            else if (pad) sb.append('=');
            if (b2 >= 0) sb.append(alpha[b2 & 63]);
            else if (pad) sb.append('=');
            lineLen += 4;
            if (wrap && lineLen >= 76) {
                sb.append(crlf ? "\r\n" : "\n");
                lineLen = 0;
            }
        }
        if (wrap && lineLen > 0) sb.append(crlf ? "\r\n" : "\n");
        String s = sb.toString();
        byte[] r = new byte[s.length()];
        for (int k = 0; k < r.length; k++) r[k] = (byte) s.charAt(k);
        return r;
    }
}
