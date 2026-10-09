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

    private static final byte[] CRLF = {'\r', '\n'};

    public static Encoder getMimeEncoder(int lineLength, byte[] lineSeparator) {
        Objects.requireNonNull(lineSeparator);
        for (byte b : lineSeparator) {
            int c = b & 0xff;
            if (Decoder.val((char) c) >= 0 || c == '=') {
                throw new IllegalArgumentException("Illegal base64 line separator character 0x" + Integer.toString(c, 16));
            }
        }
        lineLength &= ~0b11;
        if (lineLength <= 0) {
            return getEncoder();
        }
        return new Encoder(false, true, lineLength, lineSeparator.clone());
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
        private final byte[] separator;

        Encoder(boolean url, boolean pad, int lineMax) {
            this(url, pad, lineMax, CRLF);
        }

        Encoder(boolean url, boolean pad, int lineMax, byte[] separator) {
            this.url = url;
            this.pad = pad;
            this.lineMax = lineMax;
            this.separator = separator;
        }

        public Encoder withoutPadding() {
            return new Encoder(url, false, lineMax, separator);
        }

        public int encode(byte[] src, byte[] dst) {
            byte[] enc = encode(src);
            if (dst.length < enc.length) {
                throw new IllegalArgumentException("Output byte array is too small for encoding all input bytes");
            }
            System.arraycopy(enc, 0, dst, 0, enc.length);
            return enc.length;
        }

        public java.nio.ByteBuffer encode(java.nio.ByteBuffer buffer) {
            byte[] src = new byte[buffer.remaining()];
            buffer.get(src);
            return java.nio.ByteBuffer.wrap(encode(src));
        }

        /* Buffers everything and encodes on close (the JDK streams in 3-byte groups; the output is the same). */
        public java.io.OutputStream wrap(final java.io.OutputStream os) {
            Objects.requireNonNull(os);
            return new java.io.OutputStream() {
                private final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                private boolean closed;

                public void write(int b) throws java.io.IOException {
                    if (closed) {
                        throw new java.io.IOException("Stream is closed");
                    }
                    buf.write(b);
                }

                public void write(byte[] b, int off, int len) throws java.io.IOException {
                    if (closed) {
                        throw new java.io.IOException("Stream is closed");
                    }
                    buf.write(b, off, len);
                }

                public void close() throws java.io.IOException {
                    if (!closed) {
                        closed = true;
                        os.write(encode(buf.toByteArray()));
                        os.close();
                    }
                }
            };
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
                    for (byte b : separator) {
                        sb.append((char) (b & 0xff));
                    }
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

        static int val(char c) {
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

        public int decode(byte[] src, byte[] dst) {
            byte[] dec = decode(src);
            if (dst.length < dec.length) {
                throw new IllegalArgumentException("Output byte array is too small for decoding all input bytes");
            }
            System.arraycopy(dec, 0, dst, 0, dec.length);
            return dec.length;
        }

        public java.nio.ByteBuffer decode(java.nio.ByteBuffer buffer) {
            int pos0 = buffer.position();
            byte[] src = new byte[buffer.remaining()];
            buffer.get(src);
            try {
                return java.nio.ByteBuffer.wrap(decode(src));
            } catch (IllegalArgumentException e) {
                buffer.position(pos0);
                throw e;
            }
        }

        /* Reads the whole source on first use and decodes it. */
        public java.io.InputStream wrap(final java.io.InputStream is) {
            Objects.requireNonNull(is);
            return new java.io.InputStream() {
                private java.io.ByteArrayInputStream decoded;

                private java.io.ByteArrayInputStream decoded() throws java.io.IOException {
                    if (decoded == null) {
                        java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream();
                        byte[] b = new byte[4096];
                        int n;
                        while ((n = is.read(b)) > 0) {
                            all.write(b, 0, n);
                        }
                        try {
                            decoded = new java.io.ByteArrayInputStream(decode(all.toByteArray()));
                        } catch (IllegalArgumentException e) {
                            throw new java.io.IOException(e.getMessage());
                        }
                    }
                    return decoded;
                }

                public int read() throws java.io.IOException {
                    return decoded().read();
                }

                public int read(byte[] b, int off, int len) throws java.io.IOException {
                    return decoded().read(b, off, len);
                }

                public int available() throws java.io.IOException {
                    return decoded().available();
                }

                public void close() throws java.io.IOException {
                    is.close();
                }
            };
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
