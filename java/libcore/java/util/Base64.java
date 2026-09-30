package java.util;

public class Base64 {
    private Base64() {
    }

    private static final char[] TO_BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final char[] TO_BASE64_URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    public static Encoder getEncoder() {
        return new Encoder(false, true, 0);
    }

    public static Encoder getUrlEncoder() {
        return new Encoder(true, true, 0);
    }

    public static Encoder getMimeEncoder() {
        return new Encoder(false, true, 76);
    }

    public static Decoder getDecoder() {
        return new Decoder(false, false);
    }

    public static Decoder getUrlDecoder() {
        return new Decoder(true, false);
    }

    public static Decoder getMimeDecoder() {
        return new Decoder(false, true);
    }

    public static class Encoder {
        private final boolean url;
        private final boolean pad;
        private final int lineMax;

        Encoder(boolean url, boolean pad, int lineMax) {
            this.url = url;
            this.pad = pad;
            this.lineMax = lineMax;
        }

        public Encoder withoutPadding() {
            return new Encoder(url, false, lineMax);
        }

        public byte[] encode(byte[] src) {
            char[] table = url ? TO_BASE64_URL : TO_BASE64;
            StringBuilder sb = new StringBuilder((src.length + 2) / 3 * 4);
            int lineLen = 0;
            for (int i = 0; i < src.length; i += 3) {
                int b0 = src[i] & 0xff;
                int b1 = i + 1 < src.length ? src[i + 1] & 0xff : 0;
                int b2 = i + 2 < src.length ? src[i + 2] & 0xff : 0;
                if (lineMax > 0 && lineLen >= lineMax) {
                    sb.append("\r\n");
                    lineLen = 0;
                }
                sb.append(table[b0 >> 2]);
                sb.append(table[((b0 & 3) << 4) | (b1 >> 4)]);
                if (i + 1 < src.length) {
                    sb.append(table[((b1 & 0xf) << 2) | (b2 >> 6)]);
                } else if (pad) {
                    sb.append('=');
                }
                if (i + 2 < src.length) {
                    sb.append(table[b2 & 0x3f]);
                } else if (pad) {
                    sb.append('=');
                }
                lineLen += 4;
            }
            String s = sb.toString();
            byte[] out = new byte[s.length()];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) s.charAt(i);
            }
            return out;
        }

        public String encodeToString(byte[] src) {
            byte[] enc = encode(src);
            return new String(enc, java.nio.charset.StandardCharsets.ISO_8859_1);
        }
    }

    public static class Decoder {
        private final boolean url;
        private final boolean mime;

        Decoder(boolean url, boolean mime) {
            this.url = url;
            this.mime = mime;
        }

        private static int val(char c) {
            if (c >= 'A' && c <= 'Z') {
                return c - 'A';
            }
            if (c >= 'a' && c <= 'z') {
                return c - 'a' + 26;
            }
            if (c >= '0' && c <= '9') {
                return c - '0' + 52;
            }
            if (c == '+' || c == '-') {
                return 62;
            }
            if (c == '/' || c == '_') {
                return 63;
            }
            return -1;
        }

        public byte[] decode(byte[] src) {
            char[] chars = new char[src.length];
            for (int i = 0; i < src.length; i++) {
                chars[i] = (char) (src[i] & 0xff);
            }
            return decode(new String(chars));
        }

        public byte[] decode(String src) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(src.length() * 3 / 4);
            int buf = 0, bits = 0;
            for (int i = 0; i < src.length(); i++) {
                char c = src.charAt(i);
                if (c == '=') {
                    break;
                }
                int v = val(c);
                if (v < 0) {
                    if (mime || c == '\n' || c == '\r') {
                        continue;
                    }
                    throw new IllegalArgumentException("Illegal base64 character " + Integer.toHexString(c));
                }
                buf = (buf << 6) | v;
                bits += 6;
                if (bits >= 8) {
                    bits -= 8;
                    out.write((buf >> bits) & 0xff);
                }
            }
            return out.toByteArray();
        }
    }
}
